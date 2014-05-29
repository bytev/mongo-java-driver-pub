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

import org.mongodb.MongoFuture;
import org.mongodb.binding.AsyncReadBinding;

/**
 * An operation which asynchronously reads from a MongoDB server.
 *
 * @param <T> the return type of the execute method
 *
 * @since 3.0
 */
public interface AsyncReadOperation<T> {
    /**
     * General execute which can return anything of type T
     *
     * @param binding the binding to execute in the context of
     * @return a future for the result
     */
    MongoFuture<T> executeAsync(AsyncReadBinding binding);
}