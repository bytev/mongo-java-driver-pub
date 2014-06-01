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

import org.bson.codecs.Decoder;
import org.bson.types.BsonDocument;
import org.mongodb.CommandResult;
import org.mongodb.MongoFuture;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;

import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;

/**
 * An operation that executes an arbitrary command that writes to the server.
 *
 * @since 3.0
 */
public class CommandWriteOperation implements AsyncWriteOperation<CommandResult>, WriteOperation<CommandResult> {
    private final Decoder<BsonDocument> commandDecoder;
    private final String database;
    private final BsonDocument commandDocument;

    public CommandWriteOperation(final String database, final BsonDocument command, final Decoder<BsonDocument> commandDecoder) {
        this.database = database;
        this.commandDecoder = commandDecoder;
        this.commandDocument = command;
    }

    @Override
    public CommandResult execute(final WriteBinding binding) {
        return executeWrappedCommandProtocol(database, commandDocument, commandDecoder, binding);
    }

    @Override
    public MongoFuture<CommandResult> executeAsync(final AsyncWriteBinding binding) {
        return executeWrappedCommandProtocolAsync(database, commandDocument, commandDecoder, binding);
    }
}
