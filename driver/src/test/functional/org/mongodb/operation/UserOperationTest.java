/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
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

package org.mongodb.operation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.DatabaseTestCase;
import org.mongodb.Document;
import org.mongodb.MongoException;
import org.mongodb.MongoNamespace;
import org.mongodb.binding.ClusterBinding;
import org.mongodb.binding.ReadWriteBinding;
import org.mongodb.binding.WriteBinding;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.connection.Cluster;
import org.mongodb.connection.ClusterSettings;
import org.mongodb.connection.ConnectionPoolSettings;
import org.mongodb.connection.DefaultClusterFactory;
import org.mongodb.connection.ServerSettings;
import org.mongodb.connection.SocketSettings;
import org.mongodb.connection.SocketStreamFactory;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mongodb.Fixture.getBinding;
import static org.mongodb.Fixture.getPrimary;
import static org.mongodb.Fixture.getSSLSettings;
import static org.mongodb.Fixture.isAuthenticated;
import static org.mongodb.MongoCredential.createMongoCRCredential;
import static org.mongodb.ReadPreference.primary;
import static org.mongodb.WriteConcern.ACKNOWLEDGED;

// This test is here because the assertion is conditional on auth being enabled, and there"s no way to do that in Spock
@SuppressWarnings("unchecked")
public class UserOperationTest extends DatabaseTestCase {
    private User readOnlyUser;

    @Before
    public void setUp() {
        super.setUp();
        readOnlyUser = new User(createMongoCRCredential("jeff", getDatabaseName(), "123".toCharArray()), true);
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void readOnlyUserShouldNotBeAbleToWrite() throws Exception {
        assumeTrue(isAuthenticated());

        // given:
        new CreateUserOperation(readOnlyUser).execute(getBinding());
        Cluster cluster = createCluster(readOnlyUser);
        ReadWriteBinding binding = new ClusterBinding(cluster, primary(), 1, SECONDS);

        // when:
        try {
            new InsertOperation<Document>(collection.getNamespace(), true, ACKNOWLEDGED,
                                          asList(new InsertRequest<Document>(new Document())),
                                          new DocumentCodec())
            .execute(binding);
            fail("should have thrown");
        } catch (MongoException e) {
            // all good
        } finally {
            // cleanup:
            new DropUserOperation(getDatabaseName(), readOnlyUser.getCredential().getUserName()).execute(getBinding());
            cluster.close();
        }
    }

    @Test
    public void readWriteAdminUserShouldBeAbleToWriteToADifferentDatabase() throws InterruptedException {
        assumeTrue(isAuthenticated());

        // given
        User adminUser = new User(createMongoCRCredential("jeff-rw-admin", "admin", "123".toCharArray()), false);
        new CreateUserOperation(adminUser).execute(getBinding());

        Cluster cluster = createCluster(adminUser);
        WriteBinding binding = new ClusterBinding(cluster, primary(), 1, SECONDS);

        try {
            // when
            new InsertOperation<Document>(new MongoNamespace(getDatabaseName(), getCollectionName()),
                                          true, ACKNOWLEDGED,
                                          asList(new InsertRequest<Document>(new Document())),
                                          new DocumentCodec()).execute(binding);
            // then
            assertEquals(1L, (long) new CountOperation(new MongoNamespace(getDatabaseName(), getCollectionName()), new Find()
            )
                                    .execute(getBinding()));
        } finally {
            // cleanup
            new DropUserOperation("admin", adminUser.getCredential().getUserName()).execute(getBinding());
            cluster.close();
        }
    }

    @Test
    public void readOnlyAdminUserShouldNotBeAbleToWriteToADatabase() throws InterruptedException {
        assumeTrue(isAuthenticated());
        // given
        User adminUser = new User(createMongoCRCredential("jeff-ro-admin", "admin", "123".toCharArray()), true);
        new CreateUserOperation(adminUser).execute(getBinding());

        Cluster cluster = createCluster(adminUser);
        WriteBinding binding = new ClusterBinding(cluster, primary(), 1, SECONDS);

        try {
            // when
            new InsertOperation<Document>(new MongoNamespace(getDatabaseName(), getCollectionName()), true, ACKNOWLEDGED,
                                          asList(new InsertRequest<Document>(new Document())),
                                          new DocumentCodec()).execute(binding);
            fail("Should have thrown");
        } catch (MongoException e) {
            // all good
        } finally {
            // cleanup
            new DropUserOperation("admin", adminUser.getCredential().getUserName()).execute(getBinding());
            cluster.close();
        }
    }

    @Test
    public void readOnlyAdminUserShouldBeAbleToReadFromADifferentDatabase() throws InterruptedException {
        assumeTrue(isAuthenticated());

        // given
        User adminUser = new User(createMongoCRCredential("jeff-ro-admin", "admin", "123".toCharArray()), true);
        new CreateUserOperation(adminUser).execute(getBinding());

        Cluster cluster = createCluster(adminUser);
        ReadWriteBinding binding = new ClusterBinding(cluster, primary(), 1, SECONDS);
        try {
            // when
            long result = new CountOperation(new MongoNamespace(getDatabaseName(), getCollectionName()),
                                             new Find()
            )
                          .execute(binding);
            // then
            assertEquals(0, result);
        } finally {
            // cleanup
            new DropUserOperation("admin", adminUser.getCredential().getUserName()).execute(getBinding());
            cluster.close();
        }
    }


    private Cluster createCluster(final User user) throws InterruptedException {
        return new DefaultClusterFactory().create(ClusterSettings.builder().hosts(asList(getPrimary())).build(),
                                                  ServerSettings.builder().build(),
                                                  ConnectionPoolSettings.builder().maxSize(1).maxWaitQueueSize(1).build(),
                                                  new SocketStreamFactory(SocketSettings.builder().build(), getSSLSettings()),
                                                  new SocketStreamFactory(SocketSettings.builder().build(), getSSLSettings()),
                                                  asList(user.getCredential()),
                                                  null, null, null);
    }

}
