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
package com.mongodb.client.model.search;

import com.mongodb.annotations.Beta;
import com.mongodb.annotations.Sealed;

/**
 * @see SearchScoreExpression#pathExpression(FieldSearchPath)
 * @since 4.7
 */
@Sealed
@Beta(Beta.Reason.CLIENT)
public interface PathSearchScoreExpression extends SearchScoreExpression {
    /**
     * Creates a new {@link PathSearchScoreExpression} with the value to fall back to
     * if the field specified via {@link SearchScoreExpression#pathExpression(FieldSearchPath)} is not found in a document.
     *
     * @param fallback The fallback value.
     * @return A new {@link PathSearchScoreExpression}.
     */
    PathSearchScoreExpression undefined(float fallback);
}
