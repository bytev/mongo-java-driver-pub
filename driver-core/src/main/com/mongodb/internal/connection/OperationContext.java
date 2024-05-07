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

import com.mongodb.MongoConnectionPoolClearedException;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ClusterType;
import com.mongodb.connection.ServerDescription;
import com.mongodb.lang.Nullable;
import com.mongodb.selector.ServerSelector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import static java.util.stream.Collectors.toList;

/**
 * <p>This class is not part of the public API and may be removed or changed at any time</p>
 */
public class OperationContext {
    private static final AtomicLong NEXT_ID = new AtomicLong(0);
    private final long id;
    private final ServerDeprioritization serverDeprioritization;

    public OperationContext() {
        id = NEXT_ID.incrementAndGet();
        serverDeprioritization = new ServerDeprioritization();
    }

    public long getId() {
        return id;
    }

    /**
     * @return The same {@link ServerDeprioritization} if called on the same {@link OperationContext}.
     */
    public ServerDeprioritization getServerDeprioritization() {
        return serverDeprioritization;
    }

    public static final class ServerDeprioritization {
        @Nullable
        private ServerAddress candidate;
        private final Set<ServerAddress> deprioritized;
        private final DeprioritizingSelector selector;

        private ServerDeprioritization() {
            candidate = null;
            deprioritized = new HashSet<>();
            selector = new DeprioritizingSelector();
        }

        /**
         * The returned {@link ServerSelector} tries to {@linkplain ServerSelector#select(ClusterDescription) select}
         * only the {@link ServerDescription}s that do not have deprioritized {@link ServerAddress}es.
         * If no such {@link ServerDescription} can be selected, then it selects {@link ClusterDescription#getServerDescriptions()}.
         */
        ServerSelector getServerSelector() {
            return selector;
        }

        void updateCandidate(final ServerAddress serverAddress, final ClusterType clusterType) {
            candidate = isEnabled(clusterType) ? serverAddress : null;
        }

        public void onAttemptFailure(final Throwable failure) {
            if (candidate == null || failure instanceof MongoConnectionPoolClearedException) {
                candidate = null;
                return;
            }
            deprioritized.add(candidate);
        }

        private static boolean isEnabled(final ClusterType clusterType) {
            return clusterType == ClusterType.SHARDED;
        }

        /**
         * {@link ServerSelector} requires thread safety, but that is only because a user may specify
         * {@link com.mongodb.connection.ClusterSettings.Builder#serverSelector(ServerSelector)},
         * which indeed may be used concurrently. {@link DeprioritizingSelector} does not need to be thread-safe.
         */
        private final class DeprioritizingSelector implements ServerSelector {
            private DeprioritizingSelector() {
            }

            @Override
            public List<ServerDescription> select(final ClusterDescription clusterDescription) {
                List<ServerDescription> serverDescriptions = clusterDescription.getServerDescriptions();
                if (!isEnabled(clusterDescription.getType())) {
                    return serverDescriptions;
                } else {
                    List<ServerDescription> nonDeprioritizedServerDescriptions = serverDescriptions
                            .stream()
                            .filter(serverDescription -> !deprioritized.contains(serverDescription.getAddress()))
                            .collect(toList());
                    return nonDeprioritizedServerDescriptions.isEmpty() ? serverDescriptions : nonDeprioritizedServerDescriptions;
                }
            }
        }
    }
}
