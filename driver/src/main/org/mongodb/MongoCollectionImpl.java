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

package org.mongodb;

import org.bson.codecs.Codec;
import org.bson.types.BsonDocument;
import org.bson.types.BsonDocumentWrapper;
import org.bson.types.Code;
import org.mongodb.codecs.CollectibleCodec;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.operation.AggregateOperation;
import org.mongodb.operation.CountOperation;
import org.mongodb.operation.Find;
import org.mongodb.operation.FindAndRemove;
import org.mongodb.operation.FindAndRemoveOperation;
import org.mongodb.operation.FindAndReplace;
import org.mongodb.operation.FindAndReplaceOperation;
import org.mongodb.operation.FindAndUpdate;
import org.mongodb.operation.FindAndUpdateOperation;
import org.mongodb.operation.InsertOperation;
import org.mongodb.operation.InsertRequest;
import org.mongodb.operation.MapReduce;
import org.mongodb.operation.MapReduceToCollectionOperation;
import org.mongodb.operation.MapReduceWithInlineResultsOperation;
import org.mongodb.operation.QueryFlag;
import org.mongodb.operation.QueryOperation;
import org.mongodb.operation.ReadOperation;
import org.mongodb.operation.RemoveOperation;
import org.mongodb.operation.RemoveRequest;
import org.mongodb.operation.ReplaceOperation;
import org.mongodb.operation.ReplaceRequest;
import org.mongodb.operation.UpdateOperation;
import org.mongodb.operation.UpdateRequest;
import org.mongodb.operation.WriteOperation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static java.util.Arrays.asList;

class MongoCollectionImpl<T> implements MongoCollection<T> {

    private final CollectionAdministration admin;
    private final MongoClientImpl client;
    private final String name;
    private final MongoDatabase database;
    private final MongoCollectionOptions options;
    private final Codec<T> codec;

    public MongoCollectionImpl(final String name, final MongoDatabaseImpl database,
                               final Codec<T> codec, final MongoCollectionOptions options,
                               final MongoClientImpl client) {

        this.codec = codec;
        this.name = name;
        this.database = database;
        this.options = options;
        this.client = client;
        admin = new CollectionAdministrationImpl(client, getNamespace());
    }

    @Override
    public WriteResult insert(final T document) {
        return new MongoCollectionView().insert(document);
    }

    @Override
    public WriteResult insert(final List<T> documents) {
        return new MongoCollectionView().insert(documents);
    }

    @Override
    public WriteResult save(final T document) {
        return new MongoCollectionView().save(document);
    }

    @Override
    public MongoPipeline<T> pipe() {
        return new MongoCollectionPipeline();
    }

    @Override
    public CollectionAdministration tools() {
        return admin;
    }

    @Override
    public MongoView<T> find() {
        return new MongoCollectionView();
    }

    @Override
    public MongoView<T> find(final Document filter) {
        return new MongoCollectionView().find(filter);
    }

    @Override
    public MongoView<T> find(final ConvertibleToDocument filter) {
        return new MongoCollectionView().find(filter);
    }

    @Override
    public MongoView<T> withWriteConcern(final WriteConcern writeConcern) {
        return new MongoCollectionView().withWriteConcern(writeConcern);
    }

    private Codec<Document> getDocumentCodec() {
        return getOptions().getDocumentCodec();
    }

    @Override
    public MongoDatabase getDatabase() {
        return database;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Codec<T> getCodec() {
        return codec;
    }

    @Override
    public MongoCollectionOptions getOptions() {
        return options;
    }

    @Override
    public MongoNamespace getNamespace() {
        return new MongoNamespace(getDatabase().getName(), getName());
    }

    <V> V execute(final ReadOperation<V> operation, final ReadPreference readPreference) {
        return client.execute(operation, readPreference);
    }

    <V> V execute(final WriteOperation<V> operation) {
        return client.execute(operation);
    }

    private BsonDocument wrap(final Document command) {
        return new BsonDocumentWrapper<Document>(command, options.getDocumentCodec());
    }

    private final class MongoCollectionView implements MongoView<T> {
        private final Find findOp;
        private ReadPreference readPreference;
        private WriteConcern writeConcern;
        private boolean limitSet;
        private boolean upsert;

        private MongoCollectionView() {
            findOp = new Find();
            writeConcern = getOptions().getWriteConcern();
            readPreference = getOptions().getReadPreference();
        }

        @Override
        public MongoCursor<T> iterator() {
            return get();
        }

        @Override
        public MongoView<T> cursorFlags(final  EnumSet<QueryFlag> flags) {
            findOp.addFlags(flags);
            return this;
        }

        @Override
        public MongoView<T> find(final Document filter) {
            findOp.filter(new BsonDocumentWrapper<Document>(filter, getDocumentCodec()));
            return this;
        }

        MongoView<T> find(final BsonDocument filter) {
            findOp.filter(filter);
            return this;
        }

        @Override
        public MongoView<T> find(final ConvertibleToDocument filter) {
            return find(filter.toDocument());
        }

        @Override
        public MongoView<T> sort(final ConvertibleToDocument sortCriteria) {
            return sort(sortCriteria.toDocument());
        }

        @Override
        public MongoView<T> sort(final Document sortCriteria) {
            findOp.order(wrap(sortCriteria));
            return this;
        }

        @Override
        public MongoView<T> fields(final Document selector) {
            findOp.select(wrap(selector));
            return this;
        }

        @Override
        public MongoView<T> fields(final ConvertibleToDocument selector) {
            return fields(selector.toDocument());
        }

        @Override
        public MongoView<T> upsert() {
            upsert = true;
            return this;
        }

        @Override
        public MongoView<T> withQueryOptions(final QueryOptions queryOptions) {
            findOp.options(queryOptions);
            return this;
        }

        @Override
        public MongoView<T> skip(final int skip) {
            findOp.skip(skip);
            return this;
        }

        @Override
        public MongoView<T> limit(final int limit) {
            findOp.limit(limit);
            limitSet = true;
            return this;
        }

        @Override
        public MongoView<T> withReadPreference(final ReadPreference readPreference) {
            this.readPreference = readPreference;
            return this;
        }

        @Override
        public MongoCursor<T> get() {
            return execute(new QueryOperation<T>(getNamespace(), findOp, getCodec()), readPreference);
        }

        @Override
        public T getOne() {
            MongoCursor<T> cursor = execute(new QueryOperation<T>(getNamespace(), findOp.batchSize(-1), getCodec()),
                                            readPreference);

            return cursor.hasNext() ? cursor.next() : null;
        }

        @Override
        public long count() {
            return execute(new CountOperation(getNamespace(), findOp), readPreference);
        }

        @Override
        public MongoIterable<Document> mapReduce(final String map, final String reduce) {
            //TODO: support supplied read preferences?
            MapReduce mapReduce = new MapReduce(new Code(map), new Code(reduce)).filter(findOp.getFilter())
                                                                                .limit(findOp.getLimit());

            if (mapReduce.isInline()) {
                MapReduceWithInlineResultsOperation<Document> operation =
                new MapReduceWithInlineResultsOperation<Document>(getNamespace(), mapReduce, new DocumentCodec());
                return new MapReduceResultsIterable<T, Document>(operation, MongoCollectionImpl.this);
            } else {
                execute(new MapReduceToCollectionOperation(getNamespace(), mapReduce));
                return client.getDatabase(mapReduce.getOutput().getDatabaseName()).getCollection(mapReduce.getOutput().getCollectionName())
                             .find();
            }
        }

        @Override
        public void forEach(final Block<? super T> block) {
            MongoCursor<T> cursor = get();
            try {
                while (cursor.hasNext()) {
                    block.apply(cursor.next());
                }
            } finally {
                cursor.close();
            }
        }


        @Override
        public <A extends Collection<? super T>> A into(final A target) {
            forEach(new Block<T>() {
                @Override
                public void apply(final T t) {
                    target.add(t);
                }
            });
            return target;
        }

        @Override
        public <U> MongoIterable<U> map(final Function<T, U> mapper) {
            return new MappingIterable<T, U>(this, mapper);
        }

        @Override
        public MongoView<T> withWriteConcern(final WriteConcern writeConcernForThisOperation) {
            writeConcern = writeConcernForThisOperation;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public WriteResult insert(final T document) {
            return execute(new InsertOperation<T>(getNamespace(), true, writeConcern, asList(new InsertRequest<T>(document)), getCodec()));
        }

        @Override
        public WriteResult insert(final List<T> documents) {
            List<InsertRequest<T>> insertRequestList = new ArrayList<InsertRequest<T>>(documents.size());
            for (T cur : documents) {
                insertRequestList.add(new InsertRequest<T>(cur));
            }
            return execute(new InsertOperation<T>(getNamespace(), true, writeConcern, insertRequestList, getCodec()));
        }

        @Override
        public WriteResult save(final T document) {
            if (!(codec instanceof CollectibleCodec)) {
                throw new UnsupportedOperationException();  // TODO: support this
            }
            CollectibleCodec<T> collectibleCodec = (CollectibleCodec<T>) codec;
            if (!collectibleCodec.documentHasId(document)) {
                return insert(document);
            } else {
                return find(new BsonDocument("_id", collectibleCodec.getDocumentId(document))).upsert().replace(document);
            }
        }

        @Override
        public WriteResult remove() {
            RemoveRequest removeRequest = new RemoveRequest(findOp.getFilter()).multi(getMultiFromLimit());
            return execute(new RemoveOperation(getNamespace(), true, writeConcern, asList(removeRequest),
                                               getDocumentCodec()));
        }

        @Override
        public WriteResult removeOne() {
            RemoveRequest removeRequest = new RemoveRequest(findOp.getFilter()).multi(false);
            return execute(new RemoveOperation(getNamespace(), true, writeConcern, asList(removeRequest),
                                               getDocumentCodec()));
        }

        @Override
        public WriteResult update(final Document updateOperations) {
            UpdateRequest update = new UpdateRequest(findOp.getFilter(), wrap(updateOperations)).upsert(upsert)
                                                                                                .multi(getMultiFromLimit());
            return execute(new UpdateOperation(getNamespace(), true, writeConcern, asList(update),
                                               getDocumentCodec()));
        }

        @Override
        public WriteResult update(final ConvertibleToDocument updateOperations) {
            return update(updateOperations.toDocument());
        }

        @Override
        public WriteResult updateOne(final Document updateOperations) {
            UpdateRequest update = new UpdateRequest(findOp.getFilter(), wrap(updateOperations)).upsert(upsert).multi(false);
            return execute(new UpdateOperation(getNamespace(), true, writeConcern, asList(update), getDocumentCodec()));
        }

        @Override
        public WriteResult updateOne(final ConvertibleToDocument updateOperations) {
            return updateOne(updateOperations.toDocument());
        }

        @Override
        @SuppressWarnings("unchecked")
        public WriteResult replace(final T replacement) {
            ReplaceRequest<T> replaceRequest = new ReplaceRequest<T>(findOp.getFilter(), replacement).upsert(upsert);
            return execute(new ReplaceOperation<T>(getNamespace(), true, writeConcern, asList(replaceRequest),
                                                   getCodec()));
        }

        @Override
        public T updateOneAndGet(final Document updateOperations) {
            return updateOneAndGet(updateOperations, Get.AfterChangeApplied);
        }

        @Override
        public T updateOneAndGet(final ConvertibleToDocument updateOperations) {
            return updateOneAndGet(updateOperations.toDocument());
        }

        @Override
        public T replaceOneAndGet(final T replacement) {
            return replaceOneAndGet(replacement, Get.AfterChangeApplied);
        }

        @Override
        public T getOneAndUpdate(final Document updateOperations) {
            return updateOneAndGet(updateOperations, Get.BeforeChangeApplied);
        }

        @Override
        public T getOneAndUpdate(final ConvertibleToDocument updateOperations) {
            return getOneAndUpdate(updateOperations.toDocument());
        }

        @Override
        public T getOneAndReplace(final T replacement) {
            return replaceOneAndGet(replacement, Get.BeforeChangeApplied);
        }

        public T updateOneAndGet(final Document updateOperations, final Get beforeOrAfter) {
            FindAndUpdate findAndUpdate = new FindAndUpdate().where(findOp.getFilter())
                                                             .updateWith(wrap(updateOperations))
                                                             .returnNew(asBoolean(beforeOrAfter))
                                                             .select(findOp.getFields())
                                                             .sortBy(findOp.getOrder())
                                                             .upsert(upsert);

            return execute(new FindAndUpdateOperation<T>(getNamespace(), findAndUpdate, getCodec()));
        }

        public T replaceOneAndGet(final T replacement, final Get beforeOrAfter) {
            FindAndReplace<T> findAndReplace = new FindAndReplace<T>(replacement).where(findOp.getFilter())
                                                                                 .returnNew(asBoolean(beforeOrAfter))
                                                                                 .select(findOp.getFields())
                                                                                 .sortBy(findOp.getOrder())
                                                                                 .upsert(upsert);
            return execute(new FindAndReplaceOperation<T>(getNamespace(), findAndReplace, getCodec()));
        }

        @Override
        public T getOneAndRemove() {
            FindAndRemove<T> findAndRemove = new FindAndRemove<T>().where(findOp.getFilter())
                                                                   .select(findOp.getFields())
                                                                   .sortBy(findOp.getOrder());

            return execute(new FindAndRemoveOperation<T>(getNamespace(), findAndRemove, getCodec()));
        }

        boolean asBoolean(final Get get) {
            return get == Get.AfterChangeApplied;
        }

        private boolean getMultiFromLimit() {
            if (limitSet) {
                if (findOp.getLimit() == 1) {
                    return false;
                } else if (findOp.getLimit() == 0) {
                    return true;
                } else {
                    throw new IllegalArgumentException("Update currently only supports a limit of either none or 1");
                }
            } else {
                return true;
            }
        }
    }

    private class MongoCollectionPipeline implements MongoPipeline<T> {
        private final List<BsonDocument> pipeline;

        private MongoCollectionPipeline() {
            pipeline = new ArrayList<BsonDocument>();
        }

        public MongoCollectionPipeline(final MongoCollectionPipeline from) {
            pipeline = new ArrayList<BsonDocument>(from.pipeline);
        }

        @Override
        public MongoPipeline<T> find(final Document criteria) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$match", criteria)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> sort(final Document sortCriteria) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$sort", sortCriteria)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> skip(final long skip) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$skip", skip)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> limit(final long limit) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$limit", limit)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> project(final Document projection) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$project", projection)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> group(final Document group) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$group", group)));
            return newPipeline;
        }

        @Override
        public MongoPipeline<T> unwind(final String field) {
            MongoCollectionPipeline newPipeline = new MongoCollectionPipeline(this);
            newPipeline.pipeline.add(wrap(new Document("$unwind", field)));
            return newPipeline;
        }

        @Override
        public <U> MongoIterable<U> map(final Function<T, U> mapper) {
            return new MappingIterable<T, U>(this, mapper);
        }

        @Override
        @SuppressWarnings("unchecked")
        public MongoCursor<T> iterator() {
            return execute(new AggregateOperation<T>(getNamespace(), pipeline, getCodec(), AggregationOptions.builder().build()),
                           options.getReadPreference());
        }

        @Override
        public void forEach(final Block<? super T> block) {
            MongoCursor<T> cursor = iterator();
            try {
                while (cursor.hasNext()) {
                    block.apply(cursor.next());
                }
            } finally {
                cursor.close();
            }
        }

        @Override
        public <A extends Collection<? super T>> A into(final A target) {
            forEach(new Block<T>() {
                @Override
                public void apply(final T t) {
                    target.add(t);
                }
            });
            return target;
        }
    }
}
