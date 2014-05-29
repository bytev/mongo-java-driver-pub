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
import org.mongodb.Document;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.codecs.PrimitiveCodecs;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.DocumentHelper.putIfNotNull;
import static org.mongodb.operation.DocumentHelper.putIfNotZero;
import static org.mongodb.operation.DocumentHelper.putIfTrue;

/**
 * An operation that atomically finds and updates a single document.
 *
 * @param <T> The document type
 *
 *  @since 3.0
 */
public class FindAndUpdateOperation<T> implements AsyncWriteOperation<T>, WriteOperation<T> {
    private final MongoNamespace namespace;
    private final FindAndUpdate findAndUpdate;
    private final CommandResultWithPayloadDecoder<T> resultDecoder;
    private final DocumentCodec commandEncoder = new DocumentCodec(PrimitiveCodecs.createDefault());

    public FindAndUpdateOperation(final MongoNamespace namespace, final FindAndUpdate findAndUpdate, final Decoder<T> resultDecoder) {
        this.namespace = namespace;
        this.findAndUpdate = findAndUpdate;
        this.resultDecoder = new CommandResultWithPayloadDecoder<T>(resultDecoder, "value");
    }

    @Override
    public T execute(final WriteBinding binding) {
        validateUpdateDocumentToEnsureItHasUpdateOperators(findAndUpdate.getUpdateOperations());
        return executeWrappedCommandProtocol(namespace, getCommand(), commandEncoder, resultDecoder, binding,
                                             FindAndModifyHelper.<T>transformer());
    }

    @Override
    public MongoFuture<T> executeAsync(final AsyncWriteBinding binding) {
        validateUpdateDocumentToEnsureItHasUpdateOperators(findAndUpdate.getUpdateOperations());
        return executeWrappedCommandProtocolAsync(namespace, getCommand(), commandEncoder, resultDecoder, binding,
                                                  FindAndModifyHelper.<T>transformer());
    }

    private void validateUpdateDocumentToEnsureItHasUpdateOperators(final Document value) {
        for (final String field : value.keySet()) {
            if (field.startsWith("$")) {
                return;
            }
        }
        throw new IllegalArgumentException(format("Find and update requires an update operator (beginning with '$') in the update "
                                                  + "Document: %s", value));
    }

    private Document getCommand() {
        Document command = new Document("findandmodify", namespace.getCollectionName());
        putIfNotNull(command, "query", findAndUpdate.getFilter());
        putIfNotNull(command, "fields", findAndUpdate.getSelector());
        putIfNotNull(command, "sort", findAndUpdate.getSortCriteria());
        putIfTrue(command, "new", findAndUpdate.isReturnNew());
        putIfTrue(command, "upsert", findAndUpdate.isUpsert());
        putIfNotZero(command, "maxTimeMS", findAndUpdate.getOptions().getMaxTime(MILLISECONDS));

        command.put("update", findAndUpdate.getUpdateOperations());
        return command;
    }

}
