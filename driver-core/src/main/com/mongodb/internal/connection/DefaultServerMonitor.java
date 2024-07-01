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

package com.mongodb.internal.connection;

import com.mongodb.MongoInterruptedException;
import com.mongodb.MongoNamespace;
import com.mongodb.MongoSocketException;
import com.mongodb.ServerApi;
import com.mongodb.annotations.ThreadSafe;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ServerDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.connection.ServerSettings;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import com.mongodb.internal.diagnostics.logging.Logger;
import com.mongodb.internal.diagnostics.logging.Loggers;
import com.mongodb.internal.inject.Provider;
import com.mongodb.internal.session.SessionContext;
import com.mongodb.internal.validator.NoOpFieldNameValidator;
import com.mongodb.lang.Nullable;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.types.ObjectId;

import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.mongodb.MongoNamespace.COMMAND_COLLECTION_NAME;
import static com.mongodb.ReadPreference.primary;
import static com.mongodb.assertions.Assertions.assertNotNull;
import static com.mongodb.assertions.Assertions.fail;
import static com.mongodb.assertions.Assertions.notNull;
import static com.mongodb.connection.ServerType.UNKNOWN;
import static com.mongodb.internal.Locks.checkedWithLock;
import static com.mongodb.internal.Locks.withLock;
import static com.mongodb.internal.connection.CommandHelper.HELLO;
import static com.mongodb.internal.connection.CommandHelper.LEGACY_HELLO;
import static com.mongodb.internal.connection.CommandHelper.executeCommand;
import static com.mongodb.internal.connection.DescriptionHelper.createServerDescription;
import static com.mongodb.internal.connection.ServerDescriptionHelper.unknownConnectingServerDescription;
import static com.mongodb.internal.event.EventListenerHelper.singleServerMonitorListener;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

@ThreadSafe
class DefaultServerMonitor implements ServerMonitor {

    private static final Logger LOGGER = Loggers.getLogger("cluster");

    private final ServerId serverId;
    private final ServerMonitorListener serverMonitorListener;
    private final Provider<SdamServerDescriptionManager> sdamProvider;
    private final InternalConnectionFactory internalConnectionFactory;
    private final ClusterConnectionMode clusterConnectionMode;
    @Nullable
    private final ServerApi serverApi;
    private final boolean isFunctionAsAServiceEnvironment;
    private final ServerSettings serverSettings;
    private final ServerMonitor monitor;
    /**
     * Must be guarded by {@link #lock}.
     */
    @Nullable
    private RoundTripTimeMonitor roundTripTimeMonitor;
    private final ExponentiallyWeightedMovingAverage averageRoundTripTime = new ExponentiallyWeightedMovingAverage(0.2);
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private volatile boolean isClosed;

    DefaultServerMonitor(final ServerId serverId, final ServerSettings serverSettings,
            final InternalConnectionFactory internalConnectionFactory,
                         final ClusterConnectionMode clusterConnectionMode,
                         @Nullable final ServerApi serverApi,
                         final boolean isFunctionAsAServiceEnvironment,
                         final Provider<SdamServerDescriptionManager> sdamProvider) {
        this.serverSettings = notNull("serverSettings", serverSettings);
        this.serverId = notNull("serverId", serverId);
        this.serverMonitorListener = singleServerMonitorListener(serverSettings);
        this.internalConnectionFactory = notNull("internalConnectionFactory", internalConnectionFactory);
        this.clusterConnectionMode = notNull("clusterConnectionMode", clusterConnectionMode);
        this.serverApi = serverApi;
        this.isFunctionAsAServiceEnvironment = isFunctionAsAServiceEnvironment;
        this.sdamProvider = sdamProvider;
        monitor = new ServerMonitor();
        roundTripTimeMonitor = null;
        isClosed = false;
    }

    @Override
    public void start() {
        monitor.start();
    }

    private void ensureRoundTripTimeMonitorStarted() {
        withLock(lock, () -> {
            if (!isClosed && roundTripTimeMonitor == null) {
                roundTripTimeMonitor = new RoundTripTimeMonitor();
                roundTripTimeMonitor.start();
            }
        });
    }

    @Override
    public void connect() {
        withLock(lock, condition::signal);
    }

    @Override
    @SuppressWarnings("try")
    public void close() {
        withLock(lock, () -> {
            isClosed = true;
            //noinspection EmptyTryBlock
            try (ServerMonitor ignoredAutoClosed = monitor;
                RoundTripTimeMonitor ignoredAutoClose2 = roundTripTimeMonitor) {
                // we are automatically closing resources here
            }
        });
    }

    @Override
    public void cancelCurrentCheck() {
        monitor.cancelCurrentCheck();
    }

    class ServerMonitor extends Thread implements AutoCloseable {
        private volatile InternalConnection connection = null;
        private volatile boolean currentCheckCancelled;

        ServerMonitor() {
            super("cluster-" + serverId.getClusterId() + "-" + serverId.getAddress());
            setDaemon(true);
        }

        @Override
        public void close() {
            interrupt();
            InternalConnection connection = this.connection;
            if (connection != null) {
                connection.close();
            }
        }

        @Override
        public void run() {
            ServerDescription currentServerDescription = unknownConnectingServerDescription(serverId, null);
            try {
                while (!isClosed) {
                    ServerDescription previousServerDescription = currentServerDescription;
                    currentServerDescription = lookupServerDescription(currentServerDescription);
                    boolean shouldStreamResponses = shouldStreamResponses(currentServerDescription);
                    if (shouldStreamResponses) {
                        ensureRoundTripTimeMonitorStarted();
                    }

                    if (isClosed) {
                        continue;
                    }

                    if (currentCheckCancelled) {
                        waitForNext();
                        currentCheckCancelled = false;
                        continue;
                    }

                    logStateChange(previousServerDescription, currentServerDescription);
                    sdamProvider.get().update(currentServerDescription);

                    if ((shouldStreamResponses && currentServerDescription.getType() != UNKNOWN)
                            || (connection != null && connection.hasMoreToCome())
                            || (currentServerDescription.getException() instanceof MongoSocketException
                            && previousServerDescription.getType() != UNKNOWN)) {
                        continue;
                    }
                    waitForNext();
                }
            } catch (InterruptedException | MongoInterruptedException closed) {
                // stop the monitor
            } catch (RuntimeException e) {
                LOGGER.error(format("Server monitor for %s exiting with exception", serverId), e);
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }

        private ServerDescription lookupServerDescription(final ServerDescription currentServerDescription) {
            try {
                if (connection == null || connection.isClosed()) {
                    currentCheckCancelled = false;
                    InternalConnection newConnection = internalConnectionFactory.create(serverId);
                    newConnection.open();
                    connection = newConnection;
                    averageRoundTripTime.addSample(connection.getInitialServerDescription().getRoundTripTimeNanos());
                    return connection.getInitialServerDescription();
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(format("Checking status of %s", serverId.getAddress()));
                }
                boolean shouldStreamResponses = shouldStreamResponses(currentServerDescription);
                serverMonitorListener.serverHearbeatStarted(new ServerHeartbeatStartedEvent(
                        connection.getDescription().getConnectionId(), shouldStreamResponses));

                long start = System.nanoTime();
                try {
                    SessionContext sessionContext = NoOpSessionContext.INSTANCE;
                    if (!connection.hasMoreToCome()) {
                        BsonDocument helloDocument = new BsonDocument(getHandshakeCommandName(currentServerDescription), new BsonInt32(1))
                                .append("helloOk", BsonBoolean.TRUE);
                        if (shouldStreamResponses) {
                            helloDocument.append("topologyVersion", assertNotNull(currentServerDescription.getTopologyVersion()).asDocument());
                            helloDocument.append("maxAwaitTimeMS", new BsonInt64(serverSettings.getHeartbeatFrequency(MILLISECONDS)));
                        }

                        connection.send(createCommandMessage(helloDocument, connection, currentServerDescription), new BsonDocumentCodec(),
                                sessionContext);
                    }

                    BsonDocument helloResult;
                    if (shouldStreamResponses) {
                        helloResult = connection.receive(new BsonDocumentCodec(), sessionContext,
                                Math.toIntExact(serverSettings.getHeartbeatFrequency(MILLISECONDS)));
                    } else {
                        helloResult = connection.receive(new BsonDocumentCodec(), sessionContext);
                    }

                    long elapsedTimeNanos = System.nanoTime() - start;
                    if (!shouldStreamResponses) {
                        averageRoundTripTime.addSample(elapsedTimeNanos);
                    }
                    serverMonitorListener.serverHeartbeatSucceeded(
                            new ServerHeartbeatSucceededEvent(connection.getDescription().getConnectionId(), helloResult,
                                    elapsedTimeNanos, shouldStreamResponses));

                    return createServerDescription(serverId.getAddress(), helloResult, averageRoundTripTime.getAverage());
                } catch (Exception e) {
                    serverMonitorListener.serverHeartbeatFailed(
                            new ServerHeartbeatFailedEvent(connection.getDescription().getConnectionId(), System.nanoTime() - start,
                                    shouldStreamResponses, e));
                    throw e;
                }
            } catch (Throwable t) {
                averageRoundTripTime.reset();
                InternalConnection localConnection = withLock(lock, () -> {
                    InternalConnection result = connection;
                    connection = null;
                    return result;
                });
                if (localConnection != null) {
                    localConnection.close();
                }
                return unknownConnectingServerDescription(serverId, t);
            }
        }

        private boolean shouldStreamResponses(final ServerDescription currentServerDescription) {
            boolean serverSupportsStreaming = currentServerDescription.getTopologyVersion() != null;
            switch (serverSettings.getServerMonitoringMode()) {
                case STREAM: {
                    return serverSupportsStreaming;
                }
                case POLL: {
                    return false;
                }
                case AUTO: {
                    return !isFunctionAsAServiceEnvironment && serverSupportsStreaming;
                }
                default: {
                    throw fail();
                }
            }
        }

        private CommandMessage createCommandMessage(final BsonDocument command, final InternalConnection connection,
                                                    final ServerDescription currentServerDescription) {
            return new CommandMessage(new MongoNamespace("admin", COMMAND_COLLECTION_NAME), command,
                    new NoOpFieldNameValidator(), primary(),
                    MessageSettings.builder()
                            .maxWireVersion(connection.getDescription().getMaxWireVersion())
                            .build(),
                    shouldStreamResponses(currentServerDescription), clusterConnectionMode, serverApi);
        }

        private void logStateChange(final ServerDescription previousServerDescription,
                                    final ServerDescription currentServerDescription) {
            if (shouldLogStageChange(previousServerDescription, currentServerDescription)) {
                if (currentServerDescription.getException() != null) {
                    LOGGER.info(format("Exception in monitor thread while connecting to server %s", serverId.getAddress()),
                            assertNotNull(currentServerDescription.getException()));
                } else {
                    LOGGER.info(format("Monitor thread successfully connected to server with description %s", currentServerDescription));
                }
            }
        }

        private void waitForNext() throws InterruptedException {
            long timeRemaining = waitForSignalOrTimeout();
            if (timeRemaining > 0) {
                long timeWaiting = serverSettings.getHeartbeatFrequency(NANOSECONDS) - timeRemaining;
                long minimumNanosToWait = serverSettings.getMinHeartbeatFrequency(NANOSECONDS);
                if (timeWaiting < minimumNanosToWait) {
                    long millisToSleep = MILLISECONDS.convert(minimumNanosToWait - timeWaiting, NANOSECONDS);
                    if (millisToSleep > 0) {
                        Thread.sleep(millisToSleep);
                    }
                }
            }
        }

        private long waitForSignalOrTimeout() throws InterruptedException {
            return checkedWithLock(lock, () -> condition.awaitNanos(serverSettings.getHeartbeatFrequency(NANOSECONDS)));
        }

        public void cancelCurrentCheck() {
            InternalConnection localConnection = withLock(lock, () -> {
                if (connection != null && !currentCheckCancelled) {
                    InternalConnection result = connection;
                    currentCheckCancelled = true;
                    return result;
                }
                return null;
            });
            if (localConnection != null) {
                localConnection.close();
            }
        }
    }

    static boolean shouldLogStageChange(final ServerDescription previous, final ServerDescription current) {

        if (previous.isOk() != current.isOk()) {
            return true;
        }
        if (!previous.getAddress().equals(current.getAddress())) {
            return true;
        }
        String previousCanonicalAddress = previous.getCanonicalAddress();
        if (previousCanonicalAddress != null
                ? !previousCanonicalAddress.equals(current.getCanonicalAddress()) : current.getCanonicalAddress() != null) {
            return true;
        }
        if (!previous.getHosts().equals(current.getHosts())) {
            return true;
        }
        if (!previous.getArbiters().equals(current.getArbiters())) {
            return true;
        }
        if (!previous.getPassives().equals(current.getPassives())) {
            return true;
        }
        String previousPrimary = previous.getPrimary();
        if (previousPrimary != null ? !previousPrimary.equals(current.getPrimary()) : current.getPrimary() != null) {
            return true;
        }
        String previousSetName = previous.getSetName();
        if (previousSetName != null ? !previousSetName.equals(current.getSetName()) : current.getSetName() != null) {
            return true;
        }
        if (previous.getState() != current.getState()) {
            return true;
        }
        if (!previous.getTagSet().equals(current.getTagSet())) {
            return true;
        }
        if (previous.getType() != current.getType()) {
            return true;
        }
        if (previous.getMaxWireVersion() != current.getMaxWireVersion()) {
            return true;
        }
        ObjectId previousElectionId = previous.getElectionId();
        if (previousElectionId != null
                    ? !previousElectionId.equals(current.getElectionId()) : current.getElectionId() != null) {
            return true;
        }
        Integer setVersion = previous.getSetVersion();
        if (setVersion != null
                    ? !setVersion.equals(current.getSetVersion()) : current.getSetVersion() != null) {
            return true;
        }

        // Compare class equality and message as exceptions rarely override equals
        Throwable previousException = previous.getException();
        Throwable currentException = current.getException();
        Class<?> thisExceptionClass = previousException != null ? previousException.getClass() : null;
        Class<?> thatExceptionClass = currentException != null ? currentException.getClass() : null;
        if (!Objects.equals(thisExceptionClass, thatExceptionClass)) {
            return true;
        }

        String thisExceptionMessage = previousException != null ? previousException.getMessage() : null;
        String thatExceptionMessage = currentException != null ? currentException.getMessage() : null;
        if (!Objects.equals(thisExceptionMessage, thatExceptionMessage)) {
            return true;
        }

        return false;
    }


    private class RoundTripTimeMonitor extends Thread implements AutoCloseable {
        private volatile InternalConnection connection = null;

        RoundTripTimeMonitor() {
            super("cluster-rtt-" + serverId.getClusterId() + "-" + serverId.getAddress());
            setDaemon(true);
        }

        @Override
        public void close() {
            interrupt();
            InternalConnection connection = this.connection;
            if (connection != null) {
                connection.close();
            }
        }

        @Override
        public void run() {
            try {
                while (!isClosed) {
                    try {
                        if (connection == null) {
                            initialize();
                        } else {
                            pingServer(connection);
                        }
                    } catch (Throwable t) {
                        if (connection != null) {
                            connection.close();
                            connection = null;
                        }
                    }
                    waitForNext();
                }
            } catch (InterruptedException closed) {
                // stop the monitor
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        }

        private void initialize() {
            connection = null;
            connection = internalConnectionFactory.create(serverId);
            connection.open();
            averageRoundTripTime.addSample(connection.getInitialServerDescription().getRoundTripTimeNanos());
        }

        private void pingServer(final InternalConnection connection) {
            long start = System.nanoTime();
            executeCommand("admin",
                    new BsonDocument(getHandshakeCommandName(connection.getInitialServerDescription()), new BsonInt32(1)),
                    clusterConnectionMode, serverApi, connection);
            long elapsedTimeNanos = System.nanoTime() - start;
            averageRoundTripTime.addSample(elapsedTimeNanos);
        }
    }

    private void waitForNext() throws InterruptedException {
        Thread.sleep(serverSettings.getHeartbeatFrequency(MILLISECONDS));
    }

    private String getHandshakeCommandName(final ServerDescription serverDescription) {
        return serverDescription.isHelloOk() ? HELLO : LEGACY_HELLO;
    }
}
