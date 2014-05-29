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
import org.mongodb.FunctionalSpecification
import org.mongodb.Index
import org.mongodb.codecs.DocumentCodec
import org.mongodb.MongoServerException

import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding
import static org.mongodb.OrderBy.ASC

class CreateIndexesSpecification extends FunctionalSpecification {
    def idIndex = ['_id': 1]
    def field1Index = ['field': 1]
    def field2Index = ['field2': 1]

    def 'should be able to create a single index'() {
        given:
        def index = Index.builder().addKey('field', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index], getNamespace())

        when:
        createIndexesOperation.execute(getBinding())

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index)
    }

    @Category(Async)
    def 'should be able to create a single index asynchronously'() {

        given:
        def index = Index.builder().addKey('field', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index], getNamespace())

        when:
        createIndexesOperation.executeAsync(getAsyncBinding()).get()

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index)
    }

    def 'should be able to create multiple indexes'() {
        given:
        def index1 = Index.builder().addKey('field', ASC).build()
        def index2 = Index.builder().addKey('field2', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index1, index2], getNamespace())


        when:
        createIndexesOperation.execute(getBinding())

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index, field2Index)
    }

    @Category(Async)
    def 'should be able to create multiple indexes asynchronously'() {

        given:
        def index1 = Index.builder().addKey('field', ASC).build()
        def index2 = Index.builder().addKey('field2', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index1, index2], getNamespace())

        when:
        createIndexesOperation.executeAsync(getAsyncBinding()).get()

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index, field2Index)
    }

    def 'should be able to handle duplicated indexes'() {
        given:
        def index = Index.builder().addKey('field', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index, index], getNamespace())

        when:
        createIndexesOperation.execute(getBinding())

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index)
    }

    @Category(Async)
    def 'should be able to handle duplicated indexes asynchronously'() {

        given:
        def index = Index.builder().addKey('field', ASC).build()
        def createIndexesOperation = new CreateIndexesOperation([index, index], getNamespace())

        when:
        createIndexesOperation.executeAsync(getAsyncBinding()).get()

        then:
        getIndexes()*.get('key') containsAll(idIndex, field1Index)
    }

    def 'should throw when trying to build an invalid index'() {
        given:
        def index = Index.builder().build()
        def createIndexesOperation = new CreateIndexesOperation([index], getNamespace())

        when:
        createIndexesOperation.execute(getBinding())

        then:
        thrown(MongoServerException)
    }

    @Category(Async)
    def 'should throw when trying to build an invalid index asynchronously'() {
        given:
        def index = Index.builder().build()
        def createIndexesOperation = new CreateIndexesOperation([index], getNamespace())

        when:
        createIndexesOperation.execute(getBinding())

        then:
        thrown(MongoServerException)
    }

    def getIndexes() {
        new GetIndexesOperation(getNamespace(), new DocumentCodec()).execute(getBinding())
    }

}
