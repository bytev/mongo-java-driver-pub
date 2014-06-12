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

package org.mongodb.operation;

import org.bson.codecs.BsonDocumentCodec;
import org.bson.types.BsonDocument;
import org.bson.types.BsonString;
import org.mongodb.Function;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.AsyncReadBinding;
import org.mongodb.binding.ReadBinding;
import org.mongodb.protocol.QueryProtocol;

import java.util.EnumSet;
import java.util.List;

import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.QueryOperationHelper.queryResultToList;
import static org.mongodb.operation.QueryOperationHelper.queryResultToListAsync;

/**
 * An operation that gets the names of all the collections in a database.
 *
 * @since 3.0
 */
public class GetCollectionNamesOperation implements AsyncReadOperation<List<String>>, ReadOperation<List<String>> {
    private final String databaseName;

    public GetCollectionNamesOperation(final String databaseName) {
        this.databaseName = notNull("databaseName", databaseName);
    }

    @Override
    public List<String> execute(final ReadBinding binding) {
        return queryResultToList(getNamespace(), getProtocol(), binding, transformer());
    }

    @Override
    public MongoFuture<List<String>> executeAsync(final AsyncReadBinding binding) {
        return queryResultToListAsync(getNamespace(), getProtocol(), binding, transformer());
    }

    private Function<BsonDocument, String> transformer() {
        return new Function<BsonDocument, String>() {
            @Override
            public String apply(final BsonDocument document) {
                String collectionName = ((BsonString) document.get("name")).getValue();
                if (!collectionName.contains("$")) {
                    return collectionName.substring(databaseName.length() + 1);
                }
                return null;
            }
        };
    }

    private MongoNamespace getNamespace() {
        return new MongoNamespace(databaseName, "system.namespaces");
    }

    private QueryProtocol<BsonDocument> getProtocol() {
        return new QueryProtocol<BsonDocument>(getNamespace(), EnumSet.noneOf(QueryFlag.class), 0, 0, new BsonDocument(), null,
                                               new BsonDocumentCodec());
    }

}
