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

import com.mongodb.ClusterFixture;
import com.mongodb.ConnectionString;
import com.mongodb.CursorType;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoNamespace;
import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSUploadStream;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.test.CollectionHelper;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionReadyEvent;
import com.mongodb.internal.connection.ServerHelper;
import com.mongodb.internal.connection.TestCommandListener;
import com.mongodb.internal.connection.TestConnectionPoolListener;
import com.mongodb.test.FlakyTest;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mongodb.ClusterFixture.getConnectionString;
import static com.mongodb.ClusterFixture.isAuthenticated;
import static com.mongodb.ClusterFixture.isDiscoverableReplicaSet;
import static com.mongodb.ClusterFixture.isServerlessTest;
import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static com.mongodb.ClusterFixture.sleep;
import static com.mongodb.client.Fixture.getDefaultDatabaseName;
import static com.mongodb.client.Fixture.getPrimary;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * See
 * <a href="https://github.com/mongodb/specifications/blob/master/source/client-side-operations-timeout/tests/README.rst">Prose Tests</a>.
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class AbstractClientSideOperationsTimeoutProseTest {

    protected static final String GRID_FS_BUCKET_NAME = "db.fs";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    protected MongoNamespace namespace;
    protected MongoNamespace gridFsFileNamespace;
    protected MongoNamespace gridFsChunksNamespace;

    protected CollectionHelper<BsonDocument> collectionHelper;
    private CollectionHelper<BsonDocument> filesCollectionHelper;
    private CollectionHelper<BsonDocument> chunksCollectionHelper;

    protected TestCommandListener commandListener;

    protected abstract MongoClient createMongoClient(MongoClientSettings mongoClientSettings);

    protected abstract GridFSBucket createGridFsBucket(MongoDatabase mongoDatabase, String bucketName);

    protected abstract boolean isAsync();

    @Tag("setsFailPoint")
    @SuppressWarnings("try")
    @FlakyTest(maxAttempts = 3)
    @DisplayName("4. Background Connection Pooling - timeoutMS used for handshake commands")
    public void testBackgroundConnectionPoolingTimeoutMSUsedForHandshakeCommands() {
        assumeTrue(serverVersionAtLeast(4, 4));
        assumeTrue(isAuthenticated());
        assumeFalse(isServerlessTest());

        collectionHelper.runAdminCommand("{"
                + "    configureFailPoint: \"failCommand\","
                + "    mode: {"
                + "        times: 1"
                + "    },"
                + "    data: {"
                + "        failCommands: [\"saslContinue\"],"
                + "        blockConnection: true,"
                + "        blockTimeMS: 150,"
                + "        appName: \"timeoutBackgroundPoolTest\""
                + "    }"
                + "}");

        TestConnectionPoolListener connectionPoolListener = new TestConnectionPoolListener();

        try (MongoClient ignoredClient = createMongoClient(getMongoClientSettingsBuilder()
                .applicationName("timeoutBackgroundPoolTest")
                .applyToConnectionPoolSettings(builder -> {
                    builder.minSize(1);
                    builder.addConnectionPoolListener(connectionPoolListener);
                })
                .timeout(100, TimeUnit.MILLISECONDS))) {

            assertDoesNotThrow(() ->
                    connectionPoolListener.waitForEvents(asList(ConnectionCreatedEvent.class, ConnectionClosedEvent.class),
                            10, TimeUnit.SECONDS));
        }
    }

    @Tag("setsFailPoint")
    @SuppressWarnings("try")
    @FlakyTest(maxAttempts = 3)
    @DisplayName("4. Background Connection Pooling - timeoutMS is refreshed for each handshake command")
    public void testBackgroundConnectionPoolingTimeoutMSIsRefreshedForEachHandshakeCommand() {
        assumeTrue(serverVersionAtLeast(4, 4));
        assumeTrue(isAuthenticated());
        assumeFalse(isServerlessTest());

        collectionHelper.runAdminCommand("{"
                + "    configureFailPoint: \"failCommand\","
                + "    mode: \"alwaysOn\","
                + "    data: {"
                + "        failCommands: [\"hello\", \"isMaster\", \"saslContinue\"],"
                + "        blockConnection: true,"
                + "        blockTimeMS: 150,"
                + "        appName: \"refreshTimeoutBackgroundPoolTest\""
                + "    }"
                + "}");

        TestConnectionPoolListener connectionPoolListener = new TestConnectionPoolListener();

        try (MongoClient ignoredClient = createMongoClient(getMongoClientSettingsBuilder()
                .applicationName("refreshTimeoutBackgroundPoolTest")
                .applyToConnectionPoolSettings(builder -> {
                    builder.minSize(1);
                    builder.addConnectionPoolListener(connectionPoolListener);
                })
                .timeout(250, TimeUnit.MILLISECONDS))) {

            assertDoesNotThrow(() ->
                    connectionPoolListener.waitForEvents(asList(ConnectionCreatedEvent.class, ConnectionReadyEvent.class),
                            10, TimeUnit.SECONDS));
        }
    }

    @Tag("setsFailPoint")
    @FlakyTest(maxAttempts = 3)
    @DisplayName("5. Blocking Iteration Methods - Tailable cursors")
    public void testBlockingIterationMethodsTailableCursor() {
        assumeTrue(serverVersionAtLeast(4, 4));
        assumeFalse(isServerlessTest());

        collectionHelper.create(namespace.getCollectionName(),
                new CreateCollectionOptions().capped(true).sizeInBytes(10 * 1024 * 1024));
        collectionHelper.insertDocuments(singletonList(BsonDocument.parse("{x: 1}")), WriteConcern.MAJORITY);
        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: \"alwaysOn\","
                + "  data: {"
                + "    failCommands: [\"getMore\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: " + 150
                + "  }"
                + "}");

        try (MongoClient client = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(250, TimeUnit.MILLISECONDS))) {
            MongoCollection<Document> collection = client.getDatabase(namespace.getDatabaseName())
                    .getCollection(namespace.getCollectionName());

            try (MongoCursor<Document> cursor = collection.find().cursorType(CursorType.Tailable).cursor()) {
                Document document = assertDoesNotThrow(cursor::next);
                assertEquals(1, document.get("x"));
                assertThrows(MongoOperationTimeoutException.class, cursor::next);
            }

            List<CommandSucceededEvent> events = commandListener.getCommandSucceededEvents();
            assertEquals(1, events.stream().filter(e -> e.getCommandName().equals("find")).count());
            long getMoreCount = events.stream().filter(e -> e.getCommandName().equals("getMore")).count();
            assertTrue(getMoreCount <= 2, "getMoreCount expected to less than or equal to two but was: " +  getMoreCount);
        }
    }

    @Tag("setsFailPoint")
    @FlakyTest(maxAttempts = 3)
    @DisplayName("5. Blocking Iteration Methods - Change Streams")
    public void testBlockingIterationMethodsChangeStream() {
        assumeTrue(serverVersionAtLeast(4, 4));
        assumeTrue(isDiscoverableReplicaSet());
        assumeFalse(isServerlessTest());
        assumeFalse(isAsync()); // Async change stream cursor is non-deterministic for cursor::next

        BsonTimestamp startTime = new BsonTimestamp((int) Instant.now().getEpochSecond(), 0);
        collectionHelper.create(namespace.getCollectionName(), new CreateCollectionOptions());
        sleep(2000);
        collectionHelper.insertDocuments(singletonList(BsonDocument.parse("{x: 1}")), WriteConcern.MAJORITY);

        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: \"alwaysOn\","
                + "  data: {"
                + "    failCommands: [\"getMore\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: " + 150
                + "  }"
                + "}");

        try (MongoClient mongoClient = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(250, TimeUnit.MILLISECONDS))) {

            MongoCollection<Document> collection = mongoClient.getDatabase(namespace.getDatabaseName())
                    .getCollection(namespace.getCollectionName()).withReadPreference(ReadPreference.primary());
            try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = collection.watch(
                    singletonList(Document.parse("{ '$match': {'operationType': 'insert'}}")))
                    .startAtOperationTime(startTime)
                    .fullDocument(FullDocument.UPDATE_LOOKUP)
                    .cursor()) {
                ChangeStreamDocument<Document> document = assertDoesNotThrow(cursor::next);

                Document fullDocument = document.getFullDocument();
                assertNotNull(fullDocument);
                assertEquals(1, fullDocument.get("x"));
                assertThrows(MongoOperationTimeoutException.class, cursor::next);
            }
            List<CommandSucceededEvent> events = commandListener.getCommandSucceededEvents();
            assertEquals(1, events.stream().filter(e -> e.getCommandName().equals("aggregate")).count());
            long getMoreCount = events.stream().filter(e -> e.getCommandName().equals("getMore")).count();
            assertTrue(getMoreCount <= 2, "getMoreCount expected to less than or equal to two but was: " +  getMoreCount);
        }
    }


    @Tag("setsFailPoint")
    @DisplayName("6. GridFS Upload - uploads via openUploadStream can be timed out")
    @Test
    public void testGridFSUploadViaOpenUploadStreamTimeout() {
        assumeTrue(serverVersionAtLeast(4, 4));
        long rtt = ClusterFixture.getPrimaryRTT();

        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: { times: 1 },"
                + "  data: {"
                + "    failCommands: [\"insert\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: " + (rtt + 205)
                + "  }"
                + "}");

        chunksCollectionHelper.create();
        filesCollectionHelper.create();

        try (MongoClient client = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(rtt + 200, TimeUnit.MILLISECONDS))) {
            MongoDatabase database = client.getDatabase(namespace.getDatabaseName());
            GridFSBucket gridFsBucket = createGridFsBucket(database, GRID_FS_BUCKET_NAME);

            try (GridFSUploadStream uploadStream = gridFsBucket.openUploadStream("filename")){
                uploadStream.write(0x12);
                assertThrows(MongoOperationTimeoutException.class, uploadStream::close);
            }
        }
    }

    @Tag("setsFailPoint")
    @DisplayName("6. GridFS Upload - Aborting an upload stream can be timed out")
    @Test
    public void testAbortingGridFsUploadStreamTimeout() throws Throwable {
        assumeTrue(serverVersionAtLeast(4, 4));
        long rtt = ClusterFixture.getPrimaryRTT();

        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: { times: 1 },"
                + "  data: {"
                + "    failCommands: [\"delete\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: " + (rtt + 305)
                + "  }"
                + "}");

        chunksCollectionHelper.create();
        filesCollectionHelper.create();

        try (MongoClient client = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(rtt + 300, TimeUnit.MILLISECONDS))) {
            MongoDatabase database = client.getDatabase(namespace.getDatabaseName());
            GridFSBucket gridFsBucket = createGridFsBucket(database, GRID_FS_BUCKET_NAME).withChunkSizeBytes(2);

            try (GridFSUploadStream uploadStream = gridFsBucket.openUploadStream("filename")){
                uploadStream.write(new byte[]{0x01, 0x02, 0x03, 0x04});
                assertThrows(MongoOperationTimeoutException.class, uploadStream::abort);
            }
        }
    }

    @Tag("setsFailPoint")
    @DisplayName("6. GridFS Download")
    @Test
    public void testGridFsDownloadStreamTimeout() {
        assumeTrue(serverVersionAtLeast(4, 4));
        long rtt = ClusterFixture.getPrimaryRTT();

        chunksCollectionHelper.create();
        filesCollectionHelper.create();

        filesCollectionHelper.insertDocuments(singletonList(BsonDocument.parse(
                "{"
                        + "   _id: {"
                        + "     $oid: \"000000000000000000000005\""
                        + "   },"
                        + "   length: 10,"
                        + "   chunkSize: 4,"
                        + "   uploadDate: {"
                        + "     $date: \"1970-01-01T00:00:00.000Z\""
                        + "   },"
                        + "   md5: \"57d83cd477bfb1ccd975ab33d827a92b\","
                        + "   filename: \"length-10\","
                        + "   contentType: \"application/octet-stream\","
                        + "   aliases: [],"
                        + "   metadata: {}"
                        + "}"
        )), WriteConcern.MAJORITY);
        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: { skip: 1 },"
                + "  data: {"
                + "    failCommands: [\"find\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: " + (rtt + 95)
                + "  }"
                + "}");

        try (MongoClient client = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(rtt + 100, TimeUnit.MILLISECONDS))) {
            MongoDatabase database = client.getDatabase(namespace.getDatabaseName());
            GridFSBucket gridFsBucket = createGridFsBucket(database, GRID_FS_BUCKET_NAME).withChunkSizeBytes(2);

            try (GridFSDownloadStream downloadStream = gridFsBucket.openDownloadStream(new ObjectId("000000000000000000000005"))){
                assertThrows(MongoOperationTimeoutException.class, downloadStream::read);

                List<CommandStartedEvent> events = commandListener.getCommandStartedEvents();
                List<CommandStartedEvent> findCommands = events.stream().filter(e -> e.getCommandName().equals("find")).collect(Collectors.toList());

                assertEquals(2, findCommands.size());
                assertEquals(gridFsFileNamespace.getCollectionName(), findCommands.get(0).getCommand().getString("find").getValue());
                assertEquals(gridFsChunksNamespace.getCollectionName(), findCommands.get(1).getCommand().getString("find").getValue());
            }
        }
    }

    @DisplayName("8. Server Selection 1 / 2")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("test8ServerSelectionArguments")
    public void test8ServerSelection(final String connectionString) {
        assumeFalse(isServerlessTest());
        int timeoutBuffer = 100; // 5 in spec, Java is slower
        // 1. Create a MongoClient
        try (MongoClient mongoClient = createMongoClient(getMongoClientSettingsBuilder()
                .applyConnectionString(new ConnectionString(connectionString)))
        ) {
            long start = System.nanoTime();
            // 2. Using client, execute:
            Throwable throwable = assertThrows(MongoTimeoutException.class, () -> {
                mongoClient.getDatabase("admin").runCommand(new BsonDocument("ping", new BsonInt32(1)));
            });
            // Expect this to fail with a server selection timeout error after no more than 15ms [this is increased]
            long elapsed = msElapsedSince(start);
            assertTrue(throwable.getMessage().contains("while waiting for a server"));
            assertTrue(elapsed < 10 + timeoutBuffer, "Took too long to time out, elapsedMS: " + elapsed);
        }
    }

    @Tag("setsFailPoint")
    @DisplayName("8. Server Selection 2 / 2")
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("test8ServerSelectionHandshakeArguments")
    public void test8ServerSelectionHandshake(final String ignoredTestName, final int timeoutMS, final int serverSelectionTimeoutMS) {
        assumeTrue(serverVersionAtLeast(4, 4));
        assumeTrue(isAuthenticated());
        assumeFalse(isServerlessTest());

        MongoCredential credential = getConnectionString().getCredential();
        assertNotNull(credential);
        assertNull(credential.getAuthenticationMechanism());

        MongoNamespace namespace = generateNamespace();
        collectionHelper = new CollectionHelper<>(new BsonDocumentCodec(), namespace);
        collectionHelper.runAdminCommand("{"
                + "  configureFailPoint: \"failCommand\","
                + "  mode: \"alwaysOn\","
                + "  data: {"
                + "    failCommands: [\"saslContinue\"],"
                + "    blockConnection: true,"
                + "    blockTimeMS: 350"
                + "  }"
                + "}");

        try (MongoClient mongoClient = createMongoClient(getMongoClientSettingsBuilder()
                .timeout(timeoutMS, TimeUnit.MILLISECONDS)
                .applyToClusterSettings(b -> b.serverSelectionTimeout(serverSelectionTimeoutMS, TimeUnit.MILLISECONDS))
                .retryWrites(false))) {

            long start = System.nanoTime();
            assertThrows(MongoOperationTimeoutException.class, () -> {
                mongoClient.getDatabase(namespace.getDatabaseName())
                        .getCollection(namespace.getCollectionName())
                        .insertOne(new Document("x", 1));
            });
            long elapsed = msElapsedSince(start);
            assertTrue(elapsed <= 310, "Took too long to time out, elapsedMS: " + elapsed);
        }
    }

    private static Stream<Arguments> test8ServerSelectionArguments() {
        return Stream.of(
                Arguments.of(Named.of("serverSelectionTimeoutMS honored if timeoutMS is not set",
                        "mongodb://invalid/?serverSelectionTimeoutMS=10")),
                Arguments.of(Named.of("timeoutMS honored for server selection if it's lower than serverSelectionTimeoutMS",
                        "mongodb://invalid/?timeoutMS=200&serverSelectionTimeoutMS=10")),
                Arguments.of(Named.of("serverSelectionTimeoutMS honored for server selection if it's lower than timeoutMS",
                        "mongodb://invalid/?timeoutMS=10&serverSelectionTimeoutMS=200")),
                Arguments.of(Named.of("serverSelectionTimeoutMS honored for server selection if timeoutMS=0",
                        "mongodb://invalid/?timeoutMS=0&serverSelectionTimeoutMS=10"))

        );
    }

    private static Stream<Arguments> test8ServerSelectionHandshakeArguments() {
        return Stream.of(
                Arguments.of("timeoutMS honored for connection handshake commands if it's lower than serverSelectionTimeoutMS", 200, 300),
                Arguments.of("serverSelectionTimeoutMS honored for connection handshake commands if it's lower than timeoutMS", 300, 200)
        );
    }

    protected MongoNamespace generateNamespace() {
        return new MongoNamespace(getDefaultDatabaseName(),
                getClass().getSimpleName() + "_" + COUNTER.incrementAndGet());
    }

    protected MongoClientSettings.Builder getMongoClientSettingsBuilder() {
        commandListener.reset();
        return Fixture.getMongoClientSettingsBuilder()
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.MAJORITY)
                .readPreference(ReadPreference.primary())
                .addCommandListener(commandListener);
    }

    @BeforeEach
    public void setUp() {
        namespace = generateNamespace();
        gridFsFileNamespace = new MongoNamespace(getDefaultDatabaseName(), GRID_FS_BUCKET_NAME + ".files");
        gridFsChunksNamespace = new MongoNamespace(getDefaultDatabaseName(), GRID_FS_BUCKET_NAME + ".chunks");

        collectionHelper = new CollectionHelper<>(new BsonDocumentCodec(), namespace);
        filesCollectionHelper = new CollectionHelper<>(new BsonDocumentCodec(), gridFsFileNamespace);
        chunksCollectionHelper = new CollectionHelper<>(new BsonDocumentCodec(), gridFsChunksNamespace);
        commandListener = new TestCommandListener();
    }

    @AfterEach
    public void tearDown(final TestInfo info) {
        if (collectionHelper != null) {
            if (info.getTags().contains("setsFailPoint") && serverVersionAtLeast(4, 4)) {
                collectionHelper.runAdminCommand("{configureFailPoint: \"failCommand\", mode: \"off\"}");
            }
            CollectionHelper.dropDatabase(getDefaultDatabaseName());

            collectionHelper.drop();
            filesCollectionHelper.drop();
            chunksCollectionHelper.drop();
            try {
                ServerHelper.checkPool(getPrimary());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private MongoClient createMongoClient(final MongoClientSettings.Builder builder) {
        return createMongoClient(builder.build());
    }

   private long msElapsedSince(final long t1) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t1);
    }
}
