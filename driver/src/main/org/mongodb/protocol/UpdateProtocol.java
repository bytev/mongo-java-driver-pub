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

import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.WriteConcern;
import org.mongodb.WriteResult;
import org.mongodb.connection.Connection;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.diagnostics.Loggers;
import org.mongodb.diagnostics.logging.Logger;
import org.mongodb.operation.SingleResultFuture;
import org.mongodb.operation.UpdateRequest;
import org.mongodb.protocol.message.MessageSettings;
import org.mongodb.protocol.message.RequestMessage;
import org.mongodb.protocol.message.UpdateMessage;

import java.util.List;

import static java.lang.String.format;

public class UpdateProtocol extends WriteProtocol {
    private static final org.mongodb.diagnostics.logging.Logger LOGGER = Loggers.getLogger("protocol.update");

    private final List<UpdateRequest> updates;

    public UpdateProtocol(final MongoNamespace namespace, final boolean ordered, final WriteConcern writeConcern,
                          final List<UpdateRequest> updates) {
        super(namespace, ordered, writeConcern);
        this.updates = updates;
    }

    @Override
    public WriteResult execute(final Connection connection) {
        LOGGER.debug(format("Updating documents in namespace %s on connection [%s] to server %s", getNamespace(), connection.getId(),
                            connection.getServerAddress()));
        WriteResult writeResult = super.execute(connection);
        LOGGER.debug("Update completed");
        return writeResult;
    }

    @Override
    public MongoFuture<WriteResult> executeAsync(final Connection connection) {
        LOGGER.debug(format("Asynchronously updating documents in namespace %s on connection [%s] to server %s", getNamespace(),
                            connection.getId(), connection.getServerAddress()));
        final SingleResultFuture<WriteResult> future = new SingleResultFuture<WriteResult>();
        super.executeAsync(connection).register(new SingleResultCallback<WriteResult>() {
            @Override
            public void onResult(final WriteResult result, final MongoException e) {
                if (e == null) {
                    LOGGER.debug("Asynchronous updated completed");
                }
                future.init(result, e);
            }
        });
        return future;
    }

    @Override
    protected RequestMessage createRequestMessage(final MessageSettings settings) {
        return new UpdateMessage(getNamespace().getFullName(), updates, settings);
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
