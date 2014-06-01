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
import org.bson.types.BsonInt32;

/**
 * A Codec for BsonInt32 instances.
 *
 * @since 3.0
 */
public class BsonInt32Codec implements Codec<BsonInt32> {
    @Override
    public BsonInt32 decode(final BsonReader reader) {
        return new BsonInt32(reader.readInt32());
    }

    @Override
    public void encode(final BsonWriter writer, final BsonInt32 value) {
        writer.writeInt32(value.getValue());
    }

    @Override
    public Class<BsonInt32> getEncoderClass() {
        return BsonInt32.class;
    }
}
