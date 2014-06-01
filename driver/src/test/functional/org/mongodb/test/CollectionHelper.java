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

package org.mongodb.test;

import org.bson.codecs.Codec;
import org.bson.types.BsonDocument;
import org.bson.types.BsonDocumentWrapper;
import org.mongodb.Document;
import org.mongodb.MongoCursor;
import org.mongodb.MongoNamespace;
import org.mongodb.WriteConcern;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.operation.CountOperation;
import org.mongodb.operation.Find;
import org.mongodb.operation.InsertOperation;
import org.mongodb.operation.InsertRequest;
import org.mongodb.operation.QueryOperation;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.mongodb.Fixture.getBinding;

public final class CollectionHelper<T> {

    private Codec<T> codec;
    private MongoNamespace namespace;

    public CollectionHelper(final Codec<T> codec, final MongoNamespace namespace) {
        this.codec = codec;
        this.namespace = namespace;
    }

    @SuppressWarnings("unchecked")
    public void insertDocuments(final T... documents) {
        for (T document : documents) {
            new InsertOperation<T>(namespace, true, WriteConcern.ACKNOWLEDGED,
                                   asList(new InsertRequest<T>(document)), codec).execute(getBinding());
        }
    }

    @SuppressWarnings("unchecked")
    public <I> void insertDocuments(final Codec<I> iCodec, final I... documents) {
        for (I document : documents) {
            new InsertOperation<I>(namespace, true, WriteConcern.ACKNOWLEDGED,
                                   asList(new InsertRequest<I>(document)), iCodec).execute(getBinding());
        }
    }

    public List<T> find() {
        MongoCursor<T> cursor = new QueryOperation<T>(namespace, new Find(), codec).execute(getBinding());
        List<T> results = new ArrayList<T>();
        while (cursor.hasNext()) {
            results.add(cursor.next());
        }
        return results;
    }

    public List<T> find(final Document filter) {
        MongoCursor<T> cursor = new QueryOperation<T>(namespace, new Find(wrap(filter)), codec).execute(getBinding());
        List<T> results = new ArrayList<T>();
        while (cursor.hasNext()) {
            results.add(cursor.next());
        }
        return results;
    }

    public long count() {
        return new CountOperation(namespace, new Find()).execute(getBinding());
    }

    public long count(final Document filter) {
        return new CountOperation(namespace, new Find(wrap(filter))).execute(getBinding());
    }

    public BsonDocument wrap(final Document document) {
        return new BsonDocumentWrapper<Document>(document, new DocumentCodec());
    }
}
