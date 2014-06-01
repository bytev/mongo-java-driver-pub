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
import org.bson.types.BsonDocument;
import org.bson.types.BsonString;
import org.bson.types.BsonValue;
import org.mongodb.BulkWriteError;
import org.mongodb.BulkWriteException;
import org.mongodb.BulkWriteResult;
import org.mongodb.BulkWriteUpsert;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.MongoWriteException;
import org.mongodb.WriteConcern;
import org.mongodb.WriteConcernError;
import org.mongodb.WriteResult;
import org.mongodb.binding.AsyncWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.codecs.CollectibleCodec;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.connection.Connection;
import org.mongodb.connection.ServerDescription;
import org.mongodb.connection.ServerVersion;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.protocol.AcknowledgedBulkWriteResult;
import org.mongodb.protocol.BulkWriteBatchCombiner;
import org.mongodb.protocol.DeleteCommandProtocol;
import org.mongodb.protocol.DeleteProtocol;
import org.mongodb.protocol.IndexMap;
import org.mongodb.protocol.InsertCommandProtocol;
import org.mongodb.protocol.InsertProtocol;
import org.mongodb.protocol.ReplaceCommandProtocol;
import org.mongodb.protocol.ReplaceProtocol;
import org.mongodb.protocol.UpdateCommandProtocol;
import org.mongodb.protocol.UpdateProtocol;
import org.mongodb.protocol.WriteCommandProtocol;
import org.mongodb.protocol.WriteProtocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.mongodb.assertions.Assertions.isTrue;
import static org.mongodb.assertions.Assertions.isTrueArgument;
import static org.mongodb.assertions.Assertions.notNull;
import static org.mongodb.operation.OperationHelper.AsyncCallableWithConnection;
import static org.mongodb.operation.OperationHelper.CallableWithConnection;
import static org.mongodb.operation.OperationHelper.withConnection;
import static org.mongodb.operation.WriteRequest.Type.INSERT;
import static org.mongodb.operation.WriteRequest.Type.REMOVE;
import static org.mongodb.operation.WriteRequest.Type.REPLACE;
import static org.mongodb.operation.WriteRequest.Type.UPDATE;

/**
 * An operation to execute a series of write operations in bulk.
 *
 * @param <T> The type of document stored in the given namespace
 * @since 3.0
 */
public class MixedBulkWriteOperation<T> implements AsyncWriteOperation<BulkWriteResult>, WriteOperation<BulkWriteResult> {
    private final MongoNamespace namespace;
    private final List<WriteRequest> writeRequests;
    private final WriteConcern writeConcern;
    private final Encoder<T> encoder;
    private final boolean ordered;
    private boolean closed;

    /**
     * Construct a new instance.
     *
     * @param namespace     the namespace to write to
     * @param writeRequests the list of runWrites to execute
     * @param ordered       whether the runWrites must be executed in order.
     * @param encoder       the encoder
     */
    public MixedBulkWriteOperation(final MongoNamespace namespace, final List<WriteRequest> writeRequests, final boolean ordered,
                                   final WriteConcern writeConcern, final Encoder<T> encoder) {
        this.ordered = ordered;
        this.namespace = notNull("namespace", namespace);
        this.writeRequests = notNull("writes", writeRequests);
        this.writeConcern = notNull("writeConcern", writeConcern);
        this.encoder = notNull("encoder", encoder);
        isTrueArgument("writes is not an empty list", !writeRequests.isEmpty());
    }

    /**
     * Executes a bulk write operation.
     *
     * @param binding the WriteBinding        for the operation
     * @return the bulk write result.
     * @throws org.mongodb.BulkWriteException if a failure to complete the bulk write is detected based on the response from the server
     * @throws org.mongodb.MongoException     for general failures
     */
    @Override
    public BulkWriteResult execute(final WriteBinding binding) {
        isTrue("already executed", !closed);

        closed = true;
        return withConnection(binding, new CallableWithConnection<BulkWriteResult>() {
            @Override
            public BulkWriteResult call(final Connection connection) {
                BulkWriteBatchCombiner bulkWriteBatchCombiner = new BulkWriteBatchCombiner(connection.getServerDescription().getAddress(),
                                                                                           ordered, writeConcern);
                for (Run run : getRunGenerator(connection.getServerDescription())) {
                    try {
                        BulkWriteResult result = run.execute(connection);
                        if (result.isAcknowledged()) {
                            bulkWriteBatchCombiner.addResult(result, run.indexMap);
                        }
                    } catch (BulkWriteException e) {
                        bulkWriteBatchCombiner.addErrorResult(e, run.indexMap);
                        if (bulkWriteBatchCombiner.shouldStopSendingMoreBatches()) {
                            break;
                        }
                    }
                }
                return bulkWriteBatchCombiner.getResult();
            }
        });
    }

    /**
     * Executes a bulk write operation asynchronously.
     *
     * @param binding the AsyncWriteBinding   for the operation
     * @return the future bulk write result.
     * @throws org.mongodb.BulkWriteException if a failure to complete the bulk write is detected based on the response from the server
     * @throws org.mongodb.MongoException     for general failures
     */
    @Override
    public MongoFuture<BulkWriteResult> executeAsync(final AsyncWriteBinding binding) {
        isTrue("already executed", !closed);

        closed = true;
        final SingleResultFuture<BulkWriteResult> future = new SingleResultFuture<BulkWriteResult>();
        return withConnection(binding, new AsyncCallableWithConnection<BulkWriteResult>() {

            @Override
            public MongoFuture<BulkWriteResult> call(final Connection connection) {
                final BulkWriteBatchCombiner bulkWriteBatchCombiner = new BulkWriteBatchCombiner(connection.getServerDescription()
                                                                                                           .getAddress(),
                                                                                                 ordered,
                                                                                                 writeConcern
                );
                Iterator<Run> runs = getRunGenerator(connection.getServerDescription()).iterator();
                executeRunsAsync(runs, connection, bulkWriteBatchCombiner, future);
                return future;
            }
        });
    }

    private void executeRunsAsync(final Iterator<Run> runs, final Connection connection,
                                  final BulkWriteBatchCombiner bulkWriteBatchCombiner,
                                  final SingleResultFuture<BulkWriteResult> future) {

        final Run run = runs.next();
        run.executeAsync(connection)
           .register(new SingleResultCallback<BulkWriteResult>() {
               @Override
               public void onResult(final BulkWriteResult result, final MongoException e) {
                   if (e != null) {
                       if (e instanceof BulkWriteException) {
                           bulkWriteBatchCombiner.addErrorResult((BulkWriteException) e, run.indexMap);
                       } else {
                           future.init(null, e);
                           return;
                       }
                   } else if (result.isAcknowledged()) {
                       bulkWriteBatchCombiner.addResult(result, run.indexMap);
                   }

                   // Execute next run or complete
                   if (runs.hasNext() && !bulkWriteBatchCombiner.shouldStopSendingMoreBatches()) {
                       executeRunsAsync(runs, connection, bulkWriteBatchCombiner, future);
                   } else if (bulkWriteBatchCombiner.hasErrors()) {
                       future.init(null, bulkWriteBatchCombiner.getError());
                   } else {
                       future.init(bulkWriteBatchCombiner.getResult(), null);
                   }
               }
           });
    }

    private boolean shouldUseWriteCommands(final Connection connection) {
        return writeConcern.isAcknowledged() && serverSupportsWriteCommands(connection.getServerDescription());
    }

    private boolean serverSupportsWriteCommands(final ServerDescription serverDescription) {
        return serverDescription.getVersion().compareTo(new ServerVersion(2, 6)) >= 0;
    }

    private Iterable<Run> getRunGenerator(final ServerDescription serverDescription) {
        if (ordered) {
            return new OrderedRunGenerator(serverDescription);
        } else {
            return new UnorderedRunGenerator(serverDescription);
        }
    }

    private class OrderedRunGenerator implements Iterable<Run> {

        private final int maxWriteBatchSize;

        public OrderedRunGenerator(final ServerDescription serverDescription) {
            this.maxWriteBatchSize = serverDescription.getMaxWriteBatchSize();
        }

        @Override
        public Iterator<Run> iterator() {
            return new Iterator<Run>() {
                private int curIndex;

                @Override
                public boolean hasNext() {
                    return curIndex < writeRequests.size();
                }

                @Override
                public Run next() {
                    Run run = new Run(writeRequests.get(curIndex).getType(), true);
                    int nextIndex = getNextIndex();
                    for (int i = curIndex; i < nextIndex; i++) {
                        run.add(writeRequests.get(i), i);
                    }
                    curIndex = nextIndex;
                    return run;
                }

                private int getNextIndex() {
                    WriteRequest.Type type = writeRequests.get(curIndex).getType();
                    for (int i = curIndex; i < writeRequests.size(); i++) {
                        if (i == curIndex + maxWriteBatchSize || writeRequests.get(i).getType() != type) {
                            return i;
                        }
                    }
                    return writeRequests.size();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Not implemented");
                }
            };
        }
    }


    private class UnorderedRunGenerator implements Iterable<Run> {
        private final int maxWriteBatchSize;

        public UnorderedRunGenerator(final ServerDescription serverDescription) {
            this.maxWriteBatchSize = serverDescription.getMaxWriteBatchSize();
        }

        @Override
        public Iterator<Run> iterator() {
            return new Iterator<Run>() {
                private final List<Run> runs = new ArrayList<Run>();
                private int curIndex;

                @Override
                public boolean hasNext() {
                    return curIndex < writeRequests.size() || !runs.isEmpty();
                }

                @Override
                public Run next() {
                    while (curIndex < writeRequests.size()) {
                        WriteRequest writeRequest = writeRequests.get(curIndex);
                        Run run = findRunOfType(writeRequest.getType());
                        if (run == null) {
                            run = new Run(writeRequest.getType(), false);
                            runs.add(run);
                        }
                        run.add(writeRequest, curIndex);
                        curIndex++;
                        if (run.size() == maxWriteBatchSize) {
                            runs.remove(run);
                            return run;
                        }
                    }

                    return runs.remove(0);
                }

                private Run findRunOfType(final WriteRequest.Type type) {
                    for (Run cur : runs) {
                        if (cur.type == type) {
                            return cur;
                        }
                    }
                    return null;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("Not implemented");
                }
            };
        }
    }

    private class Run {
        @SuppressWarnings("rawtypes")
        private final List runWrites = new ArrayList();
        private final WriteRequest.Type type;
        private final boolean ordered;
        private IndexMap indexMap = IndexMap.create();

        Run(final WriteRequest.Type type, final boolean ordered) {
            this.type = type;
            this.ordered = ordered;
        }

        @SuppressWarnings("unchecked")
        void add(final WriteRequest writeRequest, final int originalIndex) {
            indexMap = indexMap.add(runWrites.size(), originalIndex);
            runWrites.add(writeRequest);
        }

        public int size() {
            return runWrites.size();
        }

        @SuppressWarnings("unchecked")
        BulkWriteResult execute(final Connection connection) {
            final BulkWriteResult nextWriteResult;

            if (type == UPDATE) {
                nextWriteResult = getUpdatesRunExecutor((List<UpdateRequest>) runWrites, connection).execute();
            } else if (type == REPLACE) {
                nextWriteResult = getReplacesRunExecutor((List<ReplaceRequest<T>>) runWrites, connection).execute();
            } else if (type == INSERT) {
                nextWriteResult = getInsertsRunExecutor((List<InsertRequest<T>>) runWrites, connection).execute();
            } else if (type == REMOVE) {
                nextWriteResult = getRemovesRunExecutor((List<RemoveRequest>) runWrites, connection).execute();
            } else {
                throw new UnsupportedOperationException(format("Unsupported write of type %s", type));
            }
            return nextWriteResult;
        }

        @SuppressWarnings("unchecked")
        MongoFuture<BulkWriteResult> executeAsync(final Connection connection) {
            final MongoFuture<BulkWriteResult> nextWriteResult;

            if (type == UPDATE) {
                nextWriteResult = getUpdatesRunExecutor((List<UpdateRequest>) runWrites, connection).executeAsync();
            } else if (type == REPLACE) {
                nextWriteResult = getReplacesRunExecutor((List<ReplaceRequest<T>>) runWrites, connection).executeAsync();
            } else if (type == INSERT) {
                nextWriteResult = getInsertsRunExecutor((List<InsertRequest<T>>) runWrites, connection).executeAsync();
            } else if (type == REMOVE) {
                nextWriteResult = getRemovesRunExecutor((List<RemoveRequest>) runWrites, connection).executeAsync();
            } else {
                throw new UnsupportedOperationException(format("Unsupported write of type %s", type));
            }
            return nextWriteResult;
        }

        @SuppressWarnings("unchecked")
        RunExecutor getReplacesRunExecutor(final List<ReplaceRequest<T>> replaceRequests, final Connection connection) {
            return new RunExecutor(connection) {
                WriteProtocol getWriteProtocol(final int index) {
                    return new ReplaceProtocol<T>(namespace,
                                                  ordered, writeConcern,
                                                  asList(replaceRequests.get(index)),
                                                  encoder
                    );
                }

                WriteCommandProtocol getWriteCommandProtocol() {
                    return new ReplaceCommandProtocol<T>(namespace, ordered, writeConcern, replaceRequests, encoder);
                }

                @Override
                WriteRequest.Type getType() {
                    return REPLACE;
                }
            };
        }

        RunExecutor getRemovesRunExecutor(final List<RemoveRequest> removeRequests, final Connection connection) {
            return new RunExecutor(connection) {
                WriteProtocol getWriteProtocol(final int index) {
                    return new DeleteProtocol(namespace, ordered, writeConcern, asList(removeRequests.get(index))
                    );
                }

                WriteCommandProtocol getWriteCommandProtocol() {
                    return new DeleteCommandProtocol(namespace, ordered, writeConcern, removeRequests
                    );
                }

                @Override
                WriteRequest.Type getType() {
                    return REMOVE;
                }
            };
        }

        @SuppressWarnings("unchecked")
        RunExecutor getInsertsRunExecutor(final List<InsertRequest<T>> insertRequests, final Connection connection) {
            if (encoder instanceof CollectibleCodec) {
                for (InsertRequest<T> cur : insertRequests) {
                    ((CollectibleCodec<T>) encoder).generateIdIfAbsentFromDocument(cur.getDocument());
                }
            }
            return new RunExecutor(connection) {
                WriteProtocol getWriteProtocol(final int index) {
                    return new InsertProtocol<T>(namespace, ordered, writeConcern, asList(insertRequests.get(index)), encoder
                    );
                }

                WriteCommandProtocol getWriteCommandProtocol() {
                    return new InsertCommandProtocol<T>(namespace, ordered, writeConcern, insertRequests, encoder
                    );
                }

                @Override
                WriteRequest.Type getType() {
                    return INSERT;
                }

                int getCount(final WriteResult writeResult) {
                    return 1;
                }
            };
        }

        RunExecutor getUpdatesRunExecutor(final List<UpdateRequest> updates, final Connection connection) {
            return new RunExecutor(connection) {
                WriteProtocol getWriteProtocol(final int index) {

                    return new UpdateProtocol(namespace, ordered, writeConcern, asList(updates.get(index)));
                }

                WriteCommandProtocol getWriteCommandProtocol() {
                    return new UpdateCommandProtocol(namespace, ordered, writeConcern, updates, new DocumentCodec());
                }

                @Override
                WriteRequest.Type getType() {
                    return UPDATE;
                }

            };
        }

        private abstract class RunExecutor {
            private final Connection connection;

            RunExecutor(final Connection connection) {
                this.connection = connection;
            }

            abstract WriteProtocol getWriteProtocol(final int write);

            abstract WriteCommandProtocol getWriteCommandProtocol();

            abstract WriteRequest.Type getType();

            int getCount(final WriteResult writeResult) {
                return getType() == INSERT ? 1 : writeResult.getCount();
            }

            BulkWriteResult execute() {
                if (shouldUseWriteCommands(connection)) {
                    return getWriteCommandProtocol().execute(connection);
                } else {
                    BulkWriteBatchCombiner bulkWriteBatchCombiner = new BulkWriteBatchCombiner(connection.getServerAddress(),
                                                                                               ordered, writeConcern);
                    for (int i = 0; i < runWrites.size(); i++) {
                        IndexMap indexMap = IndexMap.create(i, 1);
                        indexMap = indexMap.add(0, i);
                        WriteProtocol writeProtocol = getWriteProtocol(i);
                        try {
                            WriteResult writeResult = writeProtocol.execute(connection);
                            if (writeResult.wasAcknowledged()) {
                                bulkWriteBatchCombiner.addResult(getResult(writeResult), indexMap);
                            }
                        } catch (MongoWriteException writeException) {
                            if (writeException.getCommandResult().getResponse().get("wtimeout") != null) {
                                bulkWriteBatchCombiner.addWriteConcernErrorResult(getWriteConcernError(writeException));
                            } else {
                                bulkWriteBatchCombiner.addWriteErrorResult(getBulkWriteError(writeException), indexMap);
                            }
                            if (bulkWriteBatchCombiner.shouldStopSendingMoreBatches()) {
                                break;
                            }
                        }
                    }
                    return bulkWriteBatchCombiner.getResult();
                }
            }

            MongoFuture<BulkWriteResult> executeAsync() {
                if (shouldUseWriteCommands(connection)) {
                    return getWriteCommandProtocol().executeAsync(connection);
                } else {
                    final BulkWriteBatchCombiner bulkWriteBatchCombiner = new BulkWriteBatchCombiner(connection.getServerAddress(),
                                                                                                     ordered, writeConcern);
                    SingleResultFuture<BulkWriteResult> future = new SingleResultFuture<BulkWriteResult>();
                    executeRunWritesAsync(runWrites.size(), 0, connection, bulkWriteBatchCombiner, future);
                    return future;
                }
            }

            private void executeRunWritesAsync(final int numberOfRuns, final int currentPosition, final Connection connection,
                                               final BulkWriteBatchCombiner bulkWriteBatchCombiner,
                                               final SingleResultFuture<BulkWriteResult> future) {

                final IndexMap indexMap = IndexMap.create(currentPosition, 1).add(0, currentPosition);
                getWriteProtocol(currentPosition).executeAsync(connection).register(new SingleResultCallback<WriteResult>() {

                    @Override
                    public void onResult(final WriteResult result, final MongoException e) {
                        final int nextRunPosition = currentPosition + 1;
                        if (e != null) {
                            if (e instanceof MongoWriteException) {
                                MongoWriteException writeException = (MongoWriteException) e;
                                if (writeException.getCommandResult().getResponse().get("wtimeout") != null) {
                                    bulkWriteBatchCombiner.addWriteConcernErrorResult(getWriteConcernError(writeException));
                                } else {
                                    bulkWriteBatchCombiner.addWriteErrorResult(getBulkWriteError(writeException), indexMap);
                                }
                            } else {
                                future.init(null, e);
                                return;
                            }
                        } else if (result.wasAcknowledged()) {
                            bulkWriteBatchCombiner.addResult(getResult(result), indexMap);
                        }

                        // Execute next run or complete
                        if (numberOfRuns != nextRunPosition && !bulkWriteBatchCombiner.shouldStopSendingMoreBatches()) {
                            executeRunWritesAsync(numberOfRuns, nextRunPosition, connection, bulkWriteBatchCombiner, future);
                        } else if (bulkWriteBatchCombiner.hasErrors()) {
                            future.init(null, bulkWriteBatchCombiner.getError());
                        } else {
                            future.init(bulkWriteBatchCombiner.getResult(), null);
                        }
                    }
                });
            }

            BulkWriteResult getResult(final WriteResult writeResult) {
                int count = getCount(writeResult);
                List<BulkWriteUpsert> upsertedItems = getUpsertedItems(writeResult);
                Integer modifiedCount = (getType() == UPDATE || getType() == REPLACE) ? null : 0;
                return new AcknowledgedBulkWriteResult(getType(), count - upsertedItems.size(), modifiedCount, upsertedItems);
            }

            List<BulkWriteUpsert> getUpsertedItems(final WriteResult writeResult) {
                return writeResult.getUpsertedId() == null
                       ? Collections.<BulkWriteUpsert>emptyList()
                       : asList(new BulkWriteUpsert(0, writeResult.getUpsertedId()));
            }

            private BulkWriteError getBulkWriteError(final MongoWriteException writeException) {
                return new BulkWriteError(writeException.getErrorCode(), writeException.getErrorMessage(),
                                          translateGetLastErrorResponseToErrInfo(writeException.getCommandResult().getResponse()), 0);
            }

            private WriteConcernError getWriteConcernError(final MongoWriteException writeException) {
                return new WriteConcernError(writeException.getErrorCode(),
                                             ((BsonString) writeException.getCommandResult().getResponse().get("err")).getValue(),
                                             translateGetLastErrorResponseToErrInfo(writeException.getCommandResult().getResponse()));
            }

            private BsonDocument translateGetLastErrorResponseToErrInfo(final BsonDocument response) {
                BsonDocument errInfo = new BsonDocument();
                for (Map.Entry<String, BsonValue> entry : response.entrySet()) {
                    if (IGNORED_KEYS.contains(entry.getKey())) {
                        continue;
                    }
                    errInfo.put(entry.getKey(), entry.getValue());
                }
                return errInfo;
            }
        }
    }

    private static final List<String> IGNORED_KEYS = asList("ok", "err", "code");
}
