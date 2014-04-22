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
import org.mongodb.MongoNamespace
import org.mongodb.MongoServerException

import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding

class RenameCollectionOperationSpecification extends FunctionalSpecification {

    def cleanup() {
        new DropCollectionOperation(new MongoNamespace(getDatabaseName(), 'newCollection')).execute(getBinding())
    }

    def 'should return rename a collection'() {
        given:
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getDatabaseName(), getCollectionName(), 'newCollection', false).execute(getBinding())

        then:
        !collectionNameExists(getCollectionName())
        collectionNameExists('newCollection')
    }

    @Category(Async)
    def 'should return rename a collection asynchronously'() {
        given:
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getDatabaseName(), getCollectionName(), 'newCollection', false).executeAsync(getAsyncBinding()).get()

        then:
        !collectionNameExists(getCollectionName())
        collectionNameExists('newCollection')
    }

    def 'should throw if not drop and collection exists'() {
        given:
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getDatabaseName(), getCollectionName(), getCollectionName(), false).execute(getBinding())

        then:
        thrown(MongoServerException)
        collectionNameExists(getCollectionName())
    }

    @Category(Async)
    def 'should throw if not drop and collection exists asynchronously'() {
        given:
        getCollectionHelper().insertDocuments(new Document('documentThat', 'forces creation of the Collection'))
        assert collectionNameExists(getCollectionName())

        when:
        new RenameCollectionOperation(getDatabaseName(), getCollectionName(), getCollectionName(), false).execute(getBinding())

        then:
        thrown(MongoServerException)
        collectionNameExists(getCollectionName())
    }

    def collectionNameExists(String collectionName) {
        new GetCollectionNamesOperation(databaseName).execute(getBinding()).contains(collectionName);
    }

}
