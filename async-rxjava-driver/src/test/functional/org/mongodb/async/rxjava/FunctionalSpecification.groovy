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

package org.mongodb.async.rxjava

import org.mongodb.Document
import org.mongodb.MongoNamespace
import spock.lang.Specification

import static org.mongodb.async.rxjava.Fixture.dropCollection
import static org.mongodb.async.rxjava.Fixture.getDefaultDatabase
import static org.mongodb.async.rxjava.Fixture.initializeCollection

class FunctionalSpecification extends Specification {
    protected MongoDatabase database;
    protected MongoCollection<Document> collection;

    def setup() {
        database = getDefaultDatabase()
        collection = initializeCollection(new MongoNamespace(database.getName(), getClass().getName()))
    }

    def cleanup() {
        if (collection != null) {
            dropCollection(collection.getNamespace())
        }
    }

    String getDatabaseName() {
        database.getName();
    }

    String getCollectionName() {
        collection.getName();
    }

    MongoNamespace getNamespace() {
        new MongoNamespace(getDatabaseName(), getCollectionName())
    }
}
