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
package com.mongodb.internal;

import com.mongodb.lang.Nullable;

import java.util.Iterator;

import static com.mongodb.assertions.Assertions.fail;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

public final class Iterables {
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> Iterable<T> concat(@Nullable final T first, @Nullable final T... more) {
        if (more == null) {
            return singleton(first);
        } else {
            Iterable<? extends T> moreIterable = asList(more);
            return () -> new ConcatIterator<>(first, moreIterable);
        }
    }

    public static <T> Iterable<T> concat(@Nullable final T first, final Iterable<? extends T> more) {
        return () -> new ConcatIterator<>(first, more);
    }

    private Iterables() {
        throw fail();
    }

    private static class ConcatIterator<T> implements Iterator<T> {
        private static final Object NONE = new Object();

        @Nullable
        private T first;
        @Nullable
        private Iterator<? extends T> moreIterator;
        private final Iterable<? extends T> more;

        ConcatIterator(@Nullable final T first, final Iterable<? extends T> more) {
            this.first = first;
            this.more = more;
        }

        @Override
        public boolean hasNext() {
            return firstNotConsumed() || moreIterator().hasNext();
        }

        @Override
        public T next() {
            return firstNotConsumed() ? consumeFirst() : moreIterator().next();
        }

        private boolean firstNotConsumed() {
            return first != NONE;
        }

        @SuppressWarnings("unchecked")
        private T consumeFirst() {
            T result = first;
            first = (T) NONE;
            return result;
        }

        private Iterator<? extends T> moreIterator() {
            if (moreIterator == null) {
                moreIterator = more.iterator();
            }
            return moreIterator;
        }
    }
}
