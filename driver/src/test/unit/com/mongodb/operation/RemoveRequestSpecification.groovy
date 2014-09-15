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



package com.mongodb.operation

import org.bson.BsonDocument
import org.bson.BsonInt32
import spock.lang.Specification

class RemoveRequestSpecification extends Specification {

    def 'should have correct type'() {
        expect:
        new RemoveRequest(new BsonDocument()).getType() == WriteRequest.Type.REMOVE
    }

    def 'should not allow null criteria'() {
        when:
        new RemoveRequest(null)

        then:
        thrown(IllegalArgumentException)
    }

    def 'should set fields from constructor'() {
        given:
        def criteria = new BsonDocument('_id', new BsonInt32(1))

        when:
        def removeRequest = new RemoveRequest(criteria)

        then:
        removeRequest.criteria == criteria
    }

    def 'multi property should default to true'() {
        expect:
        new RemoveRequest(new BsonDocument()).multi
    }

    def 'should set multi property'() {
        expect:
        !new RemoveRequest(new BsonDocument()).multi(false).isMulti()
    }
}
