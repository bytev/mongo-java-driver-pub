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
package com.mongodb.internal;

import com.mongodb.MongoClientException;
import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.internal.function.CheckedFunction;
import com.mongodb.internal.function.CheckedSupplier;
import com.mongodb.internal.time.StartTime;
import com.mongodb.internal.time.Timeout;
import com.mongodb.lang.Nullable;
import com.mongodb.session.ClientSession;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.mongodb.assertions.Assertions.assertNotNull;
import static com.mongodb.assertions.Assertions.assertNull;
import static com.mongodb.assertions.Assertions.isTrue;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Timeout Context.
 *
 * <p>The context for handling timeouts in relation to the Client Side Operation Timeout specification.</p>
 */
public class TimeoutContext {

    private final boolean isMaintenanceContext;
    private final TimeoutSettings timeoutSettings;

    @Nullable
    private Timeout timeout;
    @Nullable
    private Timeout computedServerSelectionTimeout;
    private long minRoundTripTimeMS = 0;

    @Nullable
    private Long maxTimeMSOverride = null;

    public static MongoOperationTimeoutException createMongoRoundTripTimeoutException() {
        return createMongoTimeoutException("Remaining timeoutMS is less than the server's minimum round trip time.");
    }

    public static MongoOperationTimeoutException createMongoTimeoutException(final String message) {
        return new MongoOperationTimeoutException(message);
    }

    public static void throwMongoTimeoutException(final String message) {
        throw new MongoOperationTimeoutException(message);
    }

    public static MongoOperationTimeoutException createMongoTimeoutException(final Throwable cause) {
        return createMongoTimeoutException("Operation timed out: " + cause.getMessage(), cause);
    }

    public static MongoOperationTimeoutException createMongoTimeoutException(final String message, final Throwable cause) {
        if (cause instanceof MongoOperationTimeoutException) {
            return (MongoOperationTimeoutException) cause;
        }
        return new MongoOperationTimeoutException(message, cause);
    }

    public static TimeoutContext createMaintenanceTimeoutContext(final TimeoutSettings timeoutSettings) {
        return new TimeoutContext(true, timeoutSettings, calculateTimeout(timeoutSettings.getTimeoutMS()));
    }

    public static TimeoutContext createTimeoutContext(final ClientSession session, final TimeoutSettings timeoutSettings) {
        TimeoutContext sessionTimeoutContext = session.getTimeoutContext();

        if (sessionTimeoutContext != null) {
            TimeoutSettings sessionTimeoutSettings = sessionTimeoutContext.timeoutSettings;
            if (timeoutSettings.getGenerationId() > sessionTimeoutSettings.getGenerationId()) {
                throw new MongoClientException("Cannot change the timeoutMS during a transaction.");
            }

            // Check for any legacy operation timeouts
            if (sessionTimeoutSettings.getTimeoutMS() == null) {
                if (timeoutSettings.getMaxTimeMS() != 0) {
                    sessionTimeoutSettings = sessionTimeoutSettings.withMaxTimeMS(timeoutSettings.getMaxTimeMS());
                }
                if (timeoutSettings.getMaxAwaitTimeMS() != 0) {
                    sessionTimeoutSettings = sessionTimeoutSettings.withMaxAwaitTimeMS(timeoutSettings.getMaxAwaitTimeMS());
                }
                if (timeoutSettings.getMaxCommitTimeMS() != null) {
                    sessionTimeoutSettings = sessionTimeoutSettings.withMaxCommitMS(timeoutSettings.getMaxCommitTimeMS());
                }
                return new TimeoutContext(sessionTimeoutSettings);
            }
            return sessionTimeoutContext;
        }
       return new TimeoutContext(timeoutSettings);
    }

    public TimeoutContext(final TimeoutSettings timeoutSettings) {
        this(false, timeoutSettings, calculateTimeout(timeoutSettings.getTimeoutMS()));
    }

    public TimeoutContext(final TimeoutSettings timeoutSettings, @Nullable final Timeout timeout) {
        this(false, timeoutSettings, timeout);
    }

    TimeoutContext(final boolean isMaintenanceContext, final TimeoutSettings timeoutSettings, @Nullable final Timeout timeout) {
        this.isMaintenanceContext = isMaintenanceContext;
        this.timeoutSettings = timeoutSettings;
        this.timeout = timeout;
    }

    /**
     * Allows for the differentiation between users explicitly setting a global operation timeout via {@code timeoutMS}.
     *
     * @return true if a timeout has been set.
     */
    public boolean hasTimeoutMS() {
        return timeoutSettings.getTimeoutMS() != null;
    }

    /**
     * Checks the expiry of the timeout.
     *
     * @return true if the timeout has been set and it has expired
     */
    public boolean hasExpired() {
        // TODO-CSOT remove this method (do not inline, but use lambdas)
        return Timeout.nullAsInfinite(timeout).run(NANOSECONDS, () -> false, (ns) -> false, () -> true);
    }

    /**
     * Sets the recent min round trip time
     * @param minRoundTripTimeMS the min round trip time
     * @return this
     */
    public TimeoutContext minRoundTripTimeMS(final long minRoundTripTimeMS) {
        isTrue("'minRoundTripTimeMS' must be a positive number", minRoundTripTimeMS >= 0);
        this.minRoundTripTimeMS = minRoundTripTimeMS;
        return this;
    }

    @Nullable
    public Timeout timeoutIncludingRoundTrip() {
        return timeout == null ? null : timeout.shortenBy(minRoundTripTimeMS, MILLISECONDS);
    }

    /**
     * Returns the remaining {@code timeoutMS} if set or the {@code alternativeTimeoutMS}.
     *
     * @param alternativeTimeoutMS the alternative timeout.
     * @return timeout to use.
     */
    public long timeoutOrAlternative(final long alternativeTimeoutMS) {
        if (timeout == null) {
            return alternativeTimeoutMS;
        } else {
            return timeout.run(MILLISECONDS,
                    () -> 0L,
                    (ms) -> ms,
                    () -> {
                        throw createMongoTimeoutException("The operation timeout has expired.");
                    });
        }
    }

    public TimeoutSettings getTimeoutSettings() {
        return timeoutSettings;
    }

    public long getMaxAwaitTimeMS() {
        return timeoutSettings.getMaxAwaitTimeMS();
    }

    public Timeout getMaxTimeMSTimeout() {
        if (maxTimeMSOverride != null) {
            return new SingleUseTimeout(maxTimeMSOverride);
        } else {
            long maxTimeMS = timeoutSettings.getMaxTimeMS();
            return timeout != null
                    ? timeout.shortenBy(minRoundTripTimeMS, MILLISECONDS)
                    : new SingleUseTimeout(maxTimeMS);
        }
    }

    // TODO (CSOT) JAVA-5385 used only by tests?
    public long getMaxTimeMS() {
        return getMaxTimeMSTimeout().run(MILLISECONDS,
                () -> 0L,
                (ms) -> ms,
                () -> {
                    throw createMongoRoundTripTimeoutException();
                });
    }

    public void resetToDefaultMaxTime() {
        this.maxTimeMSOverride = null;
    }

    public void setMaxTimeOverride(final long maxTimeMS) {
        this.maxTimeMSOverride = maxTimeMS;
    }

    public Long getMaxCommitTimeMS() {
        Long maxCommitTimeMS = timeoutSettings.getMaxCommitTimeMS();
        return timeoutOrAlternative(maxCommitTimeMS != null ? maxCommitTimeMS : 0);
    }

    public long getReadTimeoutMS() {
        return timeoutOrAlternative(timeoutSettings.getReadTimeoutMS());
    }

    public long getWriteTimeoutMS() {
        return timeoutOrAlternative(0);
    }

    public Timeout createConnectTimeoutMs() {
        // null timeout treated as infinite will be later than the other

        return Timeout.earliest(
                Timeout.expiresInWithZeroAsInfinite(getTimeoutSettings().getConnectTimeoutMS(), MILLISECONDS),
                Timeout.nullAsInfinite(timeout));
    }

    /**
     * A timeout that begins at the moment that it is used.
     */
    static class SingleUseTimeout implements Timeout {
        @Nullable
        private final Long nanos;
        private boolean used = false;

        public SingleUseTimeout(final long ms) {
            if (ms == 0) {
                nanos = null;
            } else {
                nanos = NANOSECONDS.convert(ms, MILLISECONDS);
            }
        }

        @Override
        public Timeout shortenBy(final long amount, final TimeUnit timeUnit) {
            throw new AssertionError("Single-use timeout must not be shortened");
        }

        @Override
        public <T, E extends Exception> T checkedRun(final TimeUnit timeUnit, final CheckedSupplier<T, E> onInfinite,
                final CheckedFunction<Long, T, E> onHasRemaining, final CheckedSupplier<T, E> onExpired) throws E {
            if (used) {
                throw new AssertionError("Single-use timeout must not be used multiple times");
            }
            used = true;
            if (this.nanos == null) {
                return onInfinite.get();
            }
            long remaining = timeUnit.convert(nanos, NANOSECONDS);
            if (remaining == 0) {
                return onExpired.get();
            } else {
                return onHasRemaining.apply(remaining);
            }
        }
    }

    public void resetTimeout() {
        assertNotNull(timeout);
        timeout = calculateTimeout(timeoutSettings.getTimeoutMS());
    }

    /**
     * Resets the timeout if this timeout context is being used by pool maintenance
     */
    public void resetMaintenanceTimeout() {
        if (!isMaintenanceContext) {
            return;
        }
        timeout = Timeout.nullAsInfinite(timeout).run(NANOSECONDS,
                () -> timeout,
                (ms) -> calculateTimeout(timeoutSettings.getTimeoutMS()),
                () -> calculateTimeout(timeoutSettings.getTimeoutMS()));
    }

    public TimeoutContext withAdditionalReadTimeout(final int additionalReadTimeout) {
        // Only used outside timeoutMS usage
        assertNull(timeout);

        // Check existing read timeout is infinite
        if (timeoutSettings.getReadTimeoutMS() == 0) {
            return this;
        }

        long newReadTimeout = getReadTimeoutMS() + additionalReadTimeout;
        return new TimeoutContext(timeoutSettings.withReadTimeoutMS(newReadTimeout > 0 ? newReadTimeout : Long.MAX_VALUE));
    }

    @Override
    public String toString() {
        return "TimeoutContext{"
                + "isMaintenanceContext=" + isMaintenanceContext
                + ", timeoutSettings=" + timeoutSettings
                + ", timeout=" + timeout
                + ", minRoundTripTimeMS=" + minRoundTripTimeMS
                + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TimeoutContext that = (TimeoutContext) o;
        return isMaintenanceContext == that.isMaintenanceContext
                && minRoundTripTimeMS == that.minRoundTripTimeMS
                && Objects.equals(timeoutSettings, that.timeoutSettings)
                && Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isMaintenanceContext, timeoutSettings, timeout, minRoundTripTimeMS);
    }

    @Nullable
    public static Timeout calculateTimeout(@Nullable final Long timeoutMS) {
        // TODO-CSOT rename to startTimeout?
        if (timeoutMS != null) {
            return Timeout.expiresInWithZeroAsInfinite(timeoutMS, MILLISECONDS);
        }
        return null;
    }

    /**
     * Returns the computed server selection timeout
     *
     * <p>Caches the computed server selection timeout if:
     * <ul>
     *     <li>not in a maintenance context</li>
     *     <li>there is a timeoutMS, so to keep the same legacy behavior.</li>
     *     <li>the server selection timeout is less than the remaining overall timeout.</li>
     * </ul>
     *
     * @return the timeout context
     */
    public Timeout computeServerSelectionTimeout() {
        Timeout serverSelectionTimeout = StartTime.now()
                .timeoutAfterOrInfiniteIfNegative(getTimeoutSettings().getServerSelectionTimeoutMS(), MILLISECONDS);


        if (isMaintenanceContext || !hasTimeoutMS()) {
            return serverSelectionTimeout;
        }

        if (timeout != null && Timeout.earliest(serverSelectionTimeout, timeout) == timeout) {
            return timeout;
        }

        computedServerSelectionTimeout = serverSelectionTimeout;
        return computedServerSelectionTimeout;
    }

    /**
     * Returns the timeout context to use for the handshake process
     *
     * @return a new timeout context with the cached computed server selection timeout if available or this
     */
    public TimeoutContext withComputedServerSelectionTimeoutContext() {
        if (this.hasTimeoutMS() && computedServerSelectionTimeout != null) {
            return new TimeoutContext(false, timeoutSettings, computedServerSelectionTimeout);
        }
        return this;
    }

    public Timeout startWaitQueueTimeout(final StartTime checkoutStart) {
        final long ms = getTimeoutSettings().getMaxWaitTimeMS();
        return checkoutStart.timeoutAfterOrInfiniteIfNegative(ms, MILLISECONDS);
    }

    @Nullable
    public Timeout getTimeout() {
        return timeout;
    }
}
