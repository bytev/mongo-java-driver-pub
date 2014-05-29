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
import org.bson.types.Binary
import org.junit.experimental.categories.Category
import org.mongodb.Document
import org.mongodb.FunctionalSpecification
import org.mongodb.codecs.DocumentCodec

import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding
import static org.mongodb.WriteConcern.ACKNOWLEDGED

class RemoveOperationSpecification extends FunctionalSpecification {
    def 'should remove a document'() {
        given:
        getCollectionHelper().insertDocuments(new Document('_id', 1))
        def op = new RemoveOperation(getNamespace(), true, ACKNOWLEDGED,
                                     [new RemoveRequest(new Document('_id', 1))],
                                     new DocumentCodec())

        when:
        op.execute(getBinding())

        then:
        getCollectionHelper().count() == 0
    }

    @Category(Async)
    def 'should remove a document asynchronously'() {
        given:
        getCollectionHelper().insertDocuments(new Document('_id', 1))
        def op = new RemoveOperation(getNamespace(), true, ACKNOWLEDGED,
                                     [new RemoveRequest(new Document('_id', 1))],
                                     new DocumentCodec())

        when:
        op.executeAsync(getAsyncBinding()).get()

        then:
        getCollectionHelper().count() == 0
    }

    def 'should split removes into batches'() {
        given:
        Document bigDoc = new Document('bytes', new Binary(new byte[1024 * 1024 * 16 - 2127]))
        Document smallerDoc = new Document('bytes', new Binary(new byte[1024 * 16 + 1980]))
        Document simpleDoc = new Document('_id', 1)
        getCollectionHelper().insertDocuments(simpleDoc)
        def op = new RemoveOperation(getNamespace(), true, ACKNOWLEDGED,
                                     [new RemoveRequest(bigDoc), new RemoveRequest(smallerDoc),  new RemoveRequest(simpleDoc)],
                                     new DocumentCodec())

        when:
        op.execute(getBinding())

        then:
        getCollectionHelper().count() == 0
    }

    @Category(Async)
    def 'should split removes into batches asynchronously'() {
        given:
        Document bigDoc = new Document('bytes', new Binary(new byte[1024 * 1024 * 16 - 2127]))
        Document smallerDoc = new Document('bytes', new Binary(new byte[1024 * 16 + 1980]))
        Document simpleDoc = new Document('_id', 1)
        getCollectionHelper().insertDocuments(simpleDoc)
        def op = new RemoveOperation(getNamespace(), true, ACKNOWLEDGED,
                                     [new RemoveRequest(bigDoc), new RemoveRequest(smallerDoc),  new RemoveRequest(simpleDoc)],
                                     new DocumentCodec())

        when:
        op.executeAsync(getAsyncBinding()).get()

        then:
        getCollectionHelper().count() == 0
    }

}
