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

import com.mongodb.MongoDriverInformation;
import com.mongodb.internal.VisibleForTesting;
import com.mongodb.internal.build.MongoDriverVersion;
import com.mongodb.lang.Nullable;
import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mongodb.assertions.Assertions.isTrueArgument;
import static java.lang.String.format;
import static java.lang.System.getProperty;

/**
 * <p>This class is not part of the public API and may be removed or changed at any time</p>
 */
public final class ClientMetadataHelper {
    private static final String SEPARATOR = "|";

    private static final int MAXIMUM_CLIENT_METADATA_ENCODED_SIZE = 512;

    @VisibleForTesting(otherwise = VisibleForTesting.AccessModifier.PRIVATE)
    static String getOperatingSystemType(final String operatingSystemName) {
        if (nameStartsWith(operatingSystemName, "linux")) {
            return "Linux";
        } else if (nameStartsWith(operatingSystemName, "mac")) {
            return "Darwin";
        } else if (nameStartsWith(operatingSystemName, "windows")) {
            return  "Windows";
        } else if (nameStartsWith(operatingSystemName, "hp-ux", "aix", "irix", "solaris", "sunos")) {
            return "Unix";
        } else {
            return "unknown";
        }
    }

    private static String getOperatingSystemName() {
        return getProperty("os.name", "unknown");
    }

    private static boolean nameStartsWith(final String name, final String... prefixes) {
        for (String prefix : prefixes) {
            if (name.toLowerCase().startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static BsonDocument createClientMetadataDocument(@Nullable final String applicationName,
                                                            @Nullable final MongoDriverInformation mongoDriverInformation) {
        if (applicationName != null) {
            isTrueArgument("applicationName UTF-8 encoding length <= 128",
                    applicationName.getBytes(StandardCharsets.UTF_8).length <= 128);
        }

        // client fields are added in "preservation" order:
        BsonDocument client = new BsonDocument();
        tryWithLimit(client, d -> putAtPath(d, "application.name", applicationName));
        MongoDriverInformation baseDriverInfor = getDriverInformation(null);
        // required fields:
        tryWithLimit(client, d -> {
            putAtPath(d, "driver.name", listToString(baseDriverInfor.getDriverNames()));
            putAtPath(d, "driver.version", listToString(baseDriverInfor.getDriverVersions()));
        });
        tryWithLimit(client, d -> putAtPath(d, "os.type", getOperatingSystemType(getOperatingSystemName())));
        // full driver information:
        MongoDriverInformation fullDriverInfo = getDriverInformation(mongoDriverInformation);
        tryWithLimit(client, d -> {
            putAtPath(d, "driver.name", listToString(fullDriverInfo.getDriverNames()));
            putAtPath(d, "driver.version", listToString(fullDriverInfo.getDriverVersions()));
        });
        // optional fields:
        Environment environment = getEnvironment();
        tryWithLimit(client, d -> putAtPath(d, "env.name", environment.getName()));
        tryWithLimit(client, d -> putAtPath(d, "os.name", getOperatingSystemName()));
        tryWithLimit(client, d -> putAtPath(d, "os.architecture", getProperty("os.arch", "unknown")));
        tryWithLimit(client, d -> putAtPath(d, "os.version", getProperty("os.version", "unknown")));
        tryWithLimit(client, d -> putAtPath(d, "env.timeout_sec", environment.getTimeoutSec()));
        tryWithLimit(client, d -> putAtPath(d, "env.memory_mb", environment.getMemoryMb()));
        tryWithLimit(client, d -> putAtPath(d, "env.region", environment.getRegion()));
        tryWithLimit(client, d -> putAtPath(d, "env.url", environment.getUrl()));
        tryWithLimit(client, d -> putAtPath(d, "platform", listToString(baseDriverInfor.getDriverPlatforms())));
        tryWithLimit(client, d -> putAtPath(d, "platform", listToString(fullDriverInfo.getDriverPlatforms())));

        return client;
    }


    private static void putAtPath(final BsonDocument d, final String path, @Nullable final String value) {
        putAtPath(d, path, new BsonString(value));
    }

    private static void putAtPath(final BsonDocument d, final String path, @Nullable final int value) {
        putAtPath(d, path, new BsonInt32(value));
    }

    /**
     * Assumes valid documents (or not set) on path. No-op if value is null.
     */
    private static void putAtPath(final BsonDocument d, final String path, @Nullable final BsonValue value) {
        if (value == null) {
            return;
        }
        String[] split = path.split("\\.", 2);
        String first = split[0];
        if (split.length == 1) {
            d.append(first, value);
        } else {
            BsonDocument child;
            if (d.containsKey(first)) {
                child = d.getDocument(first);
            } else {
                child = new BsonDocument();
                d.append(first, child);
            }
            String rest = split[1];
            putAtPath(child, rest, value);
        }
    }

    private static void tryWithLimit(final BsonDocument document, final Consumer<BsonDocument> modifier) {
        try {
            BsonDocument temp = document.clone();
            modifier.accept(temp);
            if (!clientMetadataDocumentTooLarge(temp)) {
                modifier.accept(document);
            }
        } catch (Exception e) {
            // do nothing. This could be a SecurityException, or any other issue while building the document
        }
    }

    static boolean clientMetadataDocumentTooLarge(final BsonDocument document) {
        BasicOutputBuffer buffer = new BasicOutputBuffer(MAXIMUM_CLIENT_METADATA_ENCODED_SIZE);
        new BsonDocumentCodec().encode(new BsonBinaryWriter(buffer), document, EncoderContext.builder().build());
        return buffer.getPosition() > MAXIMUM_CLIENT_METADATA_ENCODED_SIZE;
    }

    private enum Environment {
        AWS_LAMBDA("aws.lambda"),
        AZURE_FUNC("azure.func"),
        GCP_FUNC("gcp.func"),
        VERCEL("vercel"),
        UNKNOWN(null);

        @Nullable
        private final String name;

        Environment(@Nullable final String name) {
            this.name = name;
        }

        @Nullable
        public String getName() {
            return name;
        }

        @Nullable
        public Integer getTimeoutSec() {
            switch (this) {
                case GCP_FUNC:
                    return getEnvInteger("FUNCTION_TIMEOUT_SEC");
                default:
                    return null;
            }
        }

        @Nullable
        public Integer getMemoryMb() {
            switch (this) {
                case AWS_LAMBDA:
                    return getEnvInteger("AWS_LAMBDA_FUNCTION_MEMORY_SIZE");
                case GCP_FUNC:
                    return getEnvInteger("FUNCTION_MEMORY_MB");
                default:
                    return null;
            }
        }

        @Nullable
        public String getRegion() {
            switch (this) {
                case AWS_LAMBDA:
                    return System.getenv("AWS_REGION");
                case GCP_FUNC:
                    return System.getenv("FUNCTION_REGION");
                case VERCEL:
                    return System.getenv("VERCEL_REGION");
                default:
                    return null;
            }
        }

        @Nullable
        public String getUrl() {
            switch (this) {
                case VERCEL:
                    return System.getenv("VERCEL_URL");
                default:
                    return null;
            }
        }
    }

    @Nullable
    private static Integer getEnvInteger(final String name) {
        try {
            String value = System.getenv(name);
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Environment getEnvironment() {
        List<Environment> result = new ArrayList<>();
        String awsExecutionEnv = System.getenv("AWS_EXECUTION_ENV");
        if ((awsExecutionEnv != null && awsExecutionEnv.startsWith("AWS_Lambda_"))
                || System.getenv("AWS_LAMBDA_RUNTIME_API") != null) {
            result.add(Environment.AWS_LAMBDA);
        }
        if (System.getenv("FUNCTIONS_WORKER_RUNTIME") != null) {
            result.add(Environment.AZURE_FUNC);
        }
        if (System.getenv("K_SERVICE") != null || System.getenv("FUNCTION_NAME") != null) {
            result.add(Environment.GCP_FUNC);
        }
        if (System.getenv("VERCEL") != null) {
            result.add(Environment.VERCEL);
        }
        return result.size() != 1 ? Environment.UNKNOWN : result.get(0);
    }

    static MongoDriverInformation getDriverInformation(@Nullable final MongoDriverInformation mongoDriverInformation) {
        MongoDriverInformation.Builder builder = mongoDriverInformation != null ? MongoDriverInformation.builder(mongoDriverInformation)
                : MongoDriverInformation.builder();
        return builder
                .driverName(MongoDriverVersion.NAME)
                .driverVersion(MongoDriverVersion.VERSION)
                .driverPlatform(format("Java/%s/%s", getProperty("java.vendor", "unknown-vendor"),
                        getProperty("java.runtime.version", "unknown-version")))
                .build();
    }

    private static String listToString(final List<String> listOfStrings) {
        StringBuilder stringBuilder = new StringBuilder();
        int i = 0;
        for (String val : listOfStrings) {
            if (i > 0) {
                stringBuilder.append(SEPARATOR);
            }
            stringBuilder.append(val);
            i++;
        }
        return stringBuilder.toString();
    }

    private ClientMetadataHelper() {
    }
}
