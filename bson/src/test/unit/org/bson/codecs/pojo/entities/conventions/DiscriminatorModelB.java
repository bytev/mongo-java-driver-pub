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
package org.bson.codecs.pojo.entities.conventions;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.Objects;

@BsonDiscriminator(key = "_t", value = "DiscriminatorModel")
public class DiscriminatorModelB {
    private String stringField;

    public DiscriminatorModelB setStringField(final String stringField) {
        this.stringField = stringField;
        return this;
    }

    public String getStringField() {
        return stringField;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DiscriminatorModelB that = (DiscriminatorModelB) o;
        return Objects.equals(stringField, that.stringField);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(stringField);
    }

    @Override
    public String toString() {
        return "DiscriminatorModelB{stringField='" + stringField + "'}";
    }
}
