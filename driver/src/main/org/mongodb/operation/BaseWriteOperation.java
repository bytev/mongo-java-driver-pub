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

import org.bson.types.BsonBoolean;
import org.bson.types.BsonDocument;
import org.bson.types.BsonInt32;
import org.bson.types.BsonString;
import org.mongodb.BulkWriteError;
import org.mongodb.BulkWriteException;
import org.mongodb.BulkWriteResult;
import org.mongodb.CommandResult;
import org.mongodb.MongoDuplicateKeyException;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.MongoWriteException;
import org.mongodb.WriteConcern;
import org.mongodb.WriteResult;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.connection.Connection;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.protocol.AcknowledgedWriteResult;
import org.mongodb.protocol.WriteCommandProtocol;
import org.mongodb.protocol.WriteProtocol;

import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.OperationHelper.AsyncCallableWithConnection;
import static org.mongodb.operation.OperationHelper.CallableWithConnection;
import static org.mongodb.operation.OperationHelper.DUPLICATE_KEY_ERROR_CODES;
import static org.mongodb.operation.OperationHelper.serverIsAtLeastVersionTwoDotSix;
import static org.mongodb.operation.OperationHelper.withConnection;
import static org.mongodb.operation.WriteRequest.Type.INSERT;
import static org.mongodb.operation.WriteRequest.Type.REMOVE;
import static org.mongodb.operation.WriteRequest.Type.REPLACE;
import static org.mongodb.operation.WriteRequest.Type.UPDATE;


public abstract class BaseWriteOperation implements AsyncWriteOperation<WriteResult>, WriteOperation<WriteResult> {

    private final WriteConcern writeConcern;
    private final MongoNamespace namespace;
    private final boolean ordered;

    public BaseWriteOperation(final MongoNamespace namespace, final boolean ordered, final WriteConcern writeConcern) {
        this.ordered = ordered;
        this.namespace = notNull("namespace", namespace);
        this.writeConcern = notNull("writeConcern", writeConcern);
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    public boolean isOrdered() {
        return ordered;
    }

    @Override
    public WriteResult execute(final WriteBinding binding) {
        return withConnection(binding, new CallableWithConnection<WriteResult>() {
            @Override
            public WriteResult call(final Connection connection) {
                try {
                    if (writeConcern.isAcknowledged() && serverIsAtLeastVersionTwoDotSix(connection)) {
                        return translateBulkWriteResult(getCommandProtocol().execute(connection));
                    } else {
                        return getWriteProtocol().execute(connection);
                    }
                } catch (BulkWriteException e) {
                    throw convertBulkWriteException(e);
                }
            }
        });
    }

    @Override
    public MongoFuture<WriteResult> executeAsync(final AsyncWriteBinding binding) {
        return withConnection(binding, new AsyncCallableWithConnection<WriteResult>() {

            @Override
            public MongoFuture<WriteResult> call(final Connection connection) {
                final SingleResultFuture<WriteResult> future = new SingleResultFuture<WriteResult>();
                if (writeConcern.isAcknowledged() && serverIsAtLeastVersionTwoDotSix(connection)) {
                    getCommandProtocol().executeAsync(connection)
                                        .register(new SingleResultCallback<BulkWriteResult>() {
                                            @Override
                                            public void onResult(final BulkWriteResult result, final MongoException e) {
                                                if (e != null) {
                                                    future.init(null, translateException(e));
                                                } else {
                                                    future.init(translateBulkWriteResult(result), null);
                                                }
                                            }
                                        });
                } else {
                    getWriteProtocol().executeAsync(connection).register(new SingleResultCallback<WriteResult>() {
                        @Override
                        public void onResult(final WriteResult result, final MongoException e) {
                            if (e != null) {
                                future.init(null, translateException(e));
                            } else {
                                future.init(result, null);
                            }
                        }
                    });
                }
                return future;
            }
        });
    }

    public MongoNamespace getNamespace() {
        return namespace;
    }

    protected abstract WriteProtocol getWriteProtocol();

    protected abstract WriteCommandProtocol getCommandProtocol();

    private MongoException translateException(final MongoException e) {
        MongoException checkedError = e;
        if (e instanceof BulkWriteException) {
            checkedError = convertBulkWriteException((BulkWriteException) e);
        }
        return checkedError;
    }

    private MongoWriteException convertBulkWriteException(final BulkWriteException e) {
        BulkWriteError lastError = getLastError(e);
        if (lastError != null) {
            if (DUPLICATE_KEY_ERROR_CODES.contains(lastError.getCode())) {
                return new MongoDuplicateKeyException(lastError.getCode(), lastError.getMessage(), manufactureGetLastErrorCommandResult(e));
            } else {
                return new MongoWriteException(lastError.getCode(), lastError.getMessage(), manufactureGetLastErrorCommandResult(e));
            }
        } else {
            return new MongoWriteException(e.getWriteConcernError().getCode(), e.getWriteConcernError().getMessage(),
                                           manufactureGetLastErrorCommandResult(e));
        }

    }

    private CommandResult manufactureGetLastErrorCommandResult(final BulkWriteException e) {
        BsonDocument response = new BsonDocument();
        addBulkWriteResultToResponse(e.getWriteResult(), response);
        if (e.getWriteConcernError() != null) {
            response.putAll(e.getWriteConcernError().getDetails());
        }
        if (getLastError(e) != null) {
            response.put("err", new BsonString(getLastError(e).getMessage()));
            response.put("code", new BsonInt32(getLastError(e).getCode()));
            response.putAll(getLastError(e).getDetails());

        } else if (e.getWriteConcernError() != null) {
            response.put("err", new BsonString(e.getWriteConcernError().getMessage()));
            response.put("code", new BsonInt32(e.getWriteConcernError().getCode()));
        }
        return new CommandResult(e.getServerAddress(), response, 0);
    }

    private void addBulkWriteResultToResponse(final BulkWriteResult bulkWriteResult, final BsonDocument response) {
        response.put("ok", new BsonInt32(1));
        if (getType() == INSERT) {
            response.put("n", new BsonInt32(0));
        } else if (getType() == REMOVE) {
            response.put("n", new BsonInt32(bulkWriteResult.getRemovedCount()));
        } else if (getType() == UPDATE || getType() == REPLACE) {
            response.put("n", new BsonInt32(bulkWriteResult.getMatchedCount() + bulkWriteResult.getUpserts().size()));
            if (bulkWriteResult.getUpserts().isEmpty()) {
                response.put("updatedExisting", BsonBoolean.TRUE);
            } else {
                response.put("updatedExisting", BsonBoolean.FALSE);
                response.put("upserted", bulkWriteResult.getUpserts().get(0).getId());
            }
        }
    }

    private WriteResult translateBulkWriteResult(final BulkWriteResult bulkWriteResult) {
        return new AcknowledgedWriteResult(getCount(bulkWriteResult), getUpdatedExisting(bulkWriteResult),
                                           bulkWriteResult.getUpserts().isEmpty()
                                           ? null : bulkWriteResult.getUpserts().get(0).getId()
        );
    }

    protected abstract WriteRequest.Type getType();

    protected abstract int getCount(final BulkWriteResult bulkWriteResult);

    protected boolean getUpdatedExisting(final BulkWriteResult bulkWriteResult) {
        return false;
    }

    private BulkWriteError getLastError(final BulkWriteException e) {
        return e.getWriteErrors().isEmpty() ? null : e.getWriteErrors().get(e.getWriteErrors().size() - 1);

    }
}
