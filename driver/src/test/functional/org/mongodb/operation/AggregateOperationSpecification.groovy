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

package org.mongodb.operation
import category.Async
import org.bson.types.BsonDocument
import org.bson.types.BsonString
import org.junit.experimental.categories.Category
import org.mongodb.AggregationOptions
import org.mongodb.Block
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.codecs.DocumentCodec

import java.util.concurrent.TimeUnit

import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding
import static org.mongodb.Fixture.serverVersionAtLeast

class AggregateOperationSpecification extends FunctionalSpecification {

    def setup() {
        Document pete = new Document('name', 'Pete').append('job', 'handyman')
        Document sam = new Document('name', 'Sam').append('job', 'plumber')
        Document pete2 = new Document('name', 'Pete').append('job', 'electrician')
        getCollectionHelper().insertDocuments(pete, sam, pete2)
    }

    def 'should be able to aggregate'() {
        when:
        AggregateOperation op = new AggregateOperation<Document>(getNamespace(), [], new DocumentCodec(), aggregateOptions)
        def result = op.execute(getBinding());

        then:
        List<String> results = result.iterator()*.getString('name')
        results.size() == 3
        results.containsAll(['Pete', 'Sam'])

        where:
        aggregateOptions << generateOptions()
    }

    @Category(Async)
    def 'should be able to aggregate asynchronously'() {
        when:
        AggregateOperation op = new AggregateOperation<Document>(getNamespace(), [], new DocumentCodec(), aggregateOptions)
        List<Document> docList = []
        def cursor = op.executeAsync(getAsyncBinding()).get(1, TimeUnit.SECONDS)
        cursor.forEach(new Block<Document>() {
            @Override
            void apply(final Document value) {
                if (value != null) {
                    docList += value
                }
            }
        }).get(1, TimeUnit.SECONDS)

        then:
        List<String> results = docList.iterator()*.getString('name')
        results.size() == 3
        results.containsAll(['Pete', 'Sam'])

        where:
        aggregateOptions << generateOptions()
    }

    def 'should be able to aggregate with pipeline'() {
        when:
        AggregateOperation op = new AggregateOperation<Document>(getNamespace(),
                                                                 [new BsonDocument('$match',
                                                                                   new BsonDocument('job', new BsonString('plumber')))],
                                                                 new DocumentCodec(), aggregateOptions)
        def result = op.execute(getBinding());

        then:
        List<String> results = result.iterator()*.getString('name')
        results.size() == 1
        results == ['Sam']

        where:
        aggregateOptions << generateOptions()
    }

    @Category(Async)
    def 'should be able to aggregate with pipeline asynchronously'() {
        when:
        AggregateOperation op = new AggregateOperation<Document>(getNamespace(),
                                                                 [new BsonDocument('$match',
                                                                                   new BsonDocument('job', new BsonString('plumber')))],
                                                                 new DocumentCodec(), aggregateOptions)
        List<Document> docList = []
        def cursor = op.executeAsync(getAsyncBinding()).get(1, TimeUnit.SECONDS)
        cursor.forEach(new Block<Document>() {
            @Override
            void apply(final Document value) {
                if (value != null) {
                    docList += value
                }
            }
        }).get(1, TimeUnit.SECONDS)

        then:
        List<String> results = docList.iterator()*.getString('name')
        results.size() == 1
        results == ['Sam']

        where:
        aggregateOptions << generateOptions()
    }

    private static List<AggregationOptions> generateOptions() {
        def options = [AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.INLINE).build()]
        if ((serverVersionAtLeast([2, 6, 0]))) {
            options += AggregationOptions.builder().outputMode(AggregationOptions.OutputMode.CURSOR).build()
        }
        options
    }
}
