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



package org.mongodb.protocol

import org.bson.types.BsonDocument
import org.bson.types.BsonInt32
import org.mongodb.Document
import org.mongodb.MongoNamespace
import org.mongodb.SimpleBufferProvider
import org.mongodb.WriteConcern
import org.mongodb.codecs.DocumentCodec
import org.mongodb.connection.ByteBufferOutputBuffer
import org.mongodb.operation.InsertRequest
import org.mongodb.operation.RemoveRequest
import org.mongodb.operation.ReplaceRequest
import org.mongodb.protocol.message.DeleteCommandMessage
import org.mongodb.protocol.message.InsertCommandMessage
import org.mongodb.protocol.message.MessageSettings
import org.mongodb.protocol.message.ReplaceCommandMessage
import spock.lang.Specification

class WriteCommandLimitsSpecifications extends Specification {
    def 'should split an insert command when the number of items exceeds the maximum'() {
        given:
        def inserts = []
        (1..4).each {
            inserts.add(new InsertRequest(new Document()))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new InsertCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, inserts,
                                               new DocumentCodec(), MessageSettings.builder().maxWriteBatchSize(3).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == inserts.subList(3, 4)
    }

    def 'should split an insert command when the number of bytes exceeds the maximum'() {
        given:
        def inserts = []
        (1..4).each {
            inserts.add(new InsertRequest(new Document('_id', it)))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new InsertCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, inserts,
                                               new DocumentCodec(), MessageSettings.builder().maxDocumentSize(113).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == inserts.subList(3, 4)
    }

    def 'should split a delete command when the number of items exceeds the maximum'() {
        given:
        def deletes = []
        (1..4).each {
            deletes.add(new RemoveRequest(new BsonDocument()))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new DeleteCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, deletes,
                                               MessageSettings.builder().maxWriteBatchSize(3).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == deletes.subList(3, 4)
    }

    def 'should split a delete command when the number of bytes exceeds the maximum'() {
        given:
        def deletes = []
        (1..4).each {
            deletes.add(new RemoveRequest(new BsonDocument('_id', new BsonInt32(it))))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new DeleteCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, deletes,
                                               MessageSettings.builder().maxDocumentSize(187).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == deletes.subList(3, 4)
    }

    def 'should split a replace command when the number of items exceeds the maximum'() {
        given:
        def replaces = []
        (1..4).each {
            replaces.add(new ReplaceRequest(new BsonDocument('_id', new BsonInt32(it)), new Document()))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new ReplaceCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, replaces,
                                                new DocumentCodec(), MessageSettings.builder().maxWriteBatchSize(3).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == replaces.subList(3, 4)
    }

    def 'should split a replace command when the number of bytes exceeds the maximum'() {
        given:
        def replaces = []
        (1..4).each {
            replaces.add(new ReplaceRequest(new BsonDocument('_id', new BsonInt32(it)), new Document()))
        }

        def buffer = new ByteBufferOutputBuffer(new SimpleBufferProvider());
        def message = new ReplaceCommandMessage(new MongoNamespace('test', 'test'), true, WriteConcern.ACKNOWLEDGED, replaces,
                                                new DocumentCodec(), MessageSettings.builder().maxDocumentSize(175).build());

        when:
        def nextMessage = message.encode(buffer)

        then:
        nextMessage != null
        nextMessage.requests == replaces.subList(3, 4)
    }
}