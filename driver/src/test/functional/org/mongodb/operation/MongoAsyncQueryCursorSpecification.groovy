/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package org.mongodb.operation

import category.Async
import category.Slow
import org.bson.types.BsonDocumentWrapper
import org.bson.types.Timestamp
import org.junit.experimental.categories.Category
import org.mongodb.Block
import org.mongodb.CreateCollectionOptions
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.MongoFuture
import org.mongodb.MongoInternalException
import org.mongodb.binding.AsyncClusterBinding
import org.mongodb.binding.AsyncConnectionSource
import org.mongodb.binding.AsyncReadBinding
import org.mongodb.codecs.DocumentCodec
import org.mongodb.connection.Connection
import org.mongodb.protocol.QueryProtocol
import org.mongodb.protocol.QueryResult
import spock.lang.Shared

import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.Assume.assumeFalse
import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getAsyncCluster
import static org.mongodb.Fixture.getBinding
import static org.mongodb.Fixture.isSharded
import static org.mongodb.ReadPreference.primary
import static org.mongodb.operation.QueryFlag.Exhaust

@Category(Async)
class MongoAsyncQueryCursorSpecification extends FunctionalSpecification {

    @Shared
    List<Document> documentList
    private List<Document> documentResultList
    private AsyncReadBinding binding
    private AsyncConnectionSource source

    def setup() {
        documentList = []
        documentResultList = []
        (1..1000).each {
            documentList.add(new Document('_id', it))

        }
        getCollectionHelper().insertDocuments(* documentList)

        binding = new AsyncClusterBinding(getAsyncCluster(), primary(), 1, SECONDS)
        source = binding.getReadConnectionSource().get()
    }

    def cleanup() {
        binding.release()
    }

    def 'Cursor should iterate all contents'() {
        given:
        QueryResult<Document> firstBatch = executeQuery()

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                            firstBatch, 0, 2, new DocumentCodec(),
                                            source)
                .forEach(new TestBlock()).get()

        then:
        documentList == documentResultList
    }

    def 'Cursor should support limit'() {
        given:
        QueryResult<Document> firstBatch = executeQuery()

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                            firstBatch, 100, 0, new DocumentCodec(),
                                            source)
                .forEach(new TestBlock()).get()

        then:
        documentResultList == documentList[0..99]
    }

    def 'Cursor should support Exhaust'() {
        assumeFalse(isSharded())

        setup:
        Connection connection = source.getConnection().get()
        QueryResult<Document> firstBatch = executeQuery(getOrderedByIdQuery(),
                                                        2, EnumSet.of(Exhaust),
                                                        connection)

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                            firstBatch, 0, 2, new DocumentCodec(),
                                            connection)
                .forEach(new TestBlock()).get()

        then:
        documentResultList == documentList

        cleanup:
        connection.release()
    }

    def 'Cursor should support Exhaust and limit'() {
        assumeFalse(isSharded())

        setup:
        Connection connection = source.getConnection().get()
        QueryResult<Document> firstBatch = executeQuery(getOrderedByIdQuery(), 2, EnumSet.of(Exhaust), connection)

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                            firstBatch, 5, 2, new DocumentCodec(),
                                            connection)
                .forEach(new TestBlock()).get()

        then:
        documentResultList == documentList[0..4]

        cleanup:
        connection.release()
    }

    def 'Cursor should support Exhaust and Discard'() {
        assumeFalse(isSharded())

        setup:
        AsyncConnectionSource readConnectionSource = getAsyncBinding().getReadConnectionSource().get()
        Connection connection = readConnectionSource.getConnection().get()
        QueryResult<Document> firstBatch = executeQuery(getOrderedByIdQuery(), 2, EnumSet.of(Exhaust), connection)

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(), firstBatch, 5, 2, new DocumentCodec(), connection)
                .forEach(new TestBlock(1)).get()

        then:
        thrown(MongoInternalException)
        documentResultList == documentList[0..0]
        def docs = executeQuery(getOrderedByIdQuery(), 1, EnumSet.of(Exhaust), connection).getResults()
        [[_id: 1]] == docs

        cleanup:
        connection.release()
        readConnectionSource.release()
    }

    def 'Cursor should support early termination'() {
        assumeFalse(isSharded())

        setup:
        AsyncConnectionSource source = getAsyncBinding().getReadConnectionSource().get()
        Connection connection = source.getConnection().get()
        QueryResult<Document> firstBatch = executeQuery(getOrderedByIdQuery(), 2, EnumSet.of(Exhaust), connection)
        TestBlock block = new TestBlock(1)

        when:
        new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                            firstBatch, 5, 2, new DocumentCodec(),
                                            connection)
                .forEach(block).get()

        then:
        thrown(MongoInternalException)
        block.getIterations() == 1

        cleanup:
        connection.release()
        source.release()
    }

    @Category(Slow)
    def 'Cursor should be tailable'() {
        setup:
        AsyncConnectionSource source = getAsyncBinding().getReadConnectionSource().get()
        Connection connection = source.getConnection().get()
        new DropCollectionOperation(getNamespace()).execute(getBinding())
        new CreateCollectionOperation(getDatabaseName(), new CreateCollectionOptions(getCollectionName(), true, 1000)).execute(getBinding())
        def ts = new Timestamp(5, 0)
        getCollectionHelper().insertDocuments([_id: 1, ts: ts] as Document)

        QueryResult<Document> firstBatch = executeQuery([ts: ['$gte': ts] as Document ] as Document, 2,
                                                        EnumSet.of(QueryFlag.Tailable, QueryFlag.AwaitData), connection)
        TestBlock block = new TestBlock(2)

        when:
        MongoFuture<Void> future = new MongoAsyncQueryCursor<Document>(collection.getNamespace(),
                                                                       firstBatch, 5, 2, new DocumentCodec(),
                                                                       source).forEach(block)
        then:
        block.getIterations() == 1

        when:
        getCollectionHelper().insertDocuments([_id: 2, ts: new Timestamp(1, 0)] as Document)
        getCollectionHelper().insertDocuments([_id: 3, ts: new Timestamp(6, 0)] as Document)
        getCollectionHelper().insertDocuments([_id: 4, ts: new Timestamp(8, 0)] as Document)
        future.get()

        then:
        thrown(MongoInternalException)
        block.getIterations() == 2
        documentResultList *.get('_id') == [1, 3]

        cleanup:
        connection.release()
        source.release()
    }

    private static Document getOrderedByIdQuery() {
        new Document('$query', new Document()).append('$orderby', new Document('_id', 1))
    }

    private QueryResult<Document> executeQuery() {
        executeQuery(getOrderedByIdQuery(), 0, EnumSet.noneOf(QueryFlag))
    }

    private QueryResult<Document> executeQuery(final Document query, final int numberToReturn, final EnumSet<QueryFlag> queryFlag) {
        Connection connection = source.getConnection().get()
        try {
            executeQuery(query, numberToReturn, queryFlag, connection)
        } finally {
            connection.release()
        }
    }

    private QueryResult<Document> executeQuery(final Document query, final int numberToReturn, final EnumSet<QueryFlag> queryFlag,
                                               final Connection connection) {
        new QueryProtocol<Document>(collection.getNamespace(), queryFlag, 0, numberToReturn,
                                           new BsonDocumentWrapper<Document>(query, new DocumentCodec()), null,
                                           new DocumentCodec()).execute(connection)
    }

    private final class TestBlock implements Block<Document> {
        private final int count
        private int iterations

        private TestBlock() {
            this(Integer.MAX_VALUE)
        }

        private TestBlock(final int count) {
            this.count = count
        }

        @SuppressWarnings(['ThrowRuntimeException'])
        @Override
        void apply(final Document document) {
            if (iterations >= count) {
                throw new RuntimeException('Discard the rest')
            }
            iterations++
            documentResultList.add(document)
        }

        int getIterations() {
            iterations
        }
    }
}
