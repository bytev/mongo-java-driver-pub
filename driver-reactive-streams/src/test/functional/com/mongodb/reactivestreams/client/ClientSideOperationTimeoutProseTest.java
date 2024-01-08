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

import com.mongodb.MongoClientSettings;
import com.mongodb.client.AbstractClientSideOperationsTimeoutProseTest;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets;
import com.mongodb.reactivestreams.client.syncadapter.SyncGridFSBucket;
import com.mongodb.reactivestreams.client.syncadapter.SyncMongoClient;
import org.junit.jupiter.api.Disabled;


/**
 * See https://github.com/mongodb/specifications/blob/master/source/client-side-operations-timeout/tests/README.rst#prose-tests
 */
public final class ClientSideOperationTimeoutProseTest extends AbstractClientSideOperationsTimeoutProseTest {
    private com.mongodb.reactivestreams.client.MongoClient wrapped;

    @Override
    protected MongoClient createMongoClient(final MongoClientSettings mongoClientSettings) {
        wrapped = MongoClients.create(mongoClientSettings);
        return new SyncMongoClient(wrapped);
    }

    @Override
    protected GridFSBucket createGridFsBucket(final MongoDatabase mongoDatabase, final String bucketName) {
        return new SyncGridFSBucket(GridFSBuckets.create(wrapped.getDatabase(mongoDatabase.getName()), bucketName));
    }

    @Override
    @Disabled("TODO (CSOT) - JAVA-4057")
    public void testGridFSUploadViaOpenUploadStreamTimeout() {
    }

    @Disabled("TODO (CSOT) - JAVA-4057")
    @Override
    public void testAbortingGridFsUploadStreamTimeout() {
    }

    @Disabled("TODO (CSOT) - JAVA-4057")
    @Override
    public void testGridFsDownloadStreamTimeout() {
    }

    @Override
    protected boolean isAsync() {
        return true;
    }
}
