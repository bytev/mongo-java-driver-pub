/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.connection

import category.Async
import category.Slow
import com.mongodb.CursorFlag
import com.mongodb.MongoException
import com.mongodb.MongoInternalException
import com.mongodb.MongoNamespace
import com.mongodb.MongoSocketClosedException
import com.mongodb.MongoSocketReadException
import com.mongodb.MongoSocketWriteException
import com.mongodb.async.SingleResultCallback
import com.mongodb.codecs.DocumentCodec
import com.mongodb.event.ConnectionListener
import com.mongodb.async.SingleResultFuture
import com.mongodb.protocol.message.CommandMessage
import com.mongodb.protocol.message.MessageSettings
import org.bson.BsonBinaryWriter
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.ByteBuf
import org.bson.ByteBufNIO
import org.bson.codecs.EncoderContext
import org.bson.io.BasicOutputBuffer
import org.bson.io.OutputBuffer
import org.junit.experimental.categories.Category
import org.mongodb.Document
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static MongoNamespace.COMMAND_COLLECTION_NAME
import static java.util.concurrent.TimeUnit.SECONDS

@SuppressWarnings(['UnusedVariable'])
class InternalStreamConnectionSpecification extends Specification {
    private static final String CLUSTER_ID = '1'
    def helper = new StreamHelper()
    def stream = Stub(Stream)
    def listener = Mock(ConnectionListener)
    def connectionInitializer = Mock(ConnectionInitializer)

    def 'should fire connection opened event'() {
        given:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) =  helper.isMaster()
        stream.read(_) >>> helper.read([messageId1])

        when:
        connection.sendMessage(buffers1, messageId1)

        then:
        1 * listener.connectionOpened(_)
    }

    def 'should fire connection closed event'() {
        given:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)

        when:
        connection.close()

        then:
        1 * listener.connectionClosed(_)
    }

    def 'should fire messages sent event'() {
        given:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        stream.read(_) >>> helper.read([messageId1])
        when:
        connection.sendMessage(buffers1, messageId1)

        then:
        1 * listener.messagesSent(_)
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should fire message sent event asynchronously'() {
        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        def (buffers1, messageId1) = helper.isMaster()
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }
        def latch = new CountDownLatch(1);

        when:
        connection.sendMessageAsync(buffers1, messageId1, new SingleResultCallback<Void>() {
            @Override
            void onResult(final Void result, final MongoException e) {
                latch.countDown();
            }
        })
        latch.await()

        then:
        1 * listener.messagesSent(_)
    }

    def 'should fire message received event'() {
        given:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        stream.read(_) >>> helper.read([messageId1])

        when:
        connection.receiveMessage(messageId1)

        then:
        1 * listener.messageReceived(_)
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should fire message received event asynchronously'() {
        given:
        def (buffers1, messageId1) = helper.isMaster()
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.header(messageId1))
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)

        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }
        def latch = new CountDownLatch(1);

        when:
        connection.receiveMessageAsync(messageId1, new SingleResultCallback<ResponseBuffers>() {
            @Override
            void onResult(final ResponseBuffers result, final MongoException e) {
                latch.countDown();
            }
        })
        latch.countDown();
        latch.await()

        then:
        1 * listener.messageReceived(_)
    }

    def 'should handle out of order messages on the stream'() {
        // Connect then: Send(1), Send(2), Send(3), Receive(3), Receive(2), Receive(1)
        given:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        def (buffers2, messageId2) = helper.isMaster()
        def (buffers3, messageId3) = helper.isMaster()
        stream.read(_) >>> helper.read([messageId1, messageId2, messageId3], ordered)

        when:
        connection.sendMessage(buffers1, messageId1)
        connection.sendMessage(buffers2, messageId2)
        connection.sendMessage(buffers3, messageId3)

        then:
        connection.receiveMessage(messageId3).replyHeader.responseTo == messageId3
        connection.receiveMessage(messageId2).replyHeader.responseTo == messageId2
        connection.receiveMessage(messageId1).replyHeader.responseTo == messageId1

        where:
        ordered << [true, false]
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should handle out of order messages on the stream asynchronously'() {
        // Connect then: SendAsync(1), SendAsync(2), SendAsync(3), ReceiveAsync(3), ReceiveAsync(2), ReceiveAsync(1)
        given:
        def sndLatch = new CountDownLatch(3)
        def rcvdLatch = new CountDownLatch(3)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers3, messageId3, sndCallbck3, rcvdCallbck3, fSndResult3, fRespBuffers3) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2, messageId3], ordered)

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }

        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }

        when:
        connection.sendMessageAsync(buffers1, messageId1, sndCallbck1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)
        connection.sendMessageAsync(buffers3, messageId3, sndCallbck3)

        then:
        sndLatch.await(10, SECONDS)

        when:
        connection.receiveMessageAsync(messageId3, rcvdCallbck3)
        connection.receiveMessageAsync(messageId2, rcvdCallbck2)
        connection.receiveMessageAsync(messageId1, rcvdCallbck1)
        rcvdLatch.await(10, SECONDS)

        then:
        fRespBuffers1.get().replyHeader.responseTo == messageId1
        fRespBuffers2.get().replyHeader.responseTo == messageId2
        fRespBuffers3.get().replyHeader.responseTo == messageId3

        where:
        ordered << [true, false]
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should handle out of order messages on the stream mixed synchronicity'() {
        // Connect then: Send(1), SendAsync(2), Send(3), ReceiveAsync(3), Receive(2), ReceiveAsync(1)
        given:
        def sndLatch = new CountDownLatch(1)
        def rcvdLatch = new CountDownLatch(2)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers3, messageId3, sndCallbck3, rcvdCallbck3, fSndResult3, fRespBuffers3) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2, messageId3])

        stream.read(36) >> { helper.header(messageId2) }
        stream.read(74) >> { helper.body() }
        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)

        when:
        connection.sendMessage(buffers1, messageId1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)
        connection.sendMessage(buffers3, messageId3)

        then:
        sndLatch.await(10, SECONDS)

        when:
        connection.receiveMessageAsync(messageId3, rcvdCallbck3)
        ResponseBuffers responseBuffers2 = connection.receiveMessage(messageId2)
        connection.receiveMessageAsync(messageId1, rcvdCallbck1)
        rcvdLatch.await(10, SECONDS)

        then:
        fRespBuffers1.get().replyHeader.responseTo == messageId1
        responseBuffers2.replyHeader.responseTo == messageId2
        fRespBuffers3.get().replyHeader.responseTo == messageId3
    }

    def 'should close the stream when initialization throws an exception'() {
        given:
        connectionInitializer.initialize() >> { throw new MongoInternalException('Something went wrong') }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        def (buffers2, messageId2) = helper.isMaster()

        when:
        connection.sendMessage(buffers1, messageId1)

        then:
        thrown MongoInternalException
        connection.isClosed()

        when:
        connection.sendMessage(buffers2, messageId2)

        then:
        thrown MongoSocketClosedException
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should close the stream when initialization throws an exception asynchronously'() {
        given:
        def sndLatch = new CountDownLatch(2)
        def rcvdLatch = new CountDownLatch(2)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2])

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, new MongoInternalException('Something went wrong'));
        }

        when:
        connection.sendMessageAsync(buffers1, messageId1, sndCallbck1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)
        sndLatch.await(10, SECONDS)

        then:
        connection.isClosed()

        when:
        fSndResult1.get(10, SECONDS)

        then:
        thrown MongoInternalException

        when:
        fSndResult2.get(10, SECONDS)

        then:
        thrown MongoSocketClosedException
    }

    def 'should close the stream when writing a message throws an exception'() {
        given:
        stream.write(_) >> { throw new IOException('Something went wrong') }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        def (buffers2, messageId2) = helper.isMaster()

        when:
        connection.sendMessage(buffers1, messageId1)

        then:
        connection.isClosed()
        thrown MongoSocketWriteException

        when:
        connection.sendMessage(buffers2, messageId2)

        then:
        thrown MongoSocketClosedException
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should close the stream when writing a message throws an exception asynchronously'() {
        given:
        def sndLatch = new CountDownLatch(2)
        def rcvdLatch = new CountDownLatch(2)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2])
        int seen = 0

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            if (seen == 0) {
                seen += 1
                return callback.failed(new IOException('Something went wrong'))
            }
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }

        when:
        connection.sendMessageAsync(buffers1, messageId1, sndCallbck1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)
        sndLatch.await(10, SECONDS)

        then:
        connection.isClosed()

        when:
        fSndResult1.get(10, SECONDS)

        then:
        thrown MongoSocketWriteException

        when:
        fSndResult2.get(10, SECONDS)

        then:
        thrown MongoSocketClosedException
    }

    def 'should close the stream when reading the message header throws an exception'() {
        given:
        stream.read(36) >> { throw new IOException('Something went wrong') }
        stream.read(74) >> helper.body()

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        def (buffers1, messageId1) = helper.isMaster()
        def (buffers2, messageId2) = helper.isMaster()

        when:
        connection.sendMessage(buffers1, messageId1)
        connection.sendMessage(buffers2, messageId2)
        connection.receiveMessage(messageId1)

        then:
        connection.isClosed()
        thrown MongoSocketReadException

        when:
        connection.receiveMessage(messageId2)

        then:
        thrown MongoSocketClosedException
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should close the stream when reading the message header throws an exception asynchronously'() {
        given:
        int seen = 0
        def sndLatch = new CountDownLatch(2)
        def rcvdLatch = new CountDownLatch(2)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2])

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            if (seen == 0) {
                seen += 1
                return handler.failed(new IOException('Something went wrong'))
            }
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }

        when:
        connection.sendMessageAsync(buffers1, messageId1, sndCallbck1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)

        then:
        sndLatch.await(10, SECONDS)

        when:
        connection.receiveMessageAsync(messageId1, rcvdCallbck1)
        connection.receiveMessageAsync(messageId2, rcvdCallbck2)
        rcvdLatch.await(10, SECONDS)

        then:
        connection.isClosed()

        when:
        fRespBuffers1.get(10, SECONDS)

        then:
        thrown MongoSocketReadException

        when:
        fRespBuffers2.get(10, SECONDS)

        then:
        thrown MongoSocketClosedException
    }

    def 'should close the stream when reading the message body throws an exception'() {
        given:
        def (buffers1, messageId1) = helper.isMaster()
        def (buffers2, messageId2) = helper.isMaster()
        def headers = helper.generateHeaders([messageId1, messageId2])

        stream.read(36) >> { headers.pop() }
        stream.read(74) >> { throw new IOException('Something went wrong') }

        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)

        when:
        connection.sendMessage(buffers1, messageId1)
        connection.sendMessage(buffers2, messageId2)
        connection.receiveMessage(messageId1)

        then:
        connection.isClosed()
        thrown MongoSocketReadException

        when:
        connection.receiveMessage(messageId2)

        then:
        thrown MongoSocketClosedException
    }

    @Category(Async)
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should close the stream when reading the message body throws an exception asynchronously'() {
        given:
        int seen = 0
        def sndLatch = new CountDownLatch(2)
        def rcvdLatch = new CountDownLatch(2)
        def (buffers1, messageId1, sndCallbck1, rcvdCallbck1, fSndResult1, fRespBuffers1) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def (buffers2, messageId2, sndCallbck2, rcvdCallbck2, fSndResult2, fRespBuffers2) = helper.isMasterAsync(sndLatch, rcvdLatch)
        def headers = helper.generateHeaders([messageId1, messageId2])

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            if (seen == 0) {
                seen += 1
                return handler.failed(new IOException('Something went wrong'))
            }
            handler.completed(helper.body())
        }
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }

        when:
        connection.sendMessageAsync(buffers1, messageId1, sndCallbck1)
        connection.sendMessageAsync(buffers2, messageId2, sndCallbck2)

        then:
        sndLatch.await(10, SECONDS)

        when:
        connection.receiveMessageAsync(messageId1, rcvdCallbck1)
        connection.receiveMessageAsync(messageId2, rcvdCallbck2)
        rcvdLatch.await()

        then:
        connection.isClosed()

        when:
        fRespBuffers1.get(10, SECONDS)

        then:
        thrown MongoSocketReadException

        when:
        fRespBuffers2.get(10, SECONDS)

        then:
        thrown MongoSocketClosedException
    }


    @Category(Slow)
    def 'should have threadsafe connection pipelining'() {
        given:
        int threads = 10
        int numberOfOperations = 100000
        ExecutorService pool = Executors.newFixedThreadPool(threads)
        def messages = (1..numberOfOperations).collect { helper.isMaster() }
        def headers = helper.generateHeaders( messages.collect { buffer, messageId -> messageId })
        stream.read(36) >> { headers.pop() }
        stream.read(74) >> { helper.body() }

        when:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)

        then:
        (1..numberOfOperations).each { n ->
            def conds = new AsyncConditions()
            def (buffers, messageId) = messages.pop()
            pool.submit({ connection.sendMessage(buffers, messageId) } as Runnable)
            pool.submit({
                            conds.evaluate {
                                assert connection.receiveMessage(messageId).replyHeader.responseTo == messageId
                            }
                        } as Runnable)

            conds.await(10)
        }

        cleanup:
        pool.shutdown()
    }

    @Category([Async, Slow])
    @IgnoreIf({ javaVersion < 1.7 })
    def 'should have threadsafe connection pipelining asynchronously'() {
        given:
        int threads = 10
        int numberOfOperations = 100000
        ExecutorService pool = Executors.newFixedThreadPool(threads)

        def messages = (1..numberOfOperations).collect {
            def sndLatch = new CountDownLatch(1)
            def rcvLatch = new CountDownLatch(1)

            helper.isMasterAsync(sndLatch, rcvLatch) + sndLatch + rcvLatch
        }
        def headers = messages.collect { buffers, messageId, sndCallbck, rcvdCallbck, fSndResult, fRespBuffers, sndLatch,
                                         rcvLatch -> helper.header(messageId) }

        stream.writeAsync(_, _) >> { List<ByteBuf> buffers, AsyncCompletionHandler<Void> callback ->
            callback.completed(null)
        }
        stream.readAsync(36, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(headers.pop())
        }
        stream.readAsync(74, _) >> { int numBytes, AsyncCompletionHandler<ByteBuf> handler ->
            handler.completed(helper.body())
        }

        when:
        def connection = new InternalStreamConnection(CLUSTER_ID, stream, connectionInitializer, listener)
        connectionInitializer.initialize(_) >> { SingleResultCallback<Void> callback ->
            callback.onResult(null, null);
        }

        then:
        (1..numberOfOperations).each { n ->
            def conds = new AsyncConditions()
            def (buffers, messageId, sndCallbck, rcvdCallbck, fSndResult, fRespBuffers, sndLatch,  rcvLatch) = messages.pop()

            pool.submit({ connection.sendMessageAsync(buffers, messageId, sndCallbck) } as Runnable)
            pool.submit({
                            connection.receiveMessageAsync(messageId, rcvdCallbck)
                            conds.evaluate {
                                assert fRespBuffers.get().replyHeader.responseTo == messageId
                            }
                        } as Runnable
            )
            conds.await(10)
        }

        cleanup:
        pool.shutdown()
    }

    class StreamHelper {
        int nextMessageId = 900000 // Generates a message then adds one to the id

        def read(List<Integer> messageIds) {
            read(messageIds, true)
        }

        def read(List<Integer> messageIds, boolean ordered) {
            List<ByteBuf> headers = messageIds.collect { header(it) }
            List<ByteBuf> bodies = messageIds.collect { body() }
            if (!ordered) {
                Collections.shuffle(headers, new SecureRandom())
            }
            [headers, bodies].transpose().flatten()
        }

        def header(messageId) {
            ByteBuffer headerByteBuffer = ByteBuffer.allocate(36).with {
                order(ByteOrder.LITTLE_ENDIAN);
                putInt(110);           // messageLength
                putInt(4);             // requestId
                putInt(messageId);     // responseTo
                putInt(1);             // opCode
                putInt(0);             // responseFlags
                putLong(0);            // cursorId
                putInt(0);             // starting from
                putInt(1);             // number returned
            }
            headerByteBuffer.flip()
            new ByteBufNIO(headerByteBuffer)
        }

        def body() {
            def okResponse = ['connectionId': 1, 'n': 0, 'syncMillis': 0, 'writtenTo': null, 'err': null, 'ok': 1] as Document
            def binaryResponse = new BsonBinaryWriter(new BasicOutputBuffer(), false)
            new DocumentCodec().encode(binaryResponse, okResponse, EncoderContext.builder().build())
            binaryResponse.buffer.byteBuffers.get(0)
        }

        def generateHeaders(List<Integer> messageIds) {
            generateHeaders(messageIds, true)
        }

        def generateHeaders(List<Integer> messageIds, boolean ordered) {
            List<ByteBuf> headers = messageIds.collect { header(it) }.reverse()
            if (!ordered) {
                Collections.shuffle(headers, new SecureRandom())
            }
            headers
        }

        def isMaster() {
            def command = new CommandMessage(new MongoNamespace('admin', COMMAND_COLLECTION_NAME).getFullName(),
                                             new BsonDocument('ismaster', new BsonInt32(1)),
                                             EnumSet.noneOf(CursorFlag),
                                             MessageSettings.builder().build());
            OutputBuffer buffer = new BasicOutputBuffer();
            command.encode(buffer);
            nextMessageId++
            [buffer.byteBuffers, nextMessageId]
        }

        def isMasterAsync(CountDownLatch sndLatch, CountDownLatch rcvdLatch) {
            SingleResultFuture<Void> futureSendResult = new SingleResultFuture<Void>()
            SingleResultFuture<ResponseBuffers> futureResponseBuffers = new SingleResultFuture<ResponseBuffers>()
            isMaster() + [new SingleResultCallback<Void>() {
                @Override
                void onResult(final Void result, final MongoException e) {
                    sndLatch.countDown()
                    futureSendResult.init(result, e)
                }
            }, new SingleResultCallback<ResponseBuffers>() {
                @Override
                void onResult(final ResponseBuffers result, final MongoException e) {
                    rcvdLatch.countDown()
                    futureResponseBuffers.init(result, e)
                }
            }, futureSendResult, futureResponseBuffers]

        }
    }
}
