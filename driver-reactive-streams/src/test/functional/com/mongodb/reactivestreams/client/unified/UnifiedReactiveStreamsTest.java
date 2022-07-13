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

import com.mongodb.ClientEncryptionSettings;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.unified.UnifiedTest;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.lang.Nullable;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets;
import com.mongodb.reactivestreams.client.internal.vault.ClientEncryptionImpl;
import com.mongodb.reactivestreams.client.syncadapter.SyncClientEncryption;
import com.mongodb.reactivestreams.client.syncadapter.SyncGridFSBucket;
import com.mongodb.reactivestreams.client.syncadapter.SyncMongoClient;
import com.mongodb.reactivestreams.client.syncadapter.SyncMongoDatabase;
import org.bson.BsonArray;
import org.bson.BsonDocument;

public abstract class UnifiedReactiveStreamsTest extends UnifiedTest {
    public UnifiedReactiveStreamsTest(@Nullable final String fileDescription, final String schemaVersion, final BsonArray runOnRequirements,
            final BsonArray entitiesArray, final BsonArray initialData, final BsonDocument definition) {
        super(fileDescription, schemaVersion, runOnRequirements, entitiesArray, initialData, definition);
    }

    public UnifiedReactiveStreamsTest(final String schemaVersion, final BsonArray runOnRequirements,
            final BsonArray entitiesArray, final BsonArray initialData, final BsonDocument definition) {
        this(null, schemaVersion, runOnRequirements, entitiesArray, initialData, definition);
    }

    @Override
    protected MongoClient createMongoClient(final MongoClientSettings settings) {
        return new SyncMongoClient(MongoClients.create(settings));
    }

    @Override
    protected GridFSBucket createGridFSBucket(final MongoDatabase database) {
        return new SyncGridFSBucket(GridFSBuckets.create(((SyncMongoDatabase) database).getWrapped()));
    }

    @Override
    protected ClientEncryption createClientEncryption(final MongoClient keyVaultClient, final ClientEncryptionSettings clientEncryptionSettings) {
        return new SyncClientEncryption(new ClientEncryptionImpl(((SyncMongoClient) keyVaultClient).getWrapped(), clientEncryptionSettings));
    }
}
