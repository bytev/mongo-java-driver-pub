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



package com.mongodb

import spock.lang.Specification
import spock.lang.Subject

import javax.management.ObjectName
import java.lang.management.ManagementFactory

import static java.util.concurrent.TimeUnit.SECONDS

class JMXConnectionPoolListenerSpecification extends Specification {
    private static final String CLUSTER_ID = '42'
    private static final ServerAddress SERVER_ADDRESS = new ServerAddress()

    @Subject
    private final JMXConnectionPoolListener jmxListener = new JMXConnectionPoolListener()

    private final openEvent = new ConnectionPoolOpenedEvent(CLUSTER_ID, SERVER_ADDRESS,
                                                            ConnectionPoolSettings.builder().maxSize(5).maxWaitTime(1, SECONDS).build())

    def 'statistics should reflect values from the provider'() {
        given:
        def event = new ConnectionEvent(CLUSTER_ID, SERVER_ADDRESS)

        when:
        jmxListener.with {
            connectionPoolOpened(openEvent);
            connectionAdded(event)
            connectionCheckedOut(event)
            connectionAdded(event)
            connectionCheckedOut(event)
            connectionCheckedIn(event)
        }

        then:
        ManagementFactory.getPlatformMBeanServer().isRegistered(
                new ObjectName(jmxListener.getMBeanObjectName(CLUSTER_ID, SERVER_ADDRESS)))

        with(jmxListener.getMBean(CLUSTER_ID, SERVER_ADDRESS)) {
            host == SERVER_ADDRESS.host
            port == SERVER_ADDRESS.port
            minSize == 0
            maxSize == 5
            size == 2
            checkedOutCount == 1
            waitQueueSize == 0
        }

        cleanup:
        jmxListener.connectionPoolClosed(new ConnectionPoolEvent(CLUSTER_ID, SERVER_ADDRESS))
    }

    def 'should remove MBean'() {
        given:
        jmxListener.connectionPoolOpened(openEvent);

        when:
        jmxListener.connectionPoolClosed(new ConnectionPoolEvent(CLUSTER_ID, SERVER_ADDRESS))

        then:
        jmxListener.getMBean(CLUSTER_ID, SERVER_ADDRESS) == null
        !ManagementFactory.getPlatformMBeanServer().isRegistered(new ObjectName(jmxListener.getMBeanObjectName(CLUSTER_ID, SERVER_ADDRESS)))
    }

    def 'should create a valid ObjectName for hostname'() {
        given:
        String beanName = jmxListener.getMBeanObjectName(CLUSTER_ID, new ServerAddress('localhost'));

        when:
        ObjectName objectName = new ObjectName(beanName)

        then:
        objectName.toString() == 'org.mongodb.driver:type=ConnectionPool,clusterId=42,host=localhost,port=27017'
    }

    def 'should create a valid ObjectName for ipv4 addresses'() {
        given:
        String beanName = jmxListener.getMBeanObjectName(CLUSTER_ID, new ServerAddress('127.0.0.1'))

        when:
        ObjectName objectName = new ObjectName(beanName)

        then:
        objectName.toString() == 'org.mongodb.driver:type=ConnectionPool,clusterId=42,host=127.0.0.1,port=27017'
    }

    def 'should create a valid ObjectName for ipv6 address'() {
        given:
        String beanName = jmxListener.getMBeanObjectName(CLUSTER_ID, new ServerAddress('[::1]'))

        when:
        ObjectName objectName = new ObjectName(beanName)

        then:
        objectName.toString() == 'org.mongodb.driver:type=ConnectionPool,clusterId=42,host=%3A%3A1,port=27017'
    }

    def 'should create a valid ObjectName when cluster id has a :'() {
        given:
        String beanName = jmxListener.getMBeanObjectName('kd:dk', new ServerAddress())

        when:
        ObjectName objectName = new ObjectName(beanName)

        then:
        objectName.toString() == 'org.mongodb.driver:type=ConnectionPool,clusterId=kd%3Adk,host=127.0.0.1,port=27017'
    }
}