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

package org.mongodb.async;

import org.mongodb.Block;
import org.mongodb.CancellationToken;
import org.mongodb.Function;
import org.mongodb.MongoException;
import org.mongodb.MongoFuture;
import org.mongodb.connection.SingleResultCallback;
import org.mongodb.operation.SingleResultFuture;

import java.util.Collection;

class MappingIterable<T, U> implements MongoIterable<U> {
    private final MongoIterable<T> iterable;
    private final Function<T, U> mapper;

    public MappingIterable(final MongoIterable<T> iterable, final Function<T, U> mapper) {
        this.iterable = iterable;
        this.mapper = mapper;
    }

    @Override
    public MongoFuture<Void> forEach(final Block<? super U> block) {
        return forEach(block, new CancellationToken());
    }

    @Override
    public MongoFuture<Void> forEach(final Block<? super U> block, final CancellationToken cancellationToken) {
        final SingleResultFuture<Void> future = new SingleResultFuture<Void>();
        iterable.forEach(new Block<T>() {
            @Override
            public void apply(final T t) {
                block.apply(mapper.apply(t));
            }
        }, cancellationToken).register(new SingleResultCallback<Void>() {
            @Override
            public void onResult(final Void result, final MongoException e) {
                if (e != null) {
                    future.init(null, e);
                } else {
                    future.init(null, null);
                }
            }
        });
        return future;
    }

    @Override
    public <A extends Collection<? super U>> MongoFuture<A> into(final A target) {
        final SingleResultFuture<A> future = new SingleResultFuture<A>();
        iterable.forEach(new Block<T>() {
            @Override
            public void apply(final T t) {
                target.add(mapper.apply(t));
            }
        }).register(new SingleResultCallback<Void>() {
            @Override
            public void onResult(final Void result, final MongoException e) {
                if (e != null) {
                    future.init(null, e);
                } else {
                    future.init(target, null);
                }
            }
        });
        return future;
    }

    @Override
    public <V> MongoIterable<V> map(final Function<U, V> mapper) {
        return new MappingIterable<U, V>(this, mapper);
    }
}
