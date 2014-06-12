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
import org.junit.experimental.categories.Category
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.Index
import org.mongodb.codecs.DocumentCodec

import static java.util.concurrent.TimeUnit.SECONDS
import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding
import static org.mongodb.OrderBy.ASC

class GetIndexesOperationSpecification extends FunctionalSpecification {
    def 'should return default index on Collection that exists'() {
        given:
        def operation = new GetIndexesOperation(getNamespace(), new DocumentCodec())
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))

        when:
        List<Document> indexes = operation.execute(getBinding())

        then:
        indexes.size() == 1
        indexes[0].name == '_id_'
    }

    @Category(Async)
    def 'should return default index on Collection that exists asynchronously'() {
        given:
        def operation = new GetIndexesOperation(getNamespace(), new DocumentCodec())
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))

        when:
        List<Document> indexes = operation.executeAsync(getAsyncBinding()).get(1, SECONDS)

        then:
        indexes.size() == 1
        indexes[0].name == '_id_'
    }

    def 'should return created indexes on Collection'() {
        given:
        def operation = new GetIndexesOperation(getNamespace(), new DocumentCodec())
        createIndexes(Index.builder().addKey('theField', ASC).build())

        when:
        List<Document> indexes = operation.execute(getBinding())

        then:
        indexes.size() == 2
        indexes[0].name == '_id_'
        indexes[1].name == 'theField_1'
    }

    @Category(Async)
    def 'should return created indexes on Collection asynchronously'() {
        given:
        def operation = new GetIndexesOperation(getNamespace(), new DocumentCodec())
        createIndexes(Index.builder().addKey('theField', ASC).build())

        when:
        List<Document> indexes = operation.executeAsync(getAsyncBinding()).get(1, SECONDS)

        then:
        indexes.size() == 2
        indexes[0].name == '_id_'
        indexes[1].name == 'theField_1'
    }

    @SuppressWarnings('FactoryMethodName')
    def createIndexes(Index[] indexes) {
        new CreateIndexesOperation(indexes.toList(), getNamespace()).execute(getBinding())
    }

}
