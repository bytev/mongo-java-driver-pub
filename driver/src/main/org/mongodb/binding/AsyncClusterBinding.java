package org.mongodb.binding;

import org.mongodb.MongoFuture;
import org.mongodb.ReadPreference;
import org.mongodb.connection.Cluster;
import org.mongodb.connection.Connection;
import org.mongodb.connection.Server;
import org.mongodb.operation.SingleResultFuture;
import org.mongodb.selector.PrimaryServerSelector;
import org.mongodb.selector.ReadPreferenceServerSelector;
import org.mongodb.selector.ServerSelector;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A simple ReadWriteBinding implementation that supplies write connection sources bound to a possibly different primary each time and a
 * read connection source bound to a possible different server each time.
 *
 * @since 3.0
 */
public class AsyncClusterBinding extends AbstractReferenceCounted implements AsyncReadWriteBinding {
    private final Cluster cluster;
    private final ReadPreference readPreference;
    private final long maxWaitTimeMS;

    public AsyncClusterBinding(final Cluster cluster, final ReadPreference readPreference,
                               final long maxWaitTime, final TimeUnit timeUnit) {
        this.cluster = cluster;
        this.readPreference = readPreference;
        this.maxWaitTimeMS = MILLISECONDS.convert(maxWaitTime, timeUnit);
    }

    @Override
    public AsyncReadWriteBinding retain() {
        super.retain();
        return this;
    }

    @Override
    public ReadPreference getReadPreference() {
        return readPreference;
    }

    @Override
    public MongoFuture<AsyncConnectionSource> getReadConnectionSource() {
        return new SingleResultFuture<AsyncConnectionSource>(new MyConnectionSource(new ReadPreferenceServerSelector(readPreference)));
    }

    @Override
    public MongoFuture<AsyncConnectionSource> getWriteConnectionSource() {
        return new SingleResultFuture<AsyncConnectionSource>(new MyConnectionSource(new PrimaryServerSelector()));
    }

    private final class MyConnectionSource extends AbstractReferenceCounted implements AsyncConnectionSource {
        private final Server server;

        private MyConnectionSource(final ServerSelector serverSelector) {
            this.server = cluster.selectServer(serverSelector, maxWaitTimeMS, MILLISECONDS);
            AsyncClusterBinding.this.retain();
        }

        @Override
        public MongoFuture<Connection> getConnection() {
            return new SingleResultFuture<Connection>(server.getConnection());
        }

        public AsyncConnectionSource retain() {
            super.retain();
            AsyncClusterBinding.this.retain();
            return this;
        }

        @Override
        public void release() {
            super.release();
            AsyncClusterBinding.this.release();
        }
    }
}
