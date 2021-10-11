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

package com.mongodb.reactivestreams.client;

import com.mongodb.ConnectionString;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.mongodb.ClusterFixture.TIMEOUT_DURATION;
import static com.mongodb.client.ConnectivityTestHelper.LEGACY_HELLO_COMMAND;
import static com.mongodb.reactivestreams.client.Fixture.getMongoClientSettingsBuilder;

public class ConnectivityTest {
    // the test succeeds if no exception is thrown, and fail otherwise
    @ParameterizedTest(name = "{1}")
    @MethodSource("com.mongodb.client.ConnectivityTestHelper#getConnectivityTestArguments")
    public void testConnectivity(final ConnectionString connectionString, @SuppressWarnings("unused") final List<String> hosts) {
        try (MongoClient client = MongoClients.create(getMongoClientSettingsBuilder(connectionString).build())) {
            // test that a command that doesn't require auth completes normally
            Mono.from(client.getDatabase("admin").runCommand(LEGACY_HELLO_COMMAND)).block(TIMEOUT_DURATION);

            // test that a command that requires auth completes normally
            Mono.from(client.getDatabase("test").getCollection("test").estimatedDocumentCount()).block(TIMEOUT_DURATION);
        }
    }
}
