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

package com.mongodb.client.unified;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import static com.mongodb.ClusterFixture.isDiscoverableReplicaSet;
import static com.mongodb.ClusterFixture.isSharded;
import static com.mongodb.ClusterFixture.serverVersionLessThan;
import static org.junit.Assume.assumeFalse;

public class UnifiedRetryableWritesTest extends UnifiedSyncTest {
    public UnifiedRetryableWritesTest(@SuppressWarnings("unused") final String fileDescription,
                                      @SuppressWarnings("unused") final String testDescription,
                                      final String schemaVersion, final BsonArray runOnRequirements, final BsonArray entitiesArray,
                                      final BsonArray initialData, final BsonDocument definition) {
        super(schemaVersion, runOnRequirements, entitiesArray, initialData, definition);
        customSkips(testDescription);
    }

    public static void customSkips(final String description) {
        if (isSharded() && serverVersionLessThan(5, 0)) {
            assumeFalse(description.contains("succeeds after WriteConcernError"));
            assumeFalse(description.contains("succeeds after retryable writeConcernError"));
        }
        if (isDiscoverableReplicaSet() && serverVersionLessThan(4, 4)) {
            assumeFalse(description.equals("RetryableWriteError label is added based on writeConcernError in pre-4.4 mongod response"));
        }
    }

    @Parameterized.Parameters(name = "{0}: {1}")
    public static Collection<Object[]> data() throws URISyntaxException, IOException {
        return getTestData("unified-test-format/retryable-writes");
    }
}
