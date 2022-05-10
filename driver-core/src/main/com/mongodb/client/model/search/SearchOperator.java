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
import org.bson.BsonType;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.Iterator;

import static com.mongodb.assertions.Assertions.isTrueArgument;
import static com.mongodb.internal.client.model.Util.combineToBsonValue;
import static java.util.Collections.singleton;
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
     * Creates a new {@link SearchOperator} with the scoring modifier specified.
     *
     * @param modifier The scoring modifier.
     * @return A new {@link SearchOperator}.
     */
    SearchOperator score(SearchScore modifier);

    /**
     * Returns a base for a {@link SearchOperator} that may combine multiple {@link SearchOperator}s.
     * Combining {@link SearchOperator}s affects calculation of the relevance score.
     *
     * @return A base for a {@link CompoundSearchOperator}.
     * @mongodb.atlas.manual atlas-search/compound/ compound operator
     */
    static CompoundSearchOperatorBase compound() {
        return new SearchConstructibleBsonElement("compound");
    }

    /**
     * Returns a {@link SearchOperator} that tests if the {@code path} exists in a document.
     *
     * @param path The path to test.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/exists/ exists operator
     */
    static ExistsSearchOperator exists(final FieldSearchPath path) {
        return new SearchConstructibleBsonElement("exists", new Document("path", notNull("path", path).toValue()));
    }

    /**
     * Returns a {@link SearchOperator} that performs a full-text search.
     *
     * @param query A string to search for.
     * @param path A field to be searched.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/text/ text operator
     */
    static TextSearchOperator text(final String query, final SearchPath path) {
        return text(singleton(notNull("query", query)), singleton(notNull("path", path)));
    }

    /**
     * Returns a {@link SearchOperator} that performs a full-text search.
     *
     * @param queries Non-empty strings to search for.
     * @param paths Non-empty fields to be searched.
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
                .append("path", combineToBsonValue(pathIterator, false)));
    }

    /**
     * Returns a {@link SearchOperator} that may be used to implement search-as-you-type functionality.
     *
     * @param query A string to search for.
     * @param path A field to be searched.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/autocomplete/ autocomplete operator
     */
    static AutocompleteSearchOperator autocomplete(final String query, final FieldSearchPath path) {
        return autocomplete(singleton(notNull("query", query)), notNull("path", path));
    }

    /**
     * Returns a {@link SearchOperator} that may be used to implement search-as-you-type functionality.
     *
     * @param queries Non-empty strings to search for.
     * @param path A field to be searched.
     * @return The requested {@link SearchOperator}.
     * @mongodb.atlas.manual atlas-search/autocomplete/ autocomplete operator
     */
    static AutocompleteSearchOperator autocomplete(final Iterable<String> queries, final FieldSearchPath path) {
        Iterator<String> queryIterator = notNull("queries", queries).iterator();
        isTrueArgument("queries must not be empty", queryIterator.hasNext());
        String firstQuery = queryIterator.next();
        return new SearchConstructibleBsonElement("autocomplete", new Document("query", queryIterator.hasNext() ? queries : firstQuery)
                .append("path", notNull("path", path).toValue()));
    }

    /**
     * Returns a base for a {@link SearchOperator} that tests if the values of
     * a BSON {@link BsonType#INT32 32-bit integer} / {@link BsonType#INT64 64-bit integer} / {@link BsonType#DOUBLE Double} field
     * are within an interval.
     *
     * @param path The field to be searched.
     * @return A base for a {@link RangeSearchOperator}.
     * @mongodb.atlas.manual atlas-search/range/ range operator
     */
    static RangeSearchOperatorBase<Number> numberRange(final FieldSearchPath path) {
        return numberRange(singleton(path));
    }

    /**
     * Returns a base for a {@link SearchOperator} that tests if the values of
     * BSON {@link BsonType#INT32 32-bit integer} / {@link BsonType#INT64 64-bit integer} / {@link BsonType#DOUBLE Double} fields
     * are within an interval.
     *
     * @param paths Non-empty fields to be searched.
     * @return A base for a {@link RangeSearchOperator}.
     * @mongodb.atlas.manual atlas-search/range/ range operator
     */
    static RangeSearchOperatorBase<Number> numberRange(final Iterable<? extends FieldSearchPath> paths) {
        Iterator<? extends SearchPath> pathIterator = notNull("paths", paths).iterator();
        isTrueArgument("paths must not be empty", pathIterator.hasNext());
        return new RangeConstructibleBsonElement<>("range", new Document("path", combineToBsonValue(pathIterator, true)));
    }

    /**
     * Returns a base for a {@link SearchOperator} that tests if the values of
     * a BSON {@link BsonType#DATE_TIME Date} fields are within an interval.
     *
     * @param path The field to be searched.
     * @return A base for a {@link RangeSearchOperator}.
     * @mongodb.atlas.manual atlas-search/range/ range operator
     */
    static RangeSearchOperatorBase<Instant> dateRange(final FieldSearchPath path) {
        return dateRange(singleton(path));
    }

    /**
     * Returns a base for a {@link SearchOperator} that tests if the values of
     * BSON {@link BsonType#DATE_TIME Date} fields are within an interval.
     *
     * @param paths Non-empty fields to be searched.
     * @return A base for a {@link RangeSearchOperator}.
     * @mongodb.atlas.manual atlas-search/range/ range operator
     */
    static RangeSearchOperatorBase<Instant> dateRange(final Iterable<? extends FieldSearchPath> paths) {
        Iterator<? extends SearchPath> pathIterator = notNull("paths", paths).iterator();
        isTrueArgument("paths must not be empty", pathIterator.hasNext());
        return new RangeConstructibleBsonElement<>("range", new Document("path", combineToBsonValue(pathIterator, true)));
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
     *          new Document("path", SearchPath.fieldPath("fieldName").toValue())));
     * }</pre>
     *
     * @param operator A {@link Bson} representing the required {@link SearchOperator}.
     * @return The requested {@link SearchOperator}.
     */
    static SearchOperator of(final Bson operator) {
        return new SearchConstructibleBson(notNull("operator", operator));
    }
}
