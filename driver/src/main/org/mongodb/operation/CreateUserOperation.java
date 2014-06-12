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
import org.mongodb.CommandResult;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.WriteConcern;
import org.mongodb.WriteResult;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.connection.Connection;
import org.mongodb.protocol.InsertProtocol;
import org.mongodb.protocol.Protocol;

import static java.util.Arrays.asList;
import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.OperationHelper.AsyncCallableWithConnection;
import static org.mongodb.operation.OperationHelper.CallableWithConnection;
import static org.mongodb.operation.OperationHelper.VoidTransformer;
import static org.mongodb.operation.OperationHelper.executeProtocolAsync;
import static org.mongodb.operation.OperationHelper.getBsonDocumentCodec;
import static org.mongodb.operation.OperationHelper.serverIsAtLeastVersionTwoDotSix;
import static org.mongodb.operation.OperationHelper.withConnection;
import static org.mongodb.operation.UserOperationHelper.asCollectionDocument;
import static org.mongodb.operation.UserOperationHelper.asCommandDocument;

/**
 * An operation to create a user.
 *
 * @since 3.0
 */
public class CreateUserOperation implements AsyncWriteOperation<Void>, WriteOperation<Void> {
    private final User user;

    public CreateUserOperation(final User user) {
        this.user = notNull("user", user);
    }

    @Override
    public Void execute(final WriteBinding binding) {
        return withConnection(binding, new CallableWithConnection<Void>() {
            @Override
            public Void call(final Connection connection) {
                if (serverIsAtLeastVersionTwoDotSix(connection)) {
                    executeWrappedCommandProtocol(user.getCredential().getSource(), getCommand(), connection);
                } else {
                    getCollectionBasedProtocol().execute(connection);
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
                if (serverIsAtLeastVersionTwoDotSix(connection)) {
                    return executeWrappedCommandProtocolAsync(user.getCredential().getSource(), getCommand(), connection,
                                                              new VoidTransformer<CommandResult>());
                } else {
                    return executeProtocolAsync(getCollectionBasedProtocol(), connection, new VoidTransformer<WriteResult>());
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Protocol<WriteResult> getCollectionBasedProtocol() {
        MongoNamespace namespace = new MongoNamespace(user.getCredential().getSource(), "system.users");
        return new InsertProtocol<BsonDocument>(namespace, true, WriteConcern.ACKNOWLEDGED,
                                                asList(new InsertRequest<BsonDocument>(asCollectionDocument(user))),
                                                getBsonDocumentCodec());
    }

    private BsonDocument getCommand() {
        return asCommandDocument(user, "createUser");
    }
}
