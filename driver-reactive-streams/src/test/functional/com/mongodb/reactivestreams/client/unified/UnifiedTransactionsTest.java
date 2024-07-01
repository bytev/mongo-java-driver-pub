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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import static com.mongodb.ClusterFixture.isSharded;
import static com.mongodb.ClusterFixture.serverVersionLessThan;
import static org.junit.Assume.assumeFalse;

public class UnifiedTransactionsTest extends UnifiedReactiveStreamsTest {
    public UnifiedTransactionsTest(@SuppressWarnings("unused") final String fileDescription,
                                   @SuppressWarnings("unused") final String testDescription,
                                   final String schemaVersion, final BsonArray runOnRequirements, final BsonArray entitiesArray,
                                   final BsonArray initialData, final BsonDocument definition) {
        super(schemaVersion, runOnRequirements, entitiesArray, initialData, definition);
        assumeFalse(fileDescription.equals("count"));
        if (serverVersionLessThan(4, 4) && isSharded()) {
            assumeFalse(fileDescription.equals("pin-mongos") && testDescription.equals("distinct"));
            assumeFalse(fileDescription.equals("read-concern") && testDescription.equals("only first distinct includes readConcern"));
            assumeFalse(fileDescription.equals("read-concern") && testDescription.equals("distinct ignores collection readConcern"));
            assumeFalse(fileDescription.equals("reads") && testDescription.equals("distinct"));
        }
    }

    @Parameterized.Parameters(name = "{0}: {1}")
    public static Collection<Object[]> data() throws URISyntaxException, IOException {
        return getTestData("unified-test-format/transactions");
    }
}
