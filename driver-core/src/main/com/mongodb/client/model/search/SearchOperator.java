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

import com.mongodb.annotations.Evolving;
import com.mongodb.client.model.Aggregates;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.util.Iterator;
import java.util.Map;

import static com.mongodb.assertions.Assertions.assertTrue;
import static com.mongodb.assertions.Assertions.isTrueArgument;
import static com.mongodb.internal.client.model.Util.combineToBsonValue;
import static com.mongodb.internal.client.model.Util.sizeAtLeast;
import static org.bson.assertions.Assertions.notNull;

/**
 * The core part of the {@link Aggregates#search(SearchOperator, SearchOptions) $search} pipeline stage of an aggregation pipeline.
 *
 * @mongodb.atlas.manual atlas-search/operators-and-collectors/#operators Search operators
 * @since 4.7
 */
@Evolving
public interface SearchOperator extends Bson {
    /**
     * Returns a {@link SearchOperator} that may combine multiple {@link SearchOperator}s.
     * If there are multiple combinations for the same rule, only the latest in the {@linkplain Iterator#next() iteration order} has effect.
     *
     * @param combinations Non-empty combinations of clauses.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/compound/ compound operator
     */
    static CompoundSearchOperator compound(final Iterable<? extends SearchOperatorCombination> combinations) {
        notNull("combinations", combinations);
        isTrueArgument("combinations must not be empty", sizeAtLeast(combinations, 1));
        Bson compoundValue = new Bson() {
            @Override
            public <TDocument> BsonDocument toBsonDocument(final Class<TDocument> documentClass, final CodecRegistry codecRegistry) {
                BsonDocument result = new BsonDocument();
                for (final SearchOperatorCombination combination : combinations) {
                    BsonDocument doc = combination.toBsonDocument(documentClass, codecRegistry);
                    assertTrue(doc.size() == 1);
                    Map.Entry<String, BsonValue> entry = doc.entrySet().iterator().next();
                    result.append(entry.getKey(), entry.getValue());
                }
                return result;
            }

            @Override
            public String toString() {
                return combinations.toString();
            }
        };
        return new SearchConstructibleBsonElement("compound", compoundValue);
    }

    /**
     * Returns a {@link SearchOperator} that tests if the {@code path} exists in a document.
     *
     * @param path The path to test.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/exists/ exists operator
     */
    static ExistsSearchOperator exists(final FieldSearchPath path) {
        return new SearchConstructibleBsonElement("exists", new BsonDocument("path", (notNull("path", path)).toBsonValue()));
    }

    /**
     * Returns a {@link SearchOperator} that performs a full-text search.
     *
     * @param queries Non-empty terms to search for.
     * @param paths Non-empty document fields to be searched.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/text/ text operator
     */
    static TextSearchOperator text(final Iterable<String> queries, final Iterable<? extends SearchPath> paths) {
        Iterator<String> queryIterator = notNull("queries", queries).iterator();
        isTrueArgument("queries must not be empty", queryIterator.hasNext());
        String firstQuery = queryIterator.next();
        Iterator<? extends SearchPath> pathIterator = notNull("paths", paths).iterator();
        isTrueArgument("paths must not be empty", pathIterator.hasNext());
        return new SearchConstructibleBsonElement("text", new Document("query", queryIterator.hasNext() ? queries : firstQuery)
                .append("path", combineToBsonValue(pathIterator)));
    }

    /**
     * Creates a {@link SearchOperator} from a {@link Bson} in situations when there is no builder method that better satisfies your needs.
     * This method cannot be used to validate the syntax.
     * <p>
     * <i>Example</i><br>
     * The following code creates two functionally equivalent {@link SearchOperator}s,
     * though they may not be {@linkplain Object#equals(Object) equal}.
     * <pre>{@code
     *  SearchOperator operator1 = SearchOperator.exists(
     *          SearchPath.fieldPath("fieldName"));
     *  SearchOperator operator2 = SearchOperator.of(new Document("exists",
     *          new Document("path", SearchPath.fieldPath("fieldName").toBsonValue())));
     * }</pre>
     *
     * @param operator A {@link Bson} representing the required {@link SearchOperator}.
     * @return The requested {@link SearchOperator}.
     */
    static SearchOperator of(final Bson operator) {
        return new SearchConstructibleBson(notNull("operator", operator));
    }
}
