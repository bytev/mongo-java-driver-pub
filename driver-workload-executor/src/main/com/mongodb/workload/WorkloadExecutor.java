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

package com.mongodb.workload;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.unified.Entities;
import com.mongodb.client.unified.UnifiedTest;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriterSettings;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WorkloadExecutor {
    private static volatile boolean terminateLoop;
    private static final CountDownLatch terminationLatch = new CountDownLatch(1);

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: AstrolabeTestRunner <path to workload spec JSON file> <path to results directory>");
            System.exit(1);
        }

        String pathToWorkloadFile = args[0];
        String pathToResultsDirectory = args[1];

        System.out.println("Max memory (GB): " + (Runtime.getRuntime().maxMemory() / 1_073_741_824.0));
        System.out.println("Path to workload file: '" + pathToWorkloadFile + "'");
        System.out.println("Path to results directory: '" + pathToResultsDirectory + "'");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Running shutdown hook");
            terminateLoop = true;
            try {
                if (!terminationLatch.await(1, TimeUnit.MINUTES)) {
                    System.err.println("Terminating after waiting for 1 minute for results to be written");
                } else {
                    System.out.println("Terminating.");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));


        BsonDocument fileDocument;

        try (FileReader reader = new FileReader(pathToWorkloadFile)) {
            fileDocument = new BsonDocumentCodec().decode(new JsonReader(reader), DecoderContext.builder().build());
        }

        System.out.println(fileDocument.toJson(JsonWriterSettings.builder().indent(true).build()));
        System.out.println();

        BsonArray testArray = fileDocument.getArray("tests");
        if (testArray.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one test");
        }
        BsonDocument testDocument = testArray.get(0).asDocument();

        UnifiedTest unifiedTest = new UnifiedTest(fileDocument.getString("schemaVersion").getValue(),
                fileDocument.getArray("runOnRequirements", null),
                fileDocument.getArray("createEntities", new BsonArray()),
                fileDocument.getArray("initialData", new BsonArray()),
                testDocument) {

            @Override
            protected MongoClient createMongoClient(final MongoClientSettings settings) {
                return MongoClients.create(settings);
            }

            @Override
            protected boolean terminateLoop() {
                return terminateLoop;
            }
        };

        try {
            unifiedTest.setUp();
            unifiedTest.shouldPassAllOutcomes();
            Entities entities = unifiedTest.getEntities();

            long iterationCount = -1;
            if (entities.hasIterationCount("iterations")) {
                iterationCount = entities.getIterationCount("iterations");
            }

            long successCount = -1;
            if (entities.hasSuccessCount("successes")) {
                successCount = entities.getSuccessCount("successes");
            }

            BsonArray errorDocuments = null;
            long errorCount = 0;
            if (entities.hasErrorDocuments("errors")) {
                errorDocuments = entities.getErrorDocuments("errors");
                errorCount = errorDocuments.size();
            }

            BsonArray failureDocuments = null;
            long failureCount = 0;
            if (entities.hasFailureDocuments("failures")) {
                failureDocuments = entities.getFailureDocuments("failures");
                failureCount = failureDocuments.size();
            }

            BsonArray eventDocuments = new BsonArray();
            if (entities.hasEvents("events")) {
                eventDocuments = new BsonArray(entities.getEvents("events"));
            }

            BsonDocument eventsDocument = new BsonDocument()
                    .append("events", eventDocuments)
                    .append("errors", errorDocuments == null ? new BsonArray() : errorDocuments)
                    .append("failures", failureDocuments == null ? new BsonArray() : failureDocuments);

            BsonDocument resultsDocument = new BsonDocument()
                    .append("numErrors", new BsonInt64(errorCount))
                    .append("numFailures", new BsonInt64(failureCount))
                    .append("numSuccesses", new BsonInt64(successCount))
                    .append("numIterations", new BsonInt64(iterationCount));

            writeFile(eventsDocument, Paths.get(pathToResultsDirectory, "events.json"));
            writeFile(resultsDocument, Paths.get(pathToResultsDirectory, "results.json"));
        } finally {
            unifiedTest.cleanUp();
            terminationLatch.countDown();
        }
    }

    private static void writeFile(final BsonDocument document, final Path path) throws IOException {
        System.out.println("Writing file: '" + path.toFile().getAbsolutePath());
        Files.deleteIfExists(path);
        String json = document.toJson(JsonWriterSettings.builder().indent(true).build());
        Files.write(path, (json + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
