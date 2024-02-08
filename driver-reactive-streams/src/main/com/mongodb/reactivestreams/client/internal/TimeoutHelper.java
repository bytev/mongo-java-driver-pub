/*
 * Copyright 2008-present MongoDB, Inc.
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

package com.mongodb.reactivestreams.client.internal;

import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.internal.time.Timeout;
import com.mongodb.lang.Nullable;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import reactor.core.publisher.Mono;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * <p>This class is not part of the public API and may be removed or changed at any time</p>
 */
public final class TimeoutHelper {
    private static final String DEFAULT_TIMEOUT_MESSAGE = "Operation exceeded the timeout limit.";

    private TimeoutHelper() {
        //NOP
    }

    public static long getRemainingMs(final String message, final Timeout timeout) {
        long remainingMs = timeout.remaining(MILLISECONDS);
        if (remainingMs <= 0) {
            throw new MongoOperationTimeoutException(message);
        }
        return remainingMs;
    }

    public static <T> MongoCollection<T> collectionWithTimeout(final MongoCollection<T> collection,
                                                               @Nullable final Timeout timeout) {
        if (shouldOverrideTimeout(timeout)) {
            long remainingMs = getRemainingMs(DEFAULT_TIMEOUT_MESSAGE, timeout);
            return collection.withTimeout(remainingMs, MILLISECONDS);
        }
        return collection;
    }

    public static <T> Mono<MongoCollection<T>> collectionWithTimeoutMono(final MongoCollection<T> collection,
                                                                         @Nullable final Timeout timeout) {
        try {
            return Mono.just(collectionWithTimeout(collection, timeout));
        } catch (MongoOperationTimeoutException e) {
            return Mono.error(e);
        }
    }

    public static <T> Mono<MongoCollection<T>> collectionWithTimeoutDeferred(final MongoCollection<T> collection,
                                                                             @Nullable final Timeout timeout) {
        return Mono.defer(() -> collectionWithTimeoutMono(collection, timeout));
    }


    public static MongoDatabase databaseWithTimeout(final MongoDatabase database,
                                                    @Nullable final Timeout timeout) {
        return databaseWithTimeout(database, DEFAULT_TIMEOUT_MESSAGE, timeout);
    }

    public static MongoDatabase databaseWithTimeout(final MongoDatabase database,
                                                    final String message,
                                                    @Nullable final Timeout timeout) {
        if (shouldOverrideTimeout(timeout)) {
            long remainingMs = getRemainingMs(message, timeout);
            return database.withTimeout(remainingMs, MILLISECONDS);
        }
        return database;
    }

    private static Mono<MongoDatabase> databaseWithTimeoutMono(final MongoDatabase database,
                                                               final String message,
                                                               @Nullable final Timeout timeout) {
        try {
            return Mono.just(databaseWithTimeout(database, message, timeout));
        } catch (MongoOperationTimeoutException e) {
            return Mono.error(e);
        }
    }

    public static Mono<MongoDatabase> databaseWithTimeoutDeferred(final MongoDatabase database,
                                                                  @Nullable final Timeout timeout) {
        return Mono.defer(() -> databaseWithTimeoutMono(database, DEFAULT_TIMEOUT_MESSAGE, timeout));
    }

    public static Mono<MongoDatabase> databaseWithTimeoutDeferred(final MongoDatabase database,
                                                                  final String message,
                                                                  @Nullable final Timeout timeout) {
        return Mono.defer(() -> databaseWithTimeoutMono(database, message, timeout));
    }

    private static boolean shouldOverrideTimeout(@Nullable final Timeout timeout) {
        return timeout != null && !timeout.isInfinite();
    }
}
