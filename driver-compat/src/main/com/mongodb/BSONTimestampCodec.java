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

package com.mongodb;

import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.types.BSONTimestamp;
import org.bson.types.Timestamp;

/**
 * Knows how to encode and decode BSON timestamps.
 *
 * @since 3.0
 */
public class BSONTimestampCodec implements Codec<BSONTimestamp> {
    @Override
    public void encode(final BsonWriter writer, final BSONTimestamp value) {
        writer.writeTimestamp(new Timestamp(value.getTime(), value.getInc()));
    }

    @Override
    public BSONTimestamp decode(final BsonReader reader) {
        Timestamp timestamp = reader.readTimestamp();
        return new BSONTimestamp(timestamp.getTime(), timestamp.getInc());
    }

    @Override
    public Class<BSONTimestamp> getEncoderClass() {
        return BSONTimestamp.class;
    }
}
