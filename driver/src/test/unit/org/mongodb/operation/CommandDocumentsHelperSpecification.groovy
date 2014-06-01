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

package org.mongodb.operation

import org.bson.types.BsonBoolean
import org.bson.types.BsonDocument
import org.bson.types.BsonInt32
import org.bson.types.BsonNull
import org.bson.types.BsonString
import org.bson.types.Code
import org.mongodb.operation.MapReduce as MR
import spock.lang.Specification

class CommandDocumentsHelperSpecification extends Specification {
    private static final FILTER = new BsonDocument('c', new BsonInt32(1))
    private static final SCOPE = new BsonDocument('p', new BsonInt32(2)).append('q', new BsonString('s'))
    private static final NULL = BsonNull.VALUE

    @SuppressWarnings('DuplicateMapLiteral')
    def 'should convert into correct documents'() {
        given:

        expect:
        BsonDocument document = CommandDocuments.createMapReduce('foo', mapReduce);
        document.get('query') == query
        document.get('sort') == sort
        document.getInt32('limit', new BsonInt32(0)).getValue() == limit
        document.get('finalize') == finalize
        document.get('scope') == scope
        document.getBoolean('jsMode', BsonBoolean.FALSE).getValue() == jsMode
        document.getBoolean('verbose').getValue() == verbose

        where:
        mapReduce                            | query  | sort | limit | finalize      | scope | jsMode | verbose
        new MR(new Code('a'), new Code('b')) | NULL   | NULL | 0     | NULL          | NULL  | false  | false
        new MR(new Code('a'), new Code('b'))
                .filter(FILTER)              | FILTER | NULL | 0     | NULL          | NULL  | false  | false
        new MR(new Code('a'), new Code('b'))
                .finalize(new Code('c'))     | NULL   | NULL | 0     | new Code('c') | NULL  | false  | false
        new MR(new Code('a'), new Code('b'))
                .jsMode()                    | NULL   | NULL | 0     | NULL          | NULL  | true   | false
        new MR(new Code('a'), new Code('b'))
                .verbose()                   | NULL   | NULL | 0     | NULL          | NULL  | false  | true
        new MR(new Code('a'), new Code('b'))
                .scope(SCOPE)                | NULL   | NULL | 0     | NULL          | SCOPE | false  | false
        new MR(new Code('a'), new Code('b'))
                .limit(10)                   | NULL   | NULL | 10    | NULL          | NULL  | false  | false
    }

    @SuppressWarnings('DuplicateMapLiteral')
    def 'should convert output into correct document'() {
        expect:
        CommandDocuments.createMapReduce('foo', mapReduce).get('out') == document

        where:
        output << [
                new MapReduceOutputOptions('foo'),
                new MapReduceOutputOptions('foo').database('bar'),
                new MapReduceOutputOptions('foo').action(MapReduceOutputOptions.Action.MERGE),
                new MapReduceOutputOptions('foo').action(MapReduceOutputOptions.Action.REPLACE),
                new MapReduceOutputOptions('foo').database('bar').action(MapReduceOutputOptions.Action.REDUCE),
                new MapReduceOutputOptions('foo').sharded(),
                new MapReduceOutputOptions('foo').nonAtomic(),
                new MapReduceOutputOptions('foo').database('bar').sharded()
        ]
        mapReduce = new MR(new Code('a'), new Code('b'), output)
        document << [
                new BsonDocument('replace', new BsonString('foo'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('replace', new BsonString('foo'))
                        .append('db', new BsonString('bar'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('merge', new BsonString('foo'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('replace', new BsonString('foo'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('reduce', new BsonString('foo'))
                        .append('db', new BsonString('bar'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('replace', new BsonString('foo'))
                        .append('sharded', BsonBoolean.TRUE)
                        .append('nonAtomic', BsonBoolean.FALSE),
                new BsonDocument('replace', new BsonString('foo'))
                        .append('sharded', BsonBoolean.FALSE)
                        .append('nonAtomic', BsonBoolean.TRUE),
                new BsonDocument('replace', new BsonString('foo'))
                        .append('db', new BsonString('bar'))
                        .append('sharded', BsonBoolean.TRUE)
                        .append('nonAtomic', BsonBoolean.FALSE)
        ]
    }
}
