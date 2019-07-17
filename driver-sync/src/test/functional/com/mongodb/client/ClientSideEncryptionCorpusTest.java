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

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.model.vault.EncryptOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.UuidRepresentation;
import org.bson.codecs.UuidCodec;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.mongodb.ClusterFixture.isNotAtLeastJava8;
import static com.mongodb.ClusterFixture.serverVersionAtLeast;
import static com.mongodb.client.Fixture.getMongoClientSettings;
import static com.mongodb.client.Fixture.getMongoClientSettingsBuilder;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static util.JsonPoweredTestHelper.getTestDocument;

// See https://github.com/mongodb/specifications/tree/master/source/client-side-encryption/corpus
@RunWith(Parameterized.class)
public class ClientSideEncryptionCorpusTest {
    private final boolean useLocalSchema;
    private MongoClient client;
    private MongoClient autoEncryptingClient;
    private ClientEncryption clientEncryption;

    public ClientSideEncryptionCorpusTest(final boolean useLocalSchema) {
        this.useLocalSchema = useLocalSchema;
    }

    @Before
    public void setUp() throws IOException, URISyntaxException {
        assumeFalse(isNotAtLeastJava8());
        assumeTrue(serverVersionAtLeast(4, 1));
        assumeTrue("Corpus tests disabled",
                System.getProperty("org.mongodb.test.awsAccessKeyId") != null
                        && !System.getProperty("org.mongodb.test.awsAccessKeyId").isEmpty());

        MongoClientSettings clientSettings = getMongoClientSettingsBuilder()
                .codecRegistry(fromRegistries(fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)),
                        MongoClientSettings.getDefaultCodecRegistry())).build();

        // Step 1: create unencrypted client
        client = MongoClients.create(clientSettings);
        MongoDatabase db = client.getDatabase("db");

        // Step 2: Drop and recreate db.coll with schema
        BsonDocument schemaDocument = bsonDocumentFromPath("corpus-schema.json");

        db.getCollection("coll").drop();
        db.runCommand(new BsonDocument("create", new BsonString("coll"))
                .append("validator", new BsonDocument("$jsonSchema", schemaDocument)));

        // Step 3: Drop and create admin.datakeys
        MongoDatabase adminDatabase = client.getDatabase("admin");
        MongoCollection<BsonDocument> dataKeysCollection = adminDatabase.getCollection("datakeys", BsonDocument.class);
        dataKeysCollection.drop();
        dataKeysCollection.insertOne(bsonDocumentFromPath("corpus-key-aws.json"));
        dataKeysCollection.insertOne(bsonDocumentFromPath("corpus-key-local.json"));

        // Step 4: Configure our objects
        Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>();
        Map<String, Object> awsCreds = new HashMap<String, Object>();
        Map<String, Object> localMasterkey = new HashMap<String, Object>();
        awsCreds.put("accessKeyId", System.getProperty("org.mongodb.test.awsAccessKeyId"));
        awsCreds.put("secretAccessKey", System.getProperty("org.mongodb.test.awsSecretAccessKey"));

        byte[] localMasterkeyBytes = Base64.getDecoder().decode("Mng0NCt4ZHVUYUJCa1kxNkVyNUR1QURhZ2h2UzR2d2RrZzh0cFBwM3R6NmdWMDFBM"
                + "UN3YkQ5aXRRMkhGRGdQV09wOGVNYUMxT2k3NjZKelhaQmRCZGJkTXVyZG9uSjFk");
        localMasterkey.put("key", localMasterkeyBytes);
        kmsProviders.put("aws", awsCreds);
        kmsProviders.put("local", localMasterkey);

        HashMap<String, BsonDocument> schemaMap = new HashMap<String, BsonDocument>();
        schemaMap.put("db.coll", schemaDocument);

        AutoEncryptionSettings.Builder autoEncryptionSettingsBuilder = AutoEncryptionSettings.builder()
                .keyVaultNamespace("admin.datakeys")
                .kmsProviders(kmsProviders);

        if (useLocalSchema) {
            autoEncryptionSettingsBuilder.schemaMap(schemaMap);
        }

        clientSettings = getMongoClientSettingsBuilder()
                .codecRegistry(fromRegistries(
                        fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)), MongoClientSettings.getDefaultCodecRegistry()))
                .autoEncryptionSettings(autoEncryptionSettingsBuilder.build())
                .build();
        autoEncryptingClient = MongoClients.create(clientSettings);

        ClientEncryptionSettings clientEncryptionSettings = ClientEncryptionSettings.builder().
                keyVaultMongoClientSettings(getMongoClientSettings()).
                kmsProviders(kmsProviders).
                keyVaultNamespace("admin.datakeys").build();
        clientEncryption = ClientEncryptions.create(clientEncryptionSettings);
    }

    @Test
    public void testCorpus() throws IOException, URISyntaxException {

        // Step 5: Iterate over corpus
        BsonDocument corpus = bsonDocumentFromPath("corpus.json");
        BsonDocument corpusCopied = new BsonDocument();
        for (String field : corpus.keySet()) {
            if (field.equals("_id") || field.equals("altname_aws") || field.equals("altname_local")) {
                corpusCopied.append(field, corpus.get(field));
                continue;
            }

            BsonDocument fieldDocument = corpus.getDocument(field).clone();
            String kms = fieldDocument.getString("kms").getValue();
            String abbreviatedAlgorithName = fieldDocument.getString("algo").getValue();
            String method = fieldDocument.getString("method").getValue();
            String identifier = fieldDocument.getString("identifier").getValue();
            boolean allowed = fieldDocument.getBoolean("allowed").getValue();
            BsonValue value = fieldDocument.get("value");

            byte[] awsKeyId = Base64.getDecoder().decode("AWSAAAAAAAAAAAAAAAAAAA==");
            byte[] localKeyId = Base64.getDecoder().decode("LOCALAAAAAAAAAAAAAAAAA==");

            if (method.equals("auto")) {
                corpusCopied.append(field, corpus.get(field));
                continue;
            }

            if (!method.equals("explicit")) {
                throw new UnsupportedOperationException("Unsupported method: " + method);
            }

            String fullAlgorithmName = "AEAD_AES_256_CBC_HMAC_SHA_512-";
            if (abbreviatedAlgorithName.equals("rand")) {
                fullAlgorithmName += "Random";
            } else if (abbreviatedAlgorithName.equals("det")) {
                fullAlgorithmName += "Deterministic";
            } else {
                throw new UnsupportedOperationException("Unsupported algorithm: " + abbreviatedAlgorithName);
            }

            EncryptOptions opts = new EncryptOptions(fullAlgorithmName);
            if (identifier.equals("id")) {
                if (kms.equals("aws")) {
                    opts.keyId(new BsonBinary(BsonBinarySubType.UUID_STANDARD, awsKeyId));
                } else if (kms.equals("local")) {
                    opts.keyId(new BsonBinary(BsonBinarySubType.UUID_STANDARD, localKeyId));
                } else {
                    throw new UnsupportedOperationException("Unsupported provider: " + kms);
                }
            } else if (identifier.equals("altname")) {
                opts.keyAltName(kms);
            } else {
                throw new UnsupportedOperationException("Unsupported identifier: " + identifier);
            }

            try {
                BsonValue encryptedValue = clientEncryption.encrypt(value, opts);
                fieldDocument.put("value", encryptedValue);
                corpusCopied.append(field, fieldDocument);
            } catch (MongoException e) {
                if (allowed) {
                    throw e;
                }
                corpusCopied.append(field, fieldDocument);
            }
        }

        // Step 6: insert corpusCopied
        MongoCollection<BsonDocument> encryptedCollection = autoEncryptingClient.getDatabase("db")
                .getCollection("coll", BsonDocument.class);
        encryptedCollection.insertOne(corpusCopied);

        // Step 7: check the auto decrypted document
        BsonDocument corpusDecrypted = encryptedCollection.find(new BsonDocument()).first();
        assertEquals(corpus, corpusDecrypted);

        // Step 8: check the document with an unencrypted client
        MongoCollection<BsonDocument> coll = client.getDatabase("db").getCollection("coll", BsonDocument.class);
        BsonDocument corpusEncryptedActual = coll.find(new BsonDocument()).first();
        BsonDocument corpusEncryptedExpected = bsonDocumentFromPath("corpus-encrypted.json");

        for (String field : corpusEncryptedExpected.keySet()) {
            if (field.equals("_id") || field.equals("altname_aws") || field.equals("altname_local")) {
                continue;
            }

            boolean allowed = corpusEncryptedActual.getDocument(field).getBoolean("allowed").getValue();
            String kms = corpusEncryptedActual.getDocument(field).getString("kms").getValue();
            String type = corpusEncryptedActual.getDocument(field).getString("type").getValue();
            String algorithm = corpusEncryptedActual.getDocument(field).getString("algo").getValue();
            BsonValue value = corpusEncryptedActual.getDocument(field).get("value");

            // All deterministic fields are an exact match.
            check(value, kms, type, algorithm, allowed, corpusEncryptedExpected);

            if (allowed) {
                BsonValue decrypted = clientEncryption.decrypt(value.asBinary());
                assertEquals("Values should be equal for field " + field, corpus.getDocument(field).get("value"), decrypted);
            } else {
                assertEquals("Values should be equal for field " + field, corpus.getDocument(field).get("value"),
                        corpusEncryptedActual.getDocument(field).get("value"));
            }
        }
    }

    private static void check(final BsonValue actualValue, final String actualKms, final String actualType,
                              final String actualAlgorithm, final boolean allowed, final BsonDocument expectedDocument) {
        for (String field : expectedDocument.keySet()) {
            if (field.equals("_id") || field.equals("altname_aws") || field.equals("altname_local")) {
                continue;
            }

            BsonDocument subDocument = expectedDocument.getDocument(field);

            String kms = subDocument.getString("kms").getValue();
            String type = subDocument.getString("type").getValue();
            String algorithm = subDocument.getString("algo").getValue();
            BsonValue expectedValue = subDocument.get("value");

            if (kms.equals(actualKms) && type.equals(actualType)) {
                if (actualAlgorithm.equals("det")) {
                    if (algorithm.equals("det")) {
                        assertEquals(expectedValue, actualValue);
                    }
                } else if (actualAlgorithm.equals("rand")) {
                    if (allowed) {
                        assertNotEquals(expectedValue, actualValue);
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported algorithm type: " + actualAlgorithm);
                }
            }
        }
    }

    private static BsonDocument bsonDocumentFromPath(final String path) throws IOException, URISyntaxException {
        return getTestDocument(new File(ClientSideEncryptionTest.class
                .getResource("/client-side-encryption-corpus/" + path).toURI()));
    }

    @Parameterized.Parameters(name = "useLocalSchema: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[]{true}, new Object[]{false});
    }

}
