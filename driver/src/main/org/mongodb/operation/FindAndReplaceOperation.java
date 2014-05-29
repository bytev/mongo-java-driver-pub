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

import org.bson.codecs.Codec;
import org.bson.types.BsonDocument;
import org.bson.types.BsonDocumentWrapper;
import org.bson.types.BsonString;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.DocumentHelper.putIfNotNull;
import static org.mongodb.operation.DocumentHelper.putIfNotZero;
import static org.mongodb.operation.DocumentHelper.putIfTrue;

/**
 * An operation that atomically finds and removes a single document.
 *
 * @param <T> the document type
 * @since 3.0
 */
public class FindAndReplaceOperation<T> implements AsyncWriteOperation<T>, WriteOperation<T> {
    private final MongoNamespace namespace;
    private final FindAndReplace<T> findAndReplace;
    private final Codec<T> codec;

    public FindAndReplaceOperation(final MongoNamespace namespace, final FindAndReplace<T> findAndReplace, final Codec<T> codec) {
        this.namespace = namespace;
        this.findAndReplace = findAndReplace;
        this.codec = codec;
    }

    @Override
    public T execute(final WriteBinding binding) {
        return executeWrappedCommandProtocol(namespace, getCommand(), CommandResultDocumentCodec.create(codec, "value"),
                                             binding, FindAndModifyHelper.<T>transformer());
    }

    @Override
    public MongoFuture<T> executeAsync(final AsyncWriteBinding binding) {
        return executeWrappedCommandProtocolAsync(namespace, getCommand(), CommandResultDocumentCodec.create(codec, "value"),
                                                  binding, FindAndModifyHelper.<T>transformer());
    }

    private BsonDocument getCommand() {
        BsonDocument command = new BsonDocument("findandmodify", new BsonString(namespace.getCollectionName()));
        putIfNotNull(command, "query", findAndReplace.getFilter());
        putIfNotNull(command, "fields", findAndReplace.getSelector());
        putIfNotNull(command, "sort", findAndReplace.getSortCriteria());
        putIfTrue(command, "new", findAndReplace.isReturnNew());
        putIfTrue(command, "upsert", findAndReplace.isUpsert());
        putIfNotZero(command, "maxTimeMS", findAndReplace.getOptions().getMaxTime(MILLISECONDS));

        command.put("update", new BsonDocumentWrapper<T>(findAndReplace.getReplacement(), codec));
        return command;
    }
}
