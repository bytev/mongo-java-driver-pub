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
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.codecs.DocumentCodec
import org.mongodb.test.Worker
import org.mongodb.test.WorkerCodec

import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding

class FindAndRemoveOperationSpecification extends FunctionalSpecification {
    private final DocumentCodec documentCodec = new DocumentCodec()
    private final WorkerCodec workerCodec = new WorkerCodec()

    def 'should remove single document'() {

        given:
        Document pete = new Document('name', 'Pete').append('job', 'handyman')
        Document sam = new Document('name', 'Sam').append('job', 'plumber')

        getCollectionHelper().insertDocuments(pete, sam)

        when:
        FindAndRemove findAndRemove = new FindAndRemove().where(new BsonDocument('name', new BsonString('Pete')));

        FindAndRemoveOperation<Document> operation = new FindAndRemoveOperation<Document>(getNamespace(), findAndRemove,
                                                                                          documentCodec)
        Document returnedDocument = operation.execute(getBinding())

        then:
        getCollectionHelper().find().size() == 1;
        getCollectionHelper().find().first().getString('name') == 'Sam'
        returnedDocument.getString('name') == 'Pete'
    }


    @Category(Async)
    def 'should remove single document asynchronously'() {
        given:
        Document pete = new Document('name', 'Pete').append('job', 'handyman')
        Document sam = new Document('name', 'Sam').append('job', 'plumber')

        getCollectionHelper().insertDocuments(pete, sam)

        when:
        FindAndRemove findAndRemove = new FindAndRemove().where(new BsonDocument('name', new BsonString('Pete')));

        FindAndRemoveOperation<Document> operation = new FindAndRemoveOperation<Document>(getNamespace(), findAndRemove,
                                                                                          documentCodec)
        Document returnedDocument = operation.executeAsync(getAsyncBinding()).get()

        then:
        getCollectionHelper().find().size() == 1;
        getCollectionHelper().find().first().getString('name') == 'Sam'
        returnedDocument.getString('name') == 'Pete'
    }

    def 'should remove single document when using custom codecs'() {
        given:
        Worker pete = new Worker('Pete', 'handyman', new Date(), 3)
        Worker sam = new Worker('Sam', 'plumber', new Date(), 7)
        getWorkerCollectionHelper().insertDocuments(pete, sam)

        when:
        FindAndRemove<Worker> findAndRemove = new FindAndRemove<Worker>().where(new BsonDocument('name', new BsonString('Pete')));

        FindAndRemoveOperation<Worker> operation = new FindAndRemoveOperation<Worker>(getNamespace(), findAndRemove,
                                                                                      workerCodec)
        Worker returnedDocument = operation.execute(getBinding())

        then:
        getWorkerCollectionHelper().find().size() == 1;
        getWorkerCollectionHelper().find().first() == sam
        returnedDocument == pete
    }

    @Category(Async)
    def 'should remove single document when using custom codecs asynchronously'() {
        given:
        Worker pete = new Worker('Pete', 'handyman', new Date(), 3)
        Worker sam = new Worker('Sam', 'plumber', new Date(), 7)
        getWorkerCollectionHelper().insertDocuments(pete, sam)

        when:
        FindAndRemove<Worker> findAndRemove = new FindAndRemove<Worker>().where(new BsonDocument('name', new BsonString('Pete')));

        FindAndRemoveOperation<Worker> operation = new FindAndRemoveOperation<Worker>(getNamespace(), findAndRemove,
                                                                                      workerCodec)
        Worker returnedDocument = operation.execute(getBinding())

        then:
        getWorkerCollectionHelper().find().size() == 1;
        getWorkerCollectionHelper().find().first() == sam
        returnedDocument == pete
    }

}
