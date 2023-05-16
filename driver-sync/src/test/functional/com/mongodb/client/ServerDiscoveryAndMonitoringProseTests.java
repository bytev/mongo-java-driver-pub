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

package com.mongodb.client;

import com.mongodb.Block;
import com.mongodb.ClusterFixture;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoInterruptedException;
import com.mongodb.connection.ServerSettings;
import com.mongodb.diagnostics.logging.Logger;
import com.mongodb.diagnostics.logging.Loggers;
import com.mongodb.event.ConnectionPoolClearedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolReadyEvent;
import com.mongodb.event.ServerDescriptionChangedEvent;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerListener;
import com.mongodb.event.ServerMonitorListener;
import com.mongodb.internal.Timeout;
import com.mongodb.lang.Nullable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import static com.mongodb.ClusterFixture.configureFailPoint;
import static com.mongodb.ClusterFixture.disableFailPoint;
import static com.mongodb.ClusterFixture.isServerlessTest;
import static com.mongodb.ClusterFixture.isStandalone;
import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static com.mongodb.client.Fixture.getMongoClientSettingsBuilder;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.bson.BsonDocument.parse;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.fail;

/**
 * See
 * <a href="https://github.com/mongodb/specifications/blob/master/source/server-discovery-and-monitoring/server-discovery-and-monitoring-tests.rst">Server Discovery And Monitoring—Test Plan</a>
 * and
 * <a href="https://github.com/mongodb/specifications/tree/master/source/server-discovery-and-monitoring/tests#prose-tests">Prose Tests</a>.
 */
public class ServerDiscoveryAndMonitoringProseTests {
    private static final Logger LOGGER = Loggers.getLogger(ServerDiscoveryAndMonitoringProseTests.class.getSimpleName());
    private static final long TEST_WAIT_TIMEOUT_MILLIS = SECONDS.toMillis(5);

    static final String HELLO = "hello";
    static final String LEGACY_HELLO = "isMaster";

    @Test
    @SuppressWarnings("try")
    public void testHeartbeatFrequency() throws InterruptedException {
        assumeFalse(isServerlessTest());

        CountDownLatch latch = new CountDownLatch(5);
        MongoClientSettings settings = getMongoClientSettingsBuilder()
                                       .applyToServerSettings(new Block<ServerSettings.Builder>() {
                                           @Override
                                           public void apply(final ServerSettings.Builder builder) {
                                               builder.heartbeatFrequency(50, TimeUnit.MILLISECONDS);
                                               builder.addServerMonitorListener(new ServerMonitorListener() {
                                                   @Override
                                                   public void serverHeartbeatSucceeded(final ServerHeartbeatSucceededEvent event) {
                                                       latch.countDown();
                                                   }
                                               });
                                           }
                                       }).build();

        try (MongoClient ignored = MongoClients.create(settings)) {
            assertTrue("Took longer than expected to reach expected number of hearbeats",
                       latch.await(500, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testRTTUpdates() throws InterruptedException {
        assumeTrue(isStandalone());
        assumeTrue(serverVersionAtLeast(4, 4));

        List<ServerDescriptionChangedEvent> events = synchronizedList(new ArrayList<>());
        MongoClientSettings settings = getMongoClientSettingsBuilder()
                                       .applicationName("streamingRttTest")
                                       .applyToServerSettings(new Block<ServerSettings.Builder>() {
                                           @Override
                                           public void apply(final ServerSettings.Builder builder) {
                                               builder.heartbeatFrequency(50, TimeUnit.MILLISECONDS);
                                               builder.addServerListener(new ServerListener() {
                                                   @Override
                                                   public void serverDescriptionChanged(final ServerDescriptionChangedEvent event) {
                                                       events.add(event);
                                                   }
                                               });
                                           }
                                       }).build();
        try (MongoClient client = MongoClients.create(settings)) {
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            Thread.sleep(250);
            assertTrue(events.size() >= 1);
            events.forEach(event ->
                           assertTrue(event.getNewDescription().getRoundTripTimeNanos() > 0));

            configureFailPoint(parse(format("{"
                                     + "configureFailPoint: \"failCommand\","
                                     + "mode: {times: 1000},"
                                     + " data: {"
                                     + "   failCommands: [\"%s\", \"%s\"],"
                                     + "   blockConnection: true,"
                                     + "   blockTimeMS: 100,"
                                     + "   appName: \"streamingRttTest\""
                                     + "  }"
                                     + "}", LEGACY_HELLO, HELLO)));

            long startTime = System.currentTimeMillis();
            while (true) {
                long rttMillis = NANOSECONDS.toMillis(client.getClusterDescription().getServerDescriptions().get(0)
                                                      .getRoundTripTimeNanos());
                if (rttMillis > 50) {
                    break;
                }
                assertFalse(System.currentTimeMillis() - startTime > 1000);
                //noinspection BusyWait
                Thread.sleep(50);
            }

        } finally {
            disableFailPoint("failCommand");
        }
    }

    /**
     * See
     * <a href="https://github.com/mongodb/specifications/blob/master/source/server-discovery-and-monitoring/server-discovery-and-monitoring-tests.rst#connection-pool-management">Connection Pool Management</a>.
     */
    @Test
    @SuppressWarnings("try")
    public void testConnectionPoolManagement() throws InterruptedException {
        assumeTrue(serverVersionAtLeast(4, 3));
        BlockingQueue<Object> events = new SynchronousQueue<>(true);
        ServerMonitorListener serverMonitorListener = new ServerMonitorListener() {
            @Override
            public void serverHeartbeatSucceeded(final ServerHeartbeatSucceededEvent event) {
                put(events, event);
            }

            @Override
            public void serverHeartbeatFailed(final ServerHeartbeatFailedEvent event) {
                put(events, event);
            }
        };
        ConnectionPoolListener connectionPoolListener = new ConnectionPoolListener() {
            @Override
            public void connectionPoolReady(final ConnectionPoolReadyEvent event) {
                put(events, event);
            }

            @Override
            public void connectionPoolCleared(final ConnectionPoolClearedEvent event) {
                put(events, event);
            }
        };
        String appName = "SDAMPoolManagementTest";
        MongoClientSettings clientSettings = getMongoClientSettingsBuilder()
                .applicationName(appName)
                .applyToClusterSettings(ClusterFixture::setDirectConnection)
                .applyToServerSettings(builder -> builder
                        .heartbeatFrequency(100, TimeUnit.MILLISECONDS)
                        .addServerMonitorListener(serverMonitorListener))
                .applyToConnectionPoolSettings(builder -> builder
                        .addConnectionPoolListener(connectionPoolListener))
                .build();
        try (MongoClient unused = MongoClients.create(clientSettings)) {
            /* Note that ServerHeartbeatSucceededEvent type is sometimes allowed but never required.
             * This is because DefaultServerMonitor does not send such events in situations when a server check happens as part
             * of a connection handshake. */
            assertPoll(events, ServerHeartbeatSucceededEvent.class, ConnectionPoolReadyEvent.class);
            configureFailPoint(new BsonDocument()
                    .append("configureFailPoint", new BsonString("failCommand"))
                    .append("mode", new BsonDocument()
                            .append("times", new BsonInt32(2)))
                    .append("data", new BsonDocument()
                            .append("failCommands", new BsonArray(asList(new BsonString("isMaster"), new BsonString("hello"))))
                            .append("errorCode", new BsonInt32(1234))
                            .append("appName", new BsonString(appName))));
            assertPoll(events, ServerHeartbeatSucceededEvent.class, ServerHeartbeatFailedEvent.class);
            assertPoll(events, null, ConnectionPoolClearedEvent.class);
            assertPoll(events, ServerHeartbeatSucceededEvent.class, ConnectionPoolReadyEvent.class);
        } finally {
            disableFailPoint("failCommand");
        }
    }

    private static void assertPoll(final BlockingQueue<?> queue, @Nullable final Class<?> allowed, final Class<?> required)
            throws InterruptedException {
        assertPoll(queue, allowed, required, Timeout.startNow(TEST_WAIT_TIMEOUT_MILLIS, MILLISECONDS));
    }

    private static void assertPoll(final BlockingQueue<?> queue, @Nullable final Class<?> allowed, final Class<?> required,
                                   final Timeout timeout) throws InterruptedException {
        while (true) {
            Object element;
            if (timeout.isImmediate()) {
                element = queue.poll();
            } else if (timeout.isInfinite()) {
                element = queue.take();
            } else {
                element = queue.poll(timeout.remaining(NANOSECONDS), NANOSECONDS);
            }
            if (element != null) {
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Polled " + element.toString());
                }
                Class<?> elementClass = element.getClass();
                if (required.isAssignableFrom(elementClass)) {
                    return;
                } else {
                    assertTrue(String.format("allowed %s, required %s, actual %s", allowed, required, elementClass),
                            allowed != null && allowed.isAssignableFrom(elementClass));
                }
            }
            if (timeout.expired()) {
                fail("required " + required);
            }
        }
    }

    private static <E> void put(final BlockingQueue<E> q, final E e) {
        try {
            q.put(e);
        } catch (InterruptedException t) {
            throw new MongoInterruptedException(null, t);
        }
    }
}
