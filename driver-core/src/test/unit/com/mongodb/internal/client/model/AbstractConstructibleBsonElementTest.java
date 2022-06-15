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
package com.mongodb.internal.client.model;

import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AbstractConstructibleBsonElementTest {
    @Test
    void of() {
        BsonDocument value = new BsonDocument("n", new BsonString("v"));
        AbstractConstructibleBsonElement<?> constructible = AbstractConstructibleBsonElement.of("name", value);
        BsonDocument constructibleDoc = constructible.toBsonDocument();
        assertEquals(new BsonDocument("name", value), constructibleDoc);
    }

    @Test
    void newWithAppendedValue() {
        AbstractConstructibleBsonElement<?> constructible = AbstractConstructibleBsonElement.of("name", new BsonDocument("n", new BsonString("v")));
        AbstractConstructibleBsonElement<?> appendedConstructible = constructible.newWithAppendedValue("n2", "v2");
        BsonDocument appendedConstructibleDoc = appendedConstructible.toBsonDocument();
        assertEquals(
                new BsonDocument("name", new BsonDocument("n", new BsonString("v")).append("n2", new BsonString("v2"))),
                appendedConstructibleDoc);
    }

    @Test
    void unmodifiable() {
        AbstractConstructibleBsonElement<?> constructible = AbstractConstructibleBsonElement.of("name", new BsonDocument("n", new BsonString("v")));
        String expected = constructible.toBsonDocument().toJson();
        constructible.newWithAppendedValue("n2", "v2");
        assertEquals(expected, constructible.toBsonDocument().toJson());
    }

    @Test
    void emptyIsImmutable() {
        AbstractConstructibleBsonElement<?> constructible = AbstractConstructibleBsonElement.of("name", new BsonDocument());
        String expected = constructible.toBsonDocument().toJson();
        // here we modify the document produced by `toBsonDocument` and check that it does not affect `constructible`
        constructible.toBsonDocument().getDocument("name").append("n", new BsonString("v"));
        assertEquals(expected, constructible.toBsonDocument().toJson());
    }
}
