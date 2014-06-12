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

import org.bson.types.BsonDocument;
import org.bson.types.BsonString;
import org.mongodb.MongoCommandFailureException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;

import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.CommandOperationHelper.ignoreNameSpaceErrors;
import static org.mongodb.operation.OperationHelper.ignoreResult;

/**
 * An operation that drops an index.
 *
 * @since 3.0
 */
public class DropIndexOperation implements AsyncWriteOperation<Void>, WriteOperation<Void> {
    private final MongoNamespace namespace;
    private final String indexName;

    public DropIndexOperation(final MongoNamespace namespace, final String indexName) {
        this.namespace = namespace;
        this.indexName = indexName;
    }

    @Override
    public Void execute(final WriteBinding binding) {
        try {
            executeWrappedCommandProtocol(namespace.getDatabaseName(), getCommand(), binding);
        } catch (MongoCommandFailureException e) {
            ignoreNameSpaceErrors(e);
        }
        return null;
    }

    @Override
    public MongoFuture<Void> executeAsync(final AsyncWriteBinding binding) {
        return ignoreResult(ignoreNameSpaceErrors(executeWrappedCommandProtocolAsync(namespace.getDatabaseName(), getCommand(), binding)));
    }

    private BsonDocument getCommand() {
        return new BsonDocument("dropIndexes", new BsonString(namespace.getCollectionName())).append("index", new BsonString(indexName));
    }
}
