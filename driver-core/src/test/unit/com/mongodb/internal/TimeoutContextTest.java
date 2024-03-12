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

import com.mongodb.MongoOperationTimeoutException;
import org.bson.BsonElement;
import org.bson.BsonInt64;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_INFINITE_TIMEOUT;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_MAX_AWAIT_TIME;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_MAX_COMMIT;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_MAX_TIME;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_MAX_TIME_AND_AWAIT_TIME;
import static com.mongodb.ClusterFixture.TIMEOUT_SETTINGS_WITH_TIMEOUT;
import static com.mongodb.ClusterFixture.sleep;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;


final class TimeoutContextTest {

    @SuppressWarnings("checkstyle:methodLength")
    @TestFactory
    Collection<DynamicTest> timeoutContextTest() {
        return asList(
                dynamicTest("test defaults", () -> {
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS);
                    assertAll(
                            () -> assertFalse(timeoutContext.hasTimeoutMS()),
                            () -> assertEquals(0, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxCommitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getReadTimeoutMS())
                    );
                }),
                dynamicTest("Uses timeoutMS if set", () -> {
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS_WITH_TIMEOUT.withMaxAwaitTimeMS(9));
                    assertAll(
                            () -> assertTrue(timeoutContext.hasTimeoutMS()),
                            () -> assertTrue(timeoutContext.getMaxTimeMS() > 0),
                            () -> assertEquals(0, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertTrue(timeoutContext.getMaxCommitTimeMS() > 0)
                    );
                }),
                dynamicTest("test infinite timeoutMS", () -> {
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS_WITH_INFINITE_TIMEOUT);
                    assertAll(
                            () -> assertTrue(timeoutContext.hasTimeoutMS()),
                            () -> assertEquals(0, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("MaxTimeMS set", () -> {
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS_WITH_MAX_TIME);
                    assertAll(
                            () -> assertEquals(100, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("MaxAwaitTimeMS set", () -> {
                    TimeoutContext timeoutContext =
                            new TimeoutContext(TIMEOUT_SETTINGS_WITH_MAX_AWAIT_TIME);
                    assertAll(
                            () -> assertEquals(0, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(101, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("MaxTimeMS and MaxAwaitTimeMS set", () -> {
                    TimeoutContext timeoutContext =
                            new TimeoutContext(TIMEOUT_SETTINGS_WITH_MAX_TIME_AND_AWAIT_TIME);
                    assertAll(
                            () -> assertEquals(101, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(1001, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("MaxCommitTimeMS set", () -> {
                    TimeoutContext timeoutContext =
                            new TimeoutContext(TIMEOUT_SETTINGS_WITH_MAX_COMMIT);
                    assertAll(
                            () -> assertEquals(0, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(0, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(999L, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("All deprecated options set", () -> {
                    TimeoutContext timeoutContext =
                            new TimeoutContext(TIMEOUT_SETTINGS_WITH_MAX_TIME_AND_AWAIT_TIME
                                    .withMaxCommitMS(999L));
                    assertAll(
                            () -> assertEquals(101, timeoutContext.getMaxTimeMS()),
                            () -> assertEquals(1001, timeoutContext.getMaxAwaitTimeMS()),
                            () -> assertEquals(999, timeoutContext.getMaxCommitTimeMS())
                    );
                }),
                dynamicTest("Use timeout if available or the alternative", () -> assertAll(
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS);
                            assertEquals(99L, timeoutContext.timeoutOrAlternative(99));
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(0));
                            assertEquals(0L, timeoutContext.timeoutOrAlternative(99));
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(999));
                            assertTrue(timeoutContext.timeoutOrAlternative(0) <= 999);
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(999));
                            assertTrue(timeoutContext.timeoutOrAlternative(999999) <= 999);
                        }
                )),
                dynamicTest("Calculate min works as expected", () -> assertAll(
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS);
                            assertEquals(99L, timeoutContext.calculateMin(99));
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(0));
                            assertEquals(99L, timeoutContext.calculateMin(99));
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(999));
                            assertTrue(timeoutContext.calculateMin(0) <= 999);
                        },
                        () -> {
                            TimeoutContext timeoutContext =
                                    new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(999));
                            assertTrue(timeoutContext.calculateMin(999999) <= 999);
                        }
                )),
                dynamicTest("withAdditionalReadTimeout works as expected", () -> assertAll(
                        () -> {
                            TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withReadTimeoutMS(0));
                            assertEquals(0L, timeoutContext.withAdditionalReadTimeout(101).getReadTimeoutMS());
                        },
                        () -> {
                            TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withReadTimeoutMS(10_000L));
                            assertEquals(10_101L, timeoutContext.withAdditionalReadTimeout(101).getReadTimeoutMS());
                        },
                        () -> {
                            long originalValue = Long.MAX_VALUE - 100;
                            TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withReadTimeoutMS(originalValue));
                            assertEquals(Long.MAX_VALUE, timeoutContext.withAdditionalReadTimeout(101).getReadTimeoutMS());
                        },
                        () -> {
                            TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(0L));
                            assertThrows(AssertionError.class, () -> timeoutContext.withAdditionalReadTimeout(1));
                        },
                        () -> {
                            TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(10_000L));
                            assertThrows(AssertionError.class, () -> timeoutContext.withAdditionalReadTimeout(1));
                        }
                )),
                dynamicTest("Expired works as expected", () -> {
                    TimeoutContext smallTimeout = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(1));
                    TimeoutContext longTimeout =
                            new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(9999999));
                    TimeoutContext noTimeout = new TimeoutContext(TIMEOUT_SETTINGS);
                    sleep(100);
                    assertAll(
                            () -> assertFalse(noTimeout.hasExpired()),
                            () -> assertFalse(longTimeout.hasExpired()),
                            () -> assertTrue(smallTimeout.hasExpired())
                    );
                }),
                dynamicTest("throws when calculating timeout if expired", () -> {
                    TimeoutContext smallTimeout = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(1));
                    TimeoutContext longTimeout =
                            new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(9999999));
                    TimeoutContext noTimeout = new TimeoutContext(TIMEOUT_SETTINGS);
                    sleep(100);
                    assertAll(
                            () -> assertThrows(MongoOperationTimeoutException.class, smallTimeout::getReadTimeoutMS),
                            () -> assertThrows(MongoOperationTimeoutException.class, smallTimeout::getWriteTimeoutMS),
                            () -> assertThrows(MongoOperationTimeoutException.class, smallTimeout::getMaxTimeMS),
                            () -> assertThrows(MongoOperationTimeoutException.class, smallTimeout::getMaxCommitTimeMS),
                            () -> assertThrows(MongoOperationTimeoutException.class, () -> smallTimeout.timeoutOrAlternative(1)),
                            () -> assertDoesNotThrow(longTimeout::getReadTimeoutMS),
                            () -> assertDoesNotThrow(longTimeout::getWriteTimeoutMS),
                            () -> assertDoesNotThrow(longTimeout::getMaxTimeMS),
                            () -> assertDoesNotThrow(longTimeout::getMaxCommitTimeMS),
                            () -> assertDoesNotThrow(() -> longTimeout.timeoutOrAlternative(1)),
                            () -> assertDoesNotThrow(noTimeout::getReadTimeoutMS),
                            () -> assertDoesNotThrow(noTimeout::getWriteTimeoutMS),
                            () -> assertDoesNotThrow(noTimeout::getMaxTimeMS),
                            () -> assertDoesNotThrow(noTimeout::getMaxCommitTimeMS),
                            () -> assertDoesNotThrow(() -> noTimeout.timeoutOrAlternative(1))
                    );
                }),
                dynamicTest("validates minRoundTripTime for maxTimeMS", () -> {
                    Supplier<TimeoutContext> supplier = () -> new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(100));
                    assertAll(
                            () -> assertTrue(supplier.get().getMaxTimeMS() <= 100),
                            () -> assertTrue(supplier.get().minRoundTripTimeMS(10).getMaxTimeMS() <= 90),
                            () -> assertThrows(MongoOperationTimeoutException.class, () -> supplier.get().minRoundTripTimeMS(101).getMaxTimeMS()),
                            () -> assertThrows(MongoOperationTimeoutException.class, () -> supplier.get().minRoundTripTimeMS(100).getMaxTimeMS())
                    );
                }),
                dynamicTest("should add maxTimeMS to extra elements when timeoutMS is set", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(100));
                    timeoutContext.addExtraElements(extraElements);
                    BsonElement maxTimeMsExtraElement = extraElements.get(0);
                    assertAll(
                            () -> assertEquals(1, extraElements.size()),
                            () -> assertEquals("maxTimeMS", maxTimeMsExtraElement.getName()),
                            () -> assertTrue(maxTimeMsExtraElement.getValue().asInt64().getValue() <= 100)
                    );
                }),
                dynamicTest("MaxTimeSupplier should throw when timeoutMS expires", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(1));
                    assertThrows(MongoOperationTimeoutException.class, () -> timeoutContext.addExtraElements(extraElements));
                }),
                dynamicTest("should add maxTimeMS to extra elements when timeoutMS is not set", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();
                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withMaxTimeMS(1));
                    timeoutContext.addExtraElements(extraElements);
                    BsonElement maxTimeMsExtraElement = extraElements.get(0);
                    assertAll(
                            () -> assertEquals(1, extraElements.size()),
                            () -> assertEquals("maxTimeMS", maxTimeMsExtraElement.getName()),
                            () -> assertEquals(new BsonElement("maxTimeMS", new BsonInt64(1)), extraElements.get(0)));
                }),
                dynamicTest("should not remove any existing extra elements", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();
                    BsonElement extraElement = new BsonElement("test", new BsonInt64(1));
                    extraElements.add(extraElement);

                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(100).withMaxTimeMS(1));
                    timeoutContext.addExtraElements(extraElements);
                    assertAll(
                            () -> assertEquals(2, extraElements.size()),
                            () -> assertTrue(extraElements.contains(extraElement)));
                }),
                dynamicTest("should override maxTimeMS and add to extra elements when MaxTimeSupplier is set", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();

                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(100).withMaxTimeMS(1));
                    timeoutContext.setMaxTimeSupplier(() -> 2L);
                    timeoutContext.addExtraElements(extraElements);
                    assertAll(
                            () -> assertEquals(1, extraElements.size()),
                            () -> assertEquals(new BsonElement("maxTimeMS", new BsonInt64(2)), extraElements.get(0)));
                }),
                dynamicTest("should propagate exception from MaxTimeSupplier", () -> {
                    List<BsonElement> extraElements = new ArrayList<>();

                    TimeoutContext timeoutContext = new TimeoutContext(TIMEOUT_SETTINGS.withTimeoutMS(100).withMaxTimeMS(1));
                    timeoutContext.setMaxTimeSupplier(() -> {
                        throw new MongoOperationTimeoutException("test");
                    });
                    assertThrows(MongoOperationTimeoutException.class, () -> timeoutContext.addExtraElements(extraElements));
                })
        );
    }

    private TimeoutContextTest() {
    }
}
