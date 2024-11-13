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

import com.mongodb.internal.function.CheckedSupplier;

/**
 * <p>This class is not part of the public API and may be removed or changed at any time</p>
 */
public final class ValueOrExceptionContainer<T> {
    private final T value;
    private final Exception exception;

    public ValueOrExceptionContainer(final CheckedSupplier<T, Exception> supplier) {
        T value = null;
        Exception exception = null;
        try {
            value = supplier.get();
        } catch (Exception e) {
            exception = e;
        }
        this.value = value;
        this.exception = exception;
    }

    public T get() throws Exception {
        if (containsException()) {
            throw exception;
        }
        return value;
    }

    public boolean containsException() {
        return exception != null;
    }
}
