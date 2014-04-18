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

import org.junit.experimental.categories.Category;
import category.Async
import org.mongodb.Document
import org.mongodb.Fixture
import org.mongodb.FunctionalSpecification
import org.mongodb.Index
import org.mongodb.MongoExecutionTimeoutException
import org.mongodb.ReadPreference
import org.mongodb.codecs.DocumentCodec

import static java.util.Arrays.asList
import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue
import static org.mongodb.Fixture.disableMaxTimeFailPoint
import static org.mongodb.Fixture.enableMaxTimeFailPoint
import static org.mongodb.Fixture.getAsyncBinding
import static org.mongodb.Fixture.getBinding
import static org.mongodb.Fixture.isSharded
import static org.mongodb.Fixture.serverVersionAtLeast

class QueryOperationSpecification extends FunctionalSpecification {

    @Override
    def setup() {
        collection.insert(new Document())
    }

    def 'should throw execution timeout exception from execute'() {
        assumeFalse(isSharded())
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)))

        given:
        def find = new Find().maxTime(1, SECONDS)
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        enableMaxTimeFailPoint()

        when:
        queryOperation.execute(getBinding())

        then:
        thrown(MongoExecutionTimeoutException)

        cleanup:
        disableMaxTimeFailPoint()
    }

    @Category(Async)
    def 'should throw execution timeout exception from executeAsync'() {
        assumeFalse(isSharded())
        assumeTrue(serverVersionAtLeast(asList(2, 5, 3)))

        given:
        def find = new Find().maxTime(1, SECONDS)
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        enableMaxTimeFailPoint()

        when:
        queryOperation.executeAsync(getAsyncBinding()).get();

        then:
        thrown(MongoExecutionTimeoutException)

        cleanup:
        disableMaxTimeFailPoint()
    }
    
    
    def '$max should limit items returned'() {
        given:
        for (i in 1..100) {
            collection.insert(new Document('x', 'y').append('count', i))
        }
        collection.tools().createIndexes(asList(Index.builder().addKey('count').build()))
        def count = 0;
        def find = new Find()
        find.getOptions().max(new Document('count', 10))
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        when:
        queryOperation.execute(getBinding()).each {
            count++ 
        }
        
        then:
        count == 10
    }
    
    def '$min should limit items returned'() {
        given:
        collection.find().remove();
        for (i in 1..100) {
            collection.insert(new Document('x', 'y').append('count', i))
        }
        collection.tools().createIndexes(asList(Index.builder().addKey('count').build()))
        def count = 0;
        def find = new Find()
        find.getOptions().min(new Document('count', 10))
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        when:
        queryOperation.execute(getBinding()).each {
            count++ 
        }
        
        then:
        count == 91
    }
    
    def '$maxScan should limit items returned'() {
        given:
        for (i in 1..100) {
            collection.insert(new Document('x', 'y'))
        }
        def count = 0;
        def find = new Find()
        find.getOptions().maxScan(34)
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        when:
        queryOperation.execute(getBinding()).each {
            count++ 
        }
        
        then:
        count == 34
    }

    def '$returnKey should limit properties returned'() {
        given:
        for (i in 1..100) {
            collection.insert(new Document('x', 'y'))
        }
        def idFound = false;
        def find = new Find()
        find.getOptions().returnKey()
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        when:
        queryOperation.execute(getBinding()).each {
            idFound |= it['_id'] != null
        }
        
        then:
        !idFound
    }
     def '$showDiskLoc should return disk locations'() {
        given:
        for (i in 1..100) {
            collection.insert(new Document('x', 'y'))
        }
        def found = true;
        def find = new Find()
        find.getOptions().showDiskLoc()
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())
        when:
        queryOperation.execute(getBinding()).each {
            found &= it['$diskLoc'] != null
        }
        
        then:
        found
    }

    def 'should read from a secondary'() {
        assumeTrue(Fixture.isDiscoverableReplicaSet())
        def find = new Find().readPreference(ReadPreference.secondary())
        def queryOperation = new QueryOperation<Document>(getNamespace(), find, new DocumentCodec(), new DocumentCodec())

        expect:
        queryOperation.execute(getBinding()) != null // if it didn't throw, the query was executed
    }
}
