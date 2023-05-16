/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.reactivestreams.client.unified;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.unified.UnifiedTest;
import com.mongodb.lang.Nullable;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.syncadapter.SyncMongoClient;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import static org.junit.Assume.assumeFalse;

public class LoadBalancerTest extends UnifiedTest {

    public LoadBalancerTest(@SuppressWarnings("unused") final String fileDescription,
                            final String testDescription,
                            final String schemaVersion, @Nullable final BsonArray runOnRequirements, final BsonArray entities,
                            final BsonArray initialData, final BsonDocument definition) {
        super(schemaVersion, runOnRequirements, entities, initialData, definition);
        // Reactive streams driver can't implement these tests because the underlying cursor is closed on error, which
        // breaks assumption in the tests that closing the cursor is something that happens under user control
        assumeFalse(testDescription.equals("pinned connections are not returned after an network error during getMore"));
        assumeFalse(testDescription.equals("pinned connections are not returned to the pool after a non-network error on getMore"));
        // Reactive streams driver can't implement this test because there is no way to tell that a change stream cursor
        // that has not yet received any results has even initiated the change stream
        assumeFalse(testDescription.equals("change streams pin to a connection"));
    }

    @Override
    protected MongoClient createMongoClient(final MongoClientSettings settings) {
        return new SyncMongoClient(MongoClients.create(settings));
    }

    @Parameterized.Parameters(name = "{0}: {1}")
    public static Collection<Object[]> data() throws URISyntaxException, IOException {
        return getTestData("unified-test-format/load-balancers");
    }
}
