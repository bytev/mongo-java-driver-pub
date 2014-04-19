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

package org.mongodb.binding;

import category.ReplicaSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.DatabaseTestCase;
import org.mongodb.connection.Connection;
import org.mongodb.connection.ServerAddress;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.mongodb.Fixture.getCluster;

@Category(ReplicaSet.class)
public class PinnedBindingTest extends DatabaseTestCase {
    private PinnedBinding binding;

    @Before
    public void setUp() {
        super.setUp();
        binding = new PinnedBinding(getCluster(), 1, SECONDS);
    }

    @After
    public void tearDown() {
        binding.release();
    }

    @Test
    public void shouldPinReadsToSameServer() throws InterruptedException {
        ConnectionSource readConnectionSource = binding.getReadConnectionSource();
        Connection connection = readConnectionSource.getConnection();
        ServerAddress serverAddress = connection.getServerAddress();
        connection.close();
        readConnectionSource.release();

        // there is randomization in the selection, so have to try a bunch of times.
        for (int i = 0; i < 100; i++) {
            readConnectionSource = binding.getReadConnectionSource();
            connection = readConnectionSource.getConnection();
            assertEquals(serverAddress, connection.getServerAddress());
        }

        binding.getWriteConnectionSource();

        readConnectionSource = binding.getReadConnectionSource();
        connection = readConnectionSource.getConnection();
        assertEquals(serverAddress, connection.getServerAddress());
        connection.close();
        readConnectionSource.release();
    }
    @Test
    public void shouldPinReadsToSameConnectionAsAPreviousWrite() throws InterruptedException {
        ConnectionSource writeSource = binding.getWriteConnectionSource();
        Connection writeConnection = writeSource.getConnection();

        ConnectionSource readSource = binding.getReadConnectionSource();
        Connection readConnection = readSource.getConnection();
        assertEquals(writeConnection.getId(), readConnection.getId());

        writeConnection.close();
        readConnection.close();
        writeSource.release();
        readSource.release();
    }
}
