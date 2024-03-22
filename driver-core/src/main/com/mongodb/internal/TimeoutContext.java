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
import com.mongodb.internal.time.StartTime;
import com.mongodb.internal.time.Timeout;
import com.mongodb.lang.Nullable;
import com.mongodb.session.ClientSession;

import java.util.Objects;

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

    public static MongoOperationTimeoutException createMongoRoundTripTimeoutException() {
        return createMongoTimeoutException("Remaining timeoutMS is less than the server's minimum round trip time.");
    }

    public static MongoOperationTimeoutException createMongoTimeoutException(final String message) {
        return new MongoOperationTimeoutException(message);
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
        return Timeout.run(timeout, NANOSECONDS, () -> false, (ns) -> false, () -> true, () -> false);
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
        Long timeoutMS = timeoutSettings.getTimeoutMS();
        if (timeoutMS == null) {
            return alternativeTimeoutMS;
        } else if (timeoutMS == 0) {
            // TODO-CSOT is this correct? shouldn't this check/return the remaining timeout ms?
            return timeoutMS;
        } else {
            assertNotNull(timeout);
            // TODO-CSOT refactor once above addressed? Unclear how to avoid extracting numbers.
            return timeout.run(MILLISECONDS,
                    () -> 0L,
                    (ms) -> ms,
                    () -> {
                        throw createMongoTimeoutException("The operation timeout has expired.");
                    });
        }
    }

    /**
     * Calculates the minimum timeout value between two possible timeouts.
     *
     * @param alternativeTimeoutMS the alternative timeout
     * @return the minimum value to use.
     */
    public long calculateMin(final long alternativeTimeoutMS) {
        Long timeoutMS = timeoutSettings.getTimeoutMS();
        if (timeoutMS == null) {
            return alternativeTimeoutMS;
        } else if (timeoutMS == 0) { // infinite
            // TODO-CSOT is this correct? alt might be 0=infinite here, but later is min'd against remaining?
            return alternativeTimeoutMS;
        } else {
            assertNotNull(timeout);
            // TODO-CSOT refactor once above addressed? Unclear how to avoid extracting numbers.
            long remaining = timeout.run(MILLISECONDS,
                    () -> 0L,
                    (ms) -> ms,
                    () -> {
                        throw createMongoTimeoutException("The operation timeout has expired.");
                    });
            if (alternativeTimeoutMS == 0) {
                return remaining;
            } else {
                return Math.min(remaining, alternativeTimeoutMS);
            }
        }
    }

    public TimeoutSettings getTimeoutSettings() {
        return timeoutSettings;
    }

    public long getMaxAwaitTimeMS() {
        return timeoutSettings.getMaxAwaitTimeMS();
    }

    public long getMaxTimeMS() {
        long maxTimeMS = timeoutOrAlternative(timeoutSettings.getMaxTimeMS());
        return Timeout.run(timeout, MILLISECONDS,
                // TODO-CSOT why does infinite translate to getMaxTime?
                () -> maxTimeMS,
                // TODO-CSOT why is timeout's ms not used anywhere?
                (ms) -> calculateMaxTimeMs(maxTimeMS),
                () -> calculateMaxTimeMs(maxTimeMS),
                () -> maxTimeMS);
    }

    private long calculateMaxTimeMs(final long maxTimeMS) {
        if (minRoundTripTimeMS >= maxTimeMS) {
            throw createMongoRoundTripTimeoutException();
        }
        return maxTimeMS - minRoundTripTimeMS;
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

    public int getConnectTimeoutMs() {
        return Math.toIntExact(calculateMin(getTimeoutSettings().getConnectTimeoutMS()));
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
        timeout = Timeout.run(timeout, NANOSECONDS,
                () -> timeout,
                (ms) -> calculateTimeout(timeoutSettings.getTimeoutMS()),
                () -> calculateTimeout(timeoutSettings.getTimeoutMS()),
                () -> timeout);
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
            return timeoutMS == 0 ? Timeout.infinite() : Timeout.expiresIn(timeoutMS, MILLISECONDS);
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

        if (serverSelectionTimeout.orEarlier(timeout) == timeout) {
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
        return computedServerSelectionTimeout == null
                ? this : new TimeoutContext(false, timeoutSettings, computedServerSelectionTimeout);
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
