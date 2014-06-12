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

package org.bson.codecs;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.types.Binary;
import org.bson.types.BsonBinary;

/**
 * A Codec for the BSON Binary type.
 *
 * @since 3.0
 */
public class BinaryCodec implements Codec<Binary> {
    @Override
    public void encode(final BsonWriter writer, final Binary value) {
        writer.writeBinaryData(new BsonBinary(value.getType(), value.getData()));
    }

    @Override
    public Binary decode(final BsonReader reader) {
        BsonBinary bsonBinary = reader.readBinaryData();
        return new Binary(bsonBinary.getType(), bsonBinary.getData());
    }

    @Override
    public Class<Binary> getEncoderClass() {
        return Binary.class;
    }
}
