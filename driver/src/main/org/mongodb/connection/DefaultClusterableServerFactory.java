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

import org.mongodb.MongoCredential;
import org.mongodb.event.ConnectionListener;
import org.mongodb.event.ConnectionPoolListener;

import java.util.Collections;
import java.util.List;

class DefaultClusterableServerFactory implements ClusterableServerFactory {
    private final String clusterId;
    private final ServerSettings settings;
    private final ConnectionListener connectionListener;
    private final ConnectionProviderFactory connectionProviderFactory;
    private final StreamFactory heartbeatStreamFactory;

    public DefaultClusterableServerFactory(final String clusterId, final ServerSettings settings,
                                           final ConnectionPoolSettings connectionPoolSettings,
                                           final StreamFactory streamFactory,
                                           final StreamFactory heartbeatStreamFactory,
                                           final List<MongoCredential> credentialList,
                                           final ConnectionListener connectionListener,
                                           final ConnectionPoolListener connectionPoolListener) {
        this.clusterId = clusterId;
        this.settings = settings;
        this.connectionListener = connectionListener;
        this.connectionProviderFactory = new PooledConnectionProviderFactory(clusterId,
                                                                             connectionPoolSettings,
                                                                             streamFactory,
                                                                             credentialList,
                                                                             connectionListener,
                                                                             connectionPoolListener);
        this.heartbeatStreamFactory = heartbeatStreamFactory;
    }

    @Override
    public ClusterableServer create(final ServerAddress serverAddress) {
        return new DefaultServer(serverAddress, settings, clusterId, connectionProviderFactory.create(serverAddress),
                                 new InternalStreamConnectionFactory(clusterId, heartbeatStreamFactory,
                                                                     Collections.<MongoCredential>emptyList(), connectionListener));
    }
}
