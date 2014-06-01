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

import org.bson.types.BsonArray;
import org.bson.types.BsonDocument;
import org.bson.types.BsonInt32;
import org.bson.types.BsonString;
import org.mongodb.CommandResult;
import org.mongodb.Index;
import org.mongodb.MongoCommandFailureException;
import org.mongodb.MongoDuplicateKeyException;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.MongoServerException;
import org.mongodb.WriteConcern;
import org.mongodb.WriteResult;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.connection.Connection;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.protocol.InsertProtocol;

import java.util.List;

import static java.util.Arrays.asList;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.DocumentHelper.putIfTrue;
import static org.mongodb.operation.OperationHelper.AsyncCallableWithConnection;
import static org.mongodb.operation.OperationHelper.CallableWithConnection;
import static org.mongodb.operation.OperationHelper.DUPLICATE_KEY_ERROR_CODES;
import static org.mongodb.operation.OperationHelper.getBsonDocumentCodec;
import static org.mongodb.operation.OperationHelper.serverIsAtLeastVersionTwoDotSix;
import static org.mongodb.operation.OperationHelper.withConnection;

/**
 * An operation that creates one or more indexes.
 *
 * @since 3.0
 */
public class CreateIndexesOperation implements AsyncWriteOperation<Void>, WriteOperation<Void> {
    private final List<Index> indexes;
    private final MongoNamespace namespace;
    private final MongoNamespace systemIndexes;

    public CreateIndexesOperation(final List<Index> indexes, final MongoNamespace namespace) {
        this.indexes = indexes;
        this.namespace = namespace;
        this.systemIndexes = new MongoNamespace(namespace.getDatabaseName(), "system.indexes");
    }

    @Override
    public Void execute(final WriteBinding binding) {
        return withConnection(binding, new CallableWithConnection<Void>() {
            @Override
            public Void call(final Connection connection) {
                if (serverIsAtLeastVersionTwoDotSix(connection)) {
                    try {
                        executeWrappedCommandProtocol(namespace.getDatabaseName(), getCommand(), connection);
                    } catch (MongoCommandFailureException e) {
                        throw checkForDuplicateKeyError(e);
                    }
                } else {
                    for (Index index : indexes) {
                        asInsertProtocol(index).execute(connection);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public MongoFuture<Void> executeAsync(final AsyncWriteBinding binding) {
        return withConnection(binding, new AsyncCallableWithConnection<Void>() {
            @Override
            public MongoFuture<Void> call(final Connection connection) {
                final SingleResultFuture<Void> future = new SingleResultFuture<Void>();
                if (serverIsAtLeastVersionTwoDotSix(connection)) {
                    executeWrappedCommandProtocolAsync(namespace, getCommand(), connection)
                    .register(new SingleResultCallback<CommandResult>() {
                        @Override
                        public void onResult(final CommandResult result, final MongoException e) {
                            future.init(null, translateException(e));
                        }
                    });
                } else {
                    executeInsertProtocolAsync(indexes, connection, future);
                }
                return future;
            }
        });
    }

    private void executeInsertProtocolAsync(final List<Index> indexesRemaining, final Connection connection,
                                            final SingleResultFuture<Void> future) {
        Index index = indexesRemaining.remove(0);
        asInsertProtocol(index).executeAsync(connection)
                               .register(new SingleResultCallback<WriteResult>() {
                                   @Override
                                   public void onResult(final WriteResult result, final MongoException e) {
                                       MongoException translatedException = translateException(e);
                                       if (translatedException != null) {
                                           future.init(null, translatedException);
                                       } else if (indexesRemaining.isEmpty()) {
                                           future.init(null, null);
                                       } else {
                                           executeInsertProtocolAsync(indexesRemaining, connection, future);
                                       }
                                   }
                               });
    }

    private BsonDocument getCommand() {
        BsonDocument command = new BsonDocument("createIndexes", new BsonString(namespace.getCollectionName()));
        BsonArray array = new BsonArray();
        for (Index index : indexes) {
            array.add(toDocument(index));
        }
        command.put("indexes", array);

        return command;
    }

    @SuppressWarnings("unchecked")
    private InsertProtocol<BsonDocument> asInsertProtocol(final Index index) {
        return new InsertProtocol<BsonDocument>(systemIndexes, true, WriteConcern.ACKNOWLEDGED,
                                                asList(new InsertRequest<BsonDocument>(toDocument(index))),
                                                getBsonDocumentCodec());
    }

    private BsonDocument toDocument(final Index index) {
        BsonDocument indexDetails = new BsonDocument();
        indexDetails.append("name", new BsonString(index.getName()));
        indexDetails.append("key", index.getKeys());
        putIfTrue(indexDetails, "unique", index.isUnique());
        putIfTrue(indexDetails, "sparse", index.isSparse());
        putIfTrue(indexDetails, "dropDups", index.isDropDups());
        putIfTrue(indexDetails, "background", index.isBackground());
        if (index.getExpireAfterSeconds() != -1) {
            indexDetails.append("expireAfterSeconds", new BsonInt32(index.getExpireAfterSeconds()));
        }
        indexDetails.putAll(index.getExtra());
        indexDetails.put("ns", new BsonString(namespace.getFullName()));

        return indexDetails;
    }

    private MongoException translateException(final MongoException e) {
        return (e instanceof MongoCommandFailureException) ? checkForDuplicateKeyError((MongoCommandFailureException) e) : e;
    }

    private MongoServerException checkForDuplicateKeyError(final MongoCommandFailureException e) {
        if (DUPLICATE_KEY_ERROR_CODES.contains(e.getErrorCode())) {
            return new MongoDuplicateKeyException(e.getErrorCode(), e.getErrorMessage(), e.getCommandResult());
        } else {
            return e;
        }
    }
}
