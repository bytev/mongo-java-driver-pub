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

import org.bson.BsonBinaryWriter;
import org.bson.BsonReader;
import org.bson.codecs.Decoder;
import org.mongodb.connection.BufferProvider;
import org.mongodb.connection.ByteBufferOutputBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class DBDecoderAdapter implements Decoder<DBObject> {
    private final DBDecoder decoder;
    private final DBCollection collection;
    private final BufferProvider bufferProvider;

    public DBDecoderAdapter(final DBDecoder decoder, final DBCollection collection, final BufferProvider bufferProvider) {
        this.decoder = decoder;
        this.collection = collection;
        this.bufferProvider = bufferProvider;
    }

    @Override
    public DBObject decode(final BsonReader reader) {
        BsonBinaryWriter binaryWriter = new BsonBinaryWriter(new ByteBufferOutputBuffer(bufferProvider), true);
        try {
            binaryWriter.pipe(reader);
            BufferExposingByteArrayOutputStream byteArrayOutputStream =
                new BufferExposingByteArrayOutputStream(binaryWriter.getBuffer().size());
            binaryWriter.getBuffer().pipe(byteArrayOutputStream);
            return decoder.decode(byteArrayOutputStream.getInternalBytes(), collection);
        } catch (IOException e) {
            // impossible with a byte array output stream
            throw new MongoInternalException("An unlikely IOException thrown.", e);
        } finally {
            binaryWriter.close();
        }
    }

    // Just so we don't have to copy the buffer
    private static class BufferExposingByteArrayOutputStream extends ByteArrayOutputStream {
        BufferExposingByteArrayOutputStream(final int size) {
            super(size);
        }

        byte[] getInternalBytes() {
            return buf;
        }
    }
}
