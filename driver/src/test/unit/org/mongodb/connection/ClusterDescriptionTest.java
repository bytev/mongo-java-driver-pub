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

package org.mongodb.connection;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mongodb.connection.ClusterConnectionMode.MULTIPLE;
import static org.mongodb.connection.ClusterType.REPLICA_SET;
import static org.mongodb.connection.ClusterType.UNKNOWN;
import static org.mongodb.connection.ServerConnectionState.CONNECTED;
import static org.mongodb.connection.ServerConnectionState.CONNECTING;
import static org.mongodb.connection.ServerDescription.MAX_DRIVER_WIRE_VERSION;
import static org.mongodb.connection.ServerDescription.builder;
import static org.mongodb.connection.ServerType.REPLICA_SET_OTHER;
import static org.mongodb.connection.ServerType.REPLICA_SET_PRIMARY;
import static org.mongodb.connection.ServerType.REPLICA_SET_SECONDARY;

public class ClusterDescriptionTest {

    private ServerDescription primary, secondary, otherSecondary, uninitiatedMember, notOkMember;
    private ClusterDescription cluster;

    @Before
    public void setUp() throws IOException {
        Tags tags1 = new Tags("foo", "1").append("bar", "2").append("baz", "1");
        Tags tags2 = new Tags("foo", "1").append("bar", "2").append("baz", "2");
        Tags tags3 = new Tags("foo", "1").append("bar", "3").append("baz", "3");

        primary = builder()
                  .state(CONNECTED).address(new ServerAddress("localhost", 27017)).ok(true)
                  .type(REPLICA_SET_PRIMARY).tags(tags1)
                  .build();

        secondary = builder()
                    .state(CONNECTED).address(new ServerAddress("localhost", 27018)).ok(true)
                    .type(REPLICA_SET_SECONDARY).tags(tags2)
                    .build();

        otherSecondary = builder()
                         .state(CONNECTED).address(new ServerAddress("localhost", 27019)).ok(true)
                         .type(REPLICA_SET_SECONDARY).tags(tags3)
                         .build();
        uninitiatedMember = builder()
                            .state(CONNECTED).address(new ServerAddress("localhost", 27020)).ok(true)
                            .type(REPLICA_SET_OTHER)
                            .build();

        notOkMember = builder().state(CONNECTED).address(new ServerAddress("localhost", 27021)).ok(false)
                               .build();

        List<ServerDescription> nodeList = asList(primary, secondary, otherSecondary, uninitiatedMember, notOkMember);

        cluster = new ClusterDescription(MULTIPLE, REPLICA_SET, nodeList);
    }

    @Test
    public void testMode() {
        ClusterDescription description = new ClusterDescription(MULTIPLE, UNKNOWN, Collections.<ServerDescription>emptyList());
        assertEquals(MULTIPLE, description.getConnectionMode());
    }

    @Test
    public void testAll() {
        ClusterDescription description = new ClusterDescription(MULTIPLE, UNKNOWN, Collections.<ServerDescription>emptyList());
        assertTrue(description.getAll().isEmpty());
        assertEquals(new HashSet<ServerDescription>(asList(primary, secondary, otherSecondary, uninitiatedMember, notOkMember)),
                     cluster.getAll());
    }

    @Test
    public void testAny() throws UnknownHostException {
        assertEquals(asList(primary, secondary, otherSecondary, uninitiatedMember), cluster.getAny());
    }

    @Test
    public void testPrimaryOrSecondary() throws UnknownHostException {
        assertEquals(asList(primary, secondary, otherSecondary), cluster.getAnyPrimaryOrSecondary());
        assertEquals(asList(primary, secondary), cluster.getAnyPrimaryOrSecondary(new Tags("foo", "1").append("bar", "2")));
    }

    @Test
    public void testSortingOfAll() throws UnknownHostException {
        ClusterDescription description =
        new ClusterDescription(MULTIPLE, UNKNOWN, asList(
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27019"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27018"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27017"))
                                                        .build())
        );
        Iterator<ServerDescription> iter = description.getAll().iterator();
        assertEquals(new ServerAddress("loc:27017"), iter.next().getAddress());
        assertEquals(new ServerAddress("loc:27018"), iter.next().getAddress());
        assertEquals(new ServerAddress("loc:27019"), iter.next().getAddress());
    }

    @Test
    public void clusterDescriptionWithAnIncompatibleServerShouldBeIncompatible() throws UnknownHostException {
        ClusterDescription description =
        new ClusterDescription(MULTIPLE, UNKNOWN, asList(
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27019"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTED)
                                                        .ok(true)
                                                        .address(new ServerAddress("loc:27018"))
                                                        .minWireVersion(MAX_DRIVER_WIRE_VERSION + 1)
                                                        .maxWireVersion(MAX_DRIVER_WIRE_VERSION + 1)
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27017"))
                                                        .build())
        );
        assertFalse(description.isCompatibleWithDriver());
    }

    @Test
    public void clusterDescriptionWithCompatibleServerShouldBeCompatible() throws UnknownHostException {
        ClusterDescription description =
        new ClusterDescription(MULTIPLE, UNKNOWN, asList(
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27019"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27018"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27017"))
                                                        .build())
        );
        assertTrue(description.isCompatibleWithDriver());
    }

    @Test
    public void testObjectOverrides() throws UnknownHostException {
        ClusterDescription description =
        new ClusterDescription(MULTIPLE, UNKNOWN, asList(
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27019"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27018"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27017"))
                                                        .build())
        );
        ClusterDescription descriptionTwo =
        new ClusterDescription(MULTIPLE, UNKNOWN, asList(
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27019"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27018"))
                                                        .build(),
                                                        builder()
                                                        .state(CONNECTING)
                                                        .address(new ServerAddress("loc:27017"))
                                                        .build())
        );
        assertEquals(description, descriptionTwo);
        assertEquals(description.hashCode(), descriptionTwo.hashCode());
        assertTrue(description.toString().startsWith("ClusterDescription"));
    }
}