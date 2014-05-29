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

package org.mongodb.protocol;

import org.bson.codecs.Encoder;
import org.mongodb.BulkWriteResult;
import org.mongodb.Document;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.WriteConcern;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.codecs.validators.QueryFieldNameValidator;
import org.mongodb.connection.Connection;
import org.mongodb.connection.ServerDescription;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.diagnostics.Loggers;
import org.mongodb.operation.SingleResultFuture;
import org.mongodb.operation.UpdateRequest;
import org.mongodb.operation.WriteRequest;
import org.mongodb.protocol.message.UpdateCommandMessage;

import java.util.List;

import static java.lang.String.format;
import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.protocol.ProtocolHelper.getMessageSettings;

public class UpdateCommandProtocol extends WriteCommandProtocol {

    private static final org.mongodb.diagnostics.logging.Logger LOGGER = Loggers.getLogger("protocol.update");

    private final List<UpdateRequest> updates;
    private final Encoder<Document> queryEncoder;

    public UpdateCommandProtocol(final MongoNamespace namespace, final boolean ordered, final WriteConcern writeConcern,
                                 final List<UpdateRequest> updates, final Encoder<Document> queryEncoder) {
        super(namespace, ordered, writeConcern);
        this.updates = notNull("update", updates);
        this.queryEncoder = notNull("queryEncoder", queryEncoder);
    }

    @Override
    public BulkWriteResult execute(final Connection connection) {
        LOGGER.debug(format("Updating documents in namespace %s on connection [%s] to server %s", getNamespace(), connection.getId(),
                            connection.getServerAddress()));
        BulkWriteResult writeResult = super.execute(connection);
        LOGGER.debug("Update completed");
        return writeResult;
    }

    @Override
    public MongoFuture<BulkWriteResult> executeAsync(final Connection connection) {
        LOGGER.debug(format("Asynchronously updating documents in namespace %s on connection [%s] to server %s", getNamespace(),
                            connection.getId(),
                            connection.getServerAddress()));
        final SingleResultFuture<BulkWriteResult> future = new SingleResultFuture<BulkWriteResult>();
        super.executeAsync(connection).register(new SingleResultCallback<BulkWriteResult>() {
            @Override
            public void onResult(final BulkWriteResult result, final MongoException e) {
                if (e != null) {
                    LOGGER.debug("Asynchronous update completed");
                }
                future.init(result, e);
            }
        });
        return future;
    }

    @Override
    protected WriteRequest.Type getType() {
        return WriteRequest.Type.UPDATE;
    }

    @Override
    protected UpdateCommandMessage createRequestMessage(final ServerDescription serverDescription) {
        return new UpdateCommandMessage(getNamespace(), isOrdered(), getWriteConcern(), updates,
                                        new DocumentCodec(new QueryFieldNameValidator()), getMessageSettings(serverDescription));
    }

    @Override
    protected org.mongodb.diagnostics.logging.Logger getLogger() {
        return LOGGER;
    }

}
