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

package org.mongodb.async.rxjava;

import org.mongodb.Document;
import org.mongodb.MongoNamespace;
import rx.Observable;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Helper class for asynchronous tests.
 */
public final class Fixture {
    private static MongoClientImpl mongoClient;
    private static MongoDatabase defaultDatabase;

    private Fixture() {
    }

    public static synchronized MongoClient getMongoClient() {
        if (mongoClient == null) {
            mongoClient = new MongoClientImpl(org.mongodb.async.Fixture.getMongoClient());
        }
        return mongoClient;
    }

    public static synchronized MongoDatabase getDefaultDatabase() {
        if (defaultDatabase == null) {
            defaultDatabase = getMongoClient().getDatabase(org.mongodb.async.Fixture.getDefaultDatabase().getName());
        }
        return defaultDatabase;
    }

    public static MongoCollection<Document> initializeCollection(final MongoNamespace namespace) {
        org.mongodb.async.Fixture.initializeCollection(namespace);
        return getMongoClient().getDatabase(namespace.getDatabaseName()).getCollection(namespace.getCollectionName());
    }

    public static void dropCollection(final MongoNamespace namespace) {
        org.mongodb.async.Fixture.dropCollection(namespace);
    }


    public static <T> T get(final Observable<T> observable) {
        return observable.timeout(1, SECONDS).toBlockingObservable().last();
    }

    public static List<Document> getAsList(final Observable<Document> observable) {
        return observable.timeout(1, SECONDS).toList().toBlockingObservable().last();
    }
}
