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

package com.mongodb.operation;

import com.mongodb.Function;
import com.mongodb.MongoNamespace;
import com.mongodb.async.MongoFuture;
import com.mongodb.binding.AsyncWriteBinding;
import com.mongodb.binding.WriteBinding;
import org.bson.BsonDocument;

import static com.mongodb.assertions.Assertions.notNull;
import static com.mongodb.operation.CommandDocuments.createMapReduce;
import static com.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocol;
import static com.mongodb.operation.CommandOperationHelper.executeWrappedCommandProtocolAsync;

/**
 * <p>Operation that runs a Map Reduce against a MongoDB instance.  This operation does not support "inline" results, i.e. the results will
 * be output into the collection represented by the MongoNamespace provided.</p>
 *
 * <p>To run a map reduce operation and receive the results inline (i.e. as a response to running the command) use {@code
 * MapReduceWithInlineResultsOperation}.</p>
 *
 * @mongodb.driver.manual core/map-reduce Map-Reduce
 * @since 3.0
 */
public class MapReduceToCollectionOperation implements AsyncWriteOperation<MapReduceStatistics>, WriteOperation<MapReduceStatistics> {
    private final MongoNamespace namespace;
    private final MapReduce mapReduce;

    /**
     * Construct a MapReduceOperation with all the criteria it needs to execute
     *
     * @param namespace the database and collection namespace for the operation.
     * @param mapReduce the bean containing all the details of the Map Reduce operation to perform.
     */
    public MapReduceToCollectionOperation(final MongoNamespace namespace, final MapReduce mapReduce) {
        this.namespace = notNull("namespace", namespace);
        this.mapReduce = notNull("mapReduce", mapReduce);

        if (mapReduce.isInline()) {
            throw new IllegalArgumentException("This operation can only be used with map reduce operations that put the results into a "
                                               + "collection.  Invalid MapReduce: " + mapReduce);
        }
    }

    /**
     * Executing this will return a cursor with your results in.
     *
     * @param binding the binding
     * @return a MongoCursor that can be iterated over to find all the results of the Map Reduce operation.
     */
    @Override
    public MapReduceStatistics execute(final WriteBinding binding) {
        return executeWrappedCommandProtocol(namespace.getDatabaseName(), getCommand(), binding, transformer());
    }

    @Override
    public MongoFuture<MapReduceStatistics> executeAsync(final AsyncWriteBinding binding) {
        return executeWrappedCommandProtocolAsync(namespace.getDatabaseName(), getCommand(), binding, transformer());
    }

    private Function<BsonDocument, MapReduceStatistics> transformer() {
        return new Function<BsonDocument, MapReduceStatistics>() {
            @SuppressWarnings("unchecked")
            @Override
            public MapReduceStatistics apply(final BsonDocument result) {
                return MapReduceHelper.createStatistics(result);
            }
        };
    }

    private BsonDocument getCommand() {
        return createMapReduce(namespace.getCollectionName(), mapReduce);
    }
}
