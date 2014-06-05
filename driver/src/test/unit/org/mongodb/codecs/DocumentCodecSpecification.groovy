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

package org.mongodb.codecs

import org.bson.BsonBinaryReader
import org.bson.BsonBinaryWriter
import org.bson.ByteBufNIO
import org.bson.io.BasicInputBuffer
import org.bson.io.BasicOutputBuffer
import org.bson.types.Binary
import org.bson.types.Code
import org.bson.types.MaxKey
import org.bson.types.MinKey
import org.bson.types.ObjectId
import org.bson.types.RegularExpression
import org.bson.types.Symbol
import org.bson.types.Timestamp
import org.bson.types.Undefined
import org.mongodb.CodeWithScope
import org.mongodb.Document
import spock.lang.Specification

import java.nio.ByteBuffer

import static java.util.Arrays.asList

class DocumentCodecSpecification extends Specification {
    def 'should encode and decode all default types'() {
        given:
        def doc = new Document()
        doc.with {
            put('null', null)
            put('int32', 42)
            put('int64', 52L)
            put('booleanTrue', true)
            put('booleanFalse', false)
            put('date', new Date())
//        put('dbPointer', new DBPointer('foo.bar', new ObjectId()))
            put('double', 62.0 as double)
            put('minKey', new MinKey())
            put('maxKey', new MaxKey())
            put('code', new Code('int i = 0;'))
            put('codeWithScope', new CodeWithScope('int x = y', new Document('y', 1)))
            put('objectId', new ObjectId())
            put('regex', new RegularExpression('^test.*regex.*xyz$', 'i'))
            put('string', 'the fox ...')
            put('symbol', new Symbol('ruby stuff'))
            put('timestamp', new Timestamp(0x12345678, 5))
            put('undefined', new Undefined())
            put('binary', new Binary((byte) 80, [5, 4, 3, 2, 1] as byte[]))
            put('array', asList(1, 1L, true, [1, 2, 3], new Document('a', 1), null))
            put('document', new Document('a', 2))
        }
        when:
        BsonBinaryWriter writer = new BsonBinaryWriter(new BasicOutputBuffer(), false)
        new DocumentCodec().encode(writer, doc)
        BsonBinaryReader reader = new BsonBinaryReader(new BasicInputBuffer(new ByteBufNIO(ByteBuffer.wrap(writer.buffer.toByteArray()))),
                                                       true)
        def decodedDoc = new DocumentCodec().decode(reader)

        then:
        decodedDoc.get('null') == doc.get('null')
        decodedDoc.get('int32') == doc.get('int32')
        decodedDoc.get('int64') == doc.get('int64')
        decodedDoc.get('booleanTrue') == doc.get('booleanTrue')
        decodedDoc.get('booleanFalse') == doc.get('booleanFalse')
        decodedDoc.get('date') == doc.get('date')
//        decodedDoc.get('dbPointer') == doc.get('dbPointer')
        decodedDoc.get('double') == doc.get('double')
        decodedDoc.get('minKey') == doc.get('minKey')
        decodedDoc.get('maxKey') == doc.get('maxKey')
        decodedDoc.get('code') == doc.get('code')
        decodedDoc.get('codeWithScope') == doc.get('codeWithScope')
        decodedDoc.get('objectId') == doc.get('objectId')
        decodedDoc.get('regex') == doc.get('regex')
        decodedDoc.get('string') == doc.get('string')
        decodedDoc.get('symbol') == doc.get('symbol')
        decodedDoc.get('timestamp') == doc.get('timestamp')
        decodedDoc.get('undefined') == doc.get('undefined')
        decodedDoc.get('binary') == doc.get('binary')
        decodedDoc.get('array') == doc.get('array')
        decodedDoc.get('document') == doc.get('document')
    }
}