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

package org.mongodb.codecs;

import org.bson.BSONReader;
import org.bson.BSONType;
import org.bson.BSONWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({ "unchecked", "rawtypes"})
public class ListCodec implements Codec<List> {
    private final CodecRegistry registry;
    private final BsonTypeClassMap bsonTypeClassMap;

    public ListCodec(final CodecRegistry registry, final BsonTypeClassMap bsonTypeClassMap) {
        this.registry = registry;
        this.bsonTypeClassMap = bsonTypeClassMap;
    }

    @Override
    public List decode(final BSONReader reader) {
        reader.readStartArray();
        List list = new ArrayList();
        while (reader.readBSONType() != BSONType.END_OF_DOCUMENT) {
            Object value;
            if (reader.getCurrentBSONType() == BSONType.NULL) {
                reader.readNull();
                value = null;
            } else {
                value = registry.get(bsonTypeClassMap.get(reader.getCurrentBSONType())).decode(reader);
            }
            list.add(value);
        }
        reader.readEndArray();
        return list;
    }

    @Override
    public void encode(final BSONWriter writer, final List list) {
        writer.writeStartArray();
        for (final Object value : list) {
            if (value == null) {
                writer.writeNull();
            } else {
                Codec codec = registry.get(value.getClass());
                codec.encode(writer, value);
            }
        }
        writer.writeEndArray();
    }

    @Override
    public Class<List> getEncoderClass() {
        return List.class;
    }
}
