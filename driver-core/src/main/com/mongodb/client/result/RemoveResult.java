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

package com.mongodb.client.result;

/**
 * The result of a remove operation.
 *
 * @since 3.0
 */
public class RemoveResult {
    private final long removedCount;

    /**
     * Construct an instance.
     *
     * @param removedCount the number of documents removed
     */
    public RemoveResult(final long removedCount) {
        this.removedCount = removedCount;
    }

    /**
     * Gets the number of documents removed.
     *
     * @return the number of documents removed
     */
    public long getRemovedCount() {
        return removedCount;
    }
}
