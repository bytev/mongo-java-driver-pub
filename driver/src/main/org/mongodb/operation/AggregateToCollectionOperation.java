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

import org.bson.codecs.Encoder;
import org.mongodb.AggregationOptions;
import org.mongodb.CommandResult;
import org.mongodb.Document;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.codecs.DocumentCodec;

import java.util.List;

import static org.mongodb.assertions.Assertions.isTrueArgument;
import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.AggregateHelper.asCommandDocument;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.OperationHelper.VoidTransformer;

/**
 * An operation that executes an aggregation that writes its results to a collection (which is what makes this a write operation rather
 * than a read operation).
 *
 * @since 3.0
 */
public class AggregateToCollectionOperation implements AsyncWriteOperation<Void>, WriteOperation<Void> {
    private final MongoNamespace namespace;
    private final List<Document> pipeline;
    private final Encoder<Document> encoder;
    private final AggregationOptions options;

    /**
     * Construct a new instance
     * @param namespace the namespace to aggregate from
     * @param pipeline the aggregate pipeline
     * @param options the aggregation options
     */
    public AggregateToCollectionOperation(final MongoNamespace namespace, final List<Document> pipeline, final Encoder<Document> encoder,
                                           final AggregationOptions options) {
        this.namespace = notNull("namespace", namespace);
        this.pipeline = notNull("pipeline", pipeline);
        this.encoder = notNull("encoder", encoder);
        this.options = notNull("options", options);

        isTrueArgument("pipeline is empty", !pipeline.isEmpty());
        isTrueArgument("last stage of pipeline does not contain an output collection",
                       pipeline.get(pipeline.size() - 1).getString("$out") != null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Void execute(final WriteBinding binding) {
        executeWrappedCommandProtocol(namespace, asCommandDocument(namespace, pipeline, options),
                                      encoder, new DocumentCodec(), binding, new VoidTransformer<CommandResult>());

        return null;
    }

    @Override
    public MongoFuture<Void> executeAsync(final AsyncWriteBinding binding) {
        return executeWrappedCommandProtocolAsync(namespace, asCommandDocument(namespace, pipeline, options),
                                                  encoder, new DocumentCodec(), binding, new VoidTransformer<CommandResult>());
    }
}
