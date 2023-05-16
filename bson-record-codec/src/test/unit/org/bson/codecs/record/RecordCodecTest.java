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

package org.bson.codecs.record;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.record.samples.TestRecordWithDeprecatedAnnotations;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonCreatorOnConstructor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonCreatorOnMethod;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonDiscriminatorOnRecord;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonExtraElementsOnAccessor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonExtraElementsOnComponent;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonIdOnAccessor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonIdOnCanonicalConstructor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonIgnoreOnAccessor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonIgnoreOnComponent;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonPropertyOnAccessor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonPropertyOnCanonicalConstructor;
import org.bson.codecs.record.samples.TestRecordWithIllegalBsonRepresentationOnAccessor;
import org.bson.codecs.record.samples.TestRecordWithPojoAnnotations;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RecordCodecTest {

    @Test
    public void testRecordWithDeprecatedAnnotations() {
        var codec = new RecordCodec<>(TestRecordWithDeprecatedAnnotations.class, Bson.DEFAULT_CODEC_REGISTRY);
        var identifier = new ObjectId();
        var testRecord = new TestRecordWithDeprecatedAnnotations("Lucas", 14, List.of("soccer", "basketball"), identifier.toHexString());

        var document = new BsonDocument();
        var writer = new BsonDocumentWriter(document);

        // when
        codec.encode(writer, testRecord, EncoderContext.builder().build());

        // then
        assertEquals(
                new BsonDocument("_id", new BsonObjectId(identifier))
                        .append("name", new BsonString("Lucas"))
                        .append("hobbies", new BsonArray(List.of(new BsonString("soccer"), new BsonString("basketball"))))
                        .append("a", new BsonInt32(14)),
                document);
        assertEquals("_id", document.getFirstKey());

        // when
        var decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        // then
        assertEquals(testRecord, decoded);
    }

    @Test
    public void testRecordWithPojoAnnotations() {
        var codec = new RecordCodec<>(TestRecordWithPojoAnnotations.class, Bson.DEFAULT_CODEC_REGISTRY);
        var identifier = new ObjectId();
        var testRecord = new TestRecordWithPojoAnnotations("Lucas", 14, List.of("soccer", "basketball"), identifier.toHexString());

        var document = new BsonDocument();
        var writer = new BsonDocumentWriter(document);

        // when
        codec.encode(writer, testRecord, EncoderContext.builder().build());

        // then
        assertEquals(
                new BsonDocument("_id", new BsonObjectId(identifier))
                        .append("name", new BsonString("Lucas"))
                        .append("hobbies", new BsonArray(List.of(new BsonString("soccer"), new BsonString("basketball"))))
                        .append("a", new BsonInt32(14)),
                document);
        assertEquals("_id", document.getFirstKey());

        // when
        var decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        // then
        assertEquals(testRecord, decoded);
    }

    @Test
    public void testRecordWithNulls() {
        var codec = new RecordCodec<>(TestRecordWithDeprecatedAnnotations.class, Bson.DEFAULT_CODEC_REGISTRY);
        var identifier = new ObjectId();
        var testRecord = new TestRecordWithDeprecatedAnnotations(null, 14, null, identifier.toHexString());

        var document = new BsonDocument();
        var writer = new BsonDocumentWriter(document);

        // when
        codec.encode(writer, testRecord, EncoderContext.builder().build());

        // then
        assertEquals(
                new BsonDocument("_id", new BsonObjectId(identifier))
                        .append("a", new BsonInt32(14)),
                document);

        // when
        var decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        // then
        assertEquals(testRecord, decoded);
    }

    @Test
    public void testRecordWithExtraData() {
        var codec = new RecordCodec<>(TestRecordWithDeprecatedAnnotations.class, Bson.DEFAULT_CODEC_REGISTRY);
        var identifier = new ObjectId();
        var testRecord = new TestRecordWithDeprecatedAnnotations("Felix", 13, List.of("rugby", "badminton"), identifier.toHexString());

        var document = new BsonDocument("_id", new BsonObjectId(identifier))
                .append("nationality", new BsonString("British"))
                .append("name", new BsonString("Felix"))
                .append("hobbies", new BsonArray(List.of(new BsonString("rugby"), new BsonString("badminton"))))
                .append("a", new BsonInt32(13));

        // when
        var decoded = codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

        // then
        assertEquals(testRecord, decoded);
    }

    @Test
    public void testExceptionsForAnnotationsNotOnRecordComponent() {
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonIdOnAccessor.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonIdOnCanonicalConstructor.class, Bson.DEFAULT_CODEC_REGISTRY));

        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonPropertyOnAccessor.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonPropertyOnCanonicalConstructor.class, Bson.DEFAULT_CODEC_REGISTRY));

        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonRepresentationOnAccessor.class, Bson.DEFAULT_CODEC_REGISTRY));
    }

    @Test
    public void testExceptionsForUnsupportedAnnotations() {
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonDiscriminatorOnRecord.class, Bson.DEFAULT_CODEC_REGISTRY));

        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonCreatorOnConstructor.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonCreatorOnMethod.class, Bson.DEFAULT_CODEC_REGISTRY));

        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonIgnoreOnComponent.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonIgnoreOnAccessor.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonExtraElementsOnComponent.class, Bson.DEFAULT_CODEC_REGISTRY));
        assertThrows(CodecConfigurationException.class, () ->
                new RecordCodec<>(TestRecordWithIllegalBsonExtraElementsOnAccessor.class, Bson.DEFAULT_CODEC_REGISTRY));
    }
}
