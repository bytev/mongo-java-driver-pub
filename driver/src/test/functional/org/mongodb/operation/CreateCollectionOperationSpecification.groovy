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
import org.mongodb.CreateCollectionOptions
import org.mongodb.FunctionalSpecification
import org.mongodb.MongoServerException

import static org.mongodb.Fixture.getBinding
import static org.mongodb.Fixture.getSession

class CreateCollectionOperationSpecification extends FunctionalSpecification {

    def 'should create a collection'() {
        given:
        assert !collectionNameExists(getCollectionName())

        when:
        new CreateCollectionOperation(getDatabaseName(), new CreateCollectionOptions(getCollectionName())).execute(getBinding())

        then:
        collectionNameExists(getCollectionName())
    }

    @Category(Async)
    def 'should create a collection asynchronously'() {
        given:
        assert !collectionNameExists(getCollectionName())

        when:
        new CreateCollectionOperation(getDatabaseName(), new CreateCollectionOptions(getCollectionName())).execute(getBinding())

        then:
        collectionNameExists(getCollectionName())
    }

    def 'should error when creating a collection that already exists'() {
        given:
        assert !collectionNameExists(getCollectionName())
        def operation = new CreateCollectionOperation(getDatabaseName(), new CreateCollectionOptions(getCollectionName()))
        operation.execute(getBinding())

        when:
        operation.execute(getBinding())

        then:
        thrown(MongoServerException)
        !collectionNameExists('nonExistingCollection')
    }

    @Category(Async)
    def 'should error when creating a collection that already exists asynchronously'() {
        given:
        assert !collectionNameExists(getCollectionName())
        def operation = new CreateCollectionOperation(getDatabaseName(), new CreateCollectionOptions(getCollectionName()))
        operation.execute(getBinding())

        when:
        operation.executeAsync(getSession()).get()

        then:
        thrown(MongoServerException)
        !collectionNameExists('nonExistingCollection')
    }

    def collectionNameExists(String collectionName) {
        new GetCollectionNamesOperation(databaseName).execute(getBinding()).contains(collectionName);
    }

}
