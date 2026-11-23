/*
 * Copyright (c) 2025 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.statistics.wideevents.db

import android.annotation.SuppressLint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.BucketRounding.ROUND_DOWN
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.BucketRounding.ROUND_UP
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventPath
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.FAILURE
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.SUCCESS
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.UNKNOWN
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStep
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class WideEventRepositoryTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val database: WideEventDatabase =
        Room
            .inMemoryDatabaseBuilder(
                context = ApplicationProvider.getApplicationContext(),
                klass = WideEventDatabase::class.java,
            ).addTypeConverter(WideEventEntityTypeConverters(Moshi.Builder().build()))
            .build()

    private val timeProvider =
        object : CurrentTimeProvider {
            var currentTime = Instant.parse("2025-12-03T10:15:30.00Z")

            override fun elapsedRealtime(): Long = throw UnsupportedOperationException()

            override fun currentTimeMillis(): Long = currentTime.toEpochMilli()

            override fun localDateTimeNow(): LocalDateTime = throw UnsupportedOperationException()
        }

    private val appBuildConfig: AppBuildConfig = mock { appBuildConfig ->
        whenever(appBuildConfig.versionName).thenReturn("5.123.0")
    }

    private val wideEventRepository: WideEventRepository =
        WideEventRepositoryImpl(
            database = database,
            wideEventDao = database.wideEventDao(),
            timeProvider = timeProvider,
            appBuildConfig = appBuildConfig,
        )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `when wide event is inserted, then returns a valid id`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            assertTrue(eventId > 0)
        }

    @Test
    fun `when getting active wide events ids by name, returns valid event id`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = "app_settings",
                    metadata =
                    mapOf(
                        "key1" to "value1",
                        "key2" to null,
                    ),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            assertTrue(eventId > 0)

            val events = wideEventRepository.getActiveWideEventIdsByName("test_event")

            assertTrue(events.size == 1)
            assertTrue(events.single() == eventId)
        }

    @Test
    fun `when getting active wide events ids by name, does not return completed event ids`() =
        runTest {
            val completedEventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val activeEventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            // complete wide event
            wideEventRepository.setWideEventStatus(
                eventId = completedEventId,
                status = SUCCESS,
                metadata = emptyMap(),
            )

            val events = wideEventRepository.getActiveWideEventIdsByName("test_event")

            assertTrue(events.size == 1)
            assertTrue(events.single() == activeEventId)
        }

    @Test
    fun `when getting complete wide event ids, does not return active events`() =
        runTest {
            val eventId1 =
                wideEventRepository.insertWideEvent(
                    name = "test1",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val eventId2 =
                wideEventRepository.insertWideEvent(
                    name = "test2",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val eventId3 =
                wideEventRepository.insertWideEvent(
                    name = "test3",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val eventId4 =
                wideEventRepository.insertWideEvent(
                    name = "test4",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            wideEventRepository.setWideEventStatus(
                eventId = eventId1,
                status = WideEventRepository.WideEventStatus.UNKNOWN,
                metadata = emptyMap(),
            )

            wideEventRepository.setWideEventStatus(
                eventId = eventId4,
                status = FAILURE,
                metadata = emptyMap(),
            )

            val completedEventIds = wideEventRepository.getCompletedWideEventIds()
            assertTrue(completedEventIds.toSet() == setOf(eventId1, eventId4))
        }

    @Test
    fun `when getting multiple events, returns events with all previously stored parameters`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = "app_settings",
                    metadata =
                    mapOf(
                        "key1" to "value1",
                        "key2" to null,
                    ),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            wideEventRepository.addWideEventStep(
                eventId = eventId,
                step = WideEventStep(name = "step_A", success = true),
                metadata = mapOf("key3" to "value3"),
            )

            wideEventRepository.addWideEventStep(
                eventId = eventId,
                step = WideEventStep(name = "step_B", success = false),
                metadata = mapOf("key4" to "value4"),
            )

            wideEventRepository.setWideEventStatus(
                eventId = eventId,
                status = FAILURE,
                metadata = mapOf("key5" to "value5"),
            )

            val completedEvents = wideEventRepository.getWideEvents(setOf(eventId))
            assertTrue(completedEvents.size == 1)
            with(completedEvents.single()) {
                assertTrue(id == eventId)
                assertTrue(name == "test_event")
                assertTrue(flowEntryPoint == "app_settings")
                assertTrue(status == FAILURE)
                assertTrue(
                    steps ==
                        listOf(
                            WideEventStep(name = "step_A", success = true),
                            WideEventStep(name = "step_B", success = false),
                        ),
                )
                assertTrue(
                    metadata ==
                        mapOf(
                            "key1" to "value1",
                            "key2" to null,
                            "key3" to "value3",
                            "key4" to "value4",
                            "key5" to "value5",
                        ),
                )
            }
        }

    @Test
    fun `when meta parameters are stored, then they are returned with the event`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "2.1.3",
                )

            with(wideEventRepository.getWideEvents(setOf(eventId)).single()) {
                assertTrue(metaType == "android-test-event")
                assertTrue(metaVersion == "2.1.3")
            }
        }

    @Test
    fun `when app version changes after event is inserted, then event keeps the app version from insertion`() =
        runTest {
            whenever(appBuildConfig.versionName).thenReturn("5.1.0")
            val eventId = insertEvent(name = "test_event")

            whenever(appBuildConfig.versionName).thenReturn("5.2.0")
            wideEventRepository.addWideEventStep(
                eventId = eventId,
                step = WideEventStep(name = "step_A", success = true),
                metadata = emptyMap(),
            )
            wideEventRepository.setWideEventStatus(eventId = eventId, status = SUCCESS, metadata = emptyMap())

            assertEquals("5.1.0", wideEventRepository.getWideEvents(setOf(eventId)).single().appVersion)
        }

    @Test
    fun `when delete event then it is no longer accessible`() =
        runTest {
            val completedEventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val activeEventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            // complete wide event
            wideEventRepository.setWideEventStatus(
                eventId = completedEventId,
                status = SUCCESS,
                metadata = emptyMap(),
            )

            wideEventRepository.deleteWideEvent(completedEventId)
            assertFalse(wideEventRepository.hasCompletedWideEvents().first())
            assertTrue(wideEventRepository.getCompletedWideEventIds().isEmpty())
            assertTrue(wideEventRepository.getActiveWideEventIdsByName("test_event") == listOf(activeEventId))

            wideEventRepository.deleteWideEvent(activeEventId)
            assertTrue(wideEventRepository.getActiveWideEventIdsByName("test_event").isEmpty())
        }

    @Test
    fun `when getting active wide events ids by name, does not return event if name doesn't match`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "test_event",
                    flowEntryPoint = "app_settings",
                    metadata =
                    mapOf(
                        "key1" to "value1",
                        "key2" to null,
                    ),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            assertTrue(eventId > 0)

            val events = wideEventRepository.getActiveWideEventIdsByName("some_other_event")

            assertTrue(events.isEmpty())
        }

    @Test
    fun `when inserting wide event with cleanupPolicy, it is stored correctly`() =
        runTest {
            val policy =
                WideEventRepository.CleanupPolicy.OnTimeout(
                    duration = Duration.ofHours(1),
                    status = FAILURE,
                    metadata = mapOf("reason" to "big error"),
                )

            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "cleanup_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = policy,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
            assertEquals(policy, event.cleanupPolicy)
        }

    @Test
    fun `when inserting wide event with process-start cleanupPolicy, its owner is stored`() =
        runTest {
            val policy =
                WideEventRepository.CleanupPolicy.OnProcessStart(
                    ownerProcessName = ":vpn",
                    ignoreIfIntervalTimeoutPresent = true,
                    status = WideEventRepository.WideEventStatus.UNKNOWN,
                    metadata = emptyMap(),
                )

            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "cleanup_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = policy,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
            assertEquals(policy, event.cleanupPolicy)
        }

    @Test
    fun `when cleaning up a missing event, nothing happens`() =
        runTest {
            var decided = false

            wideEventRepository.cleanUpWideEvent(eventId = 404L) {
                decided = true
                null
            }

            assertFalse(decided)
        }

    @Test
    fun `when cleaning up a completed event, it is left untouched and not re-evaluated`() =
        runTest {
            val eventId = insertActiveEvent()
            wideEventRepository.setWideEventStatus(eventId = eventId, status = SUCCESS, metadata = emptyMap())
            var decided = false

            wideEventRepository.cleanUpWideEvent(eventId = eventId) {
                decided = true
                WideEventRepository.CleanupDecision(status = WideEventRepository.WideEventStatus.UNKNOWN, metadata = emptyMap())
            }

            assertFalse(decided)
            assertEquals(SUCCESS, wideEventRepository.getWideEvents(setOf(eventId)).single().status)
        }

    @Test
    fun `when cleanup decides to keep the event, it stays active and unchanged`() =
        runTest {
            val eventId = insertActiveEvent(metadata = mapOf("key" to "value"))

            wideEventRepository.cleanUpWideEvent(eventId = eventId) { null }

            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
            assertEquals(null, event.status)
            assertEquals(mapOf("key" to "value"), event.metadata)
            assertEquals(listOf(eventId), wideEventRepository.getActiveWideEventIds())
        }

    @Test
    fun `when cleanup decides to close the event, status and metadata are applied and the daily occurrence recorded`() =
        runTest {
            val eventId = insertActiveEvent(metadata = mapOf("kept" to "yes", "overridden" to "old"))

            wideEventRepository.cleanUpWideEvent(eventId = eventId) {
                WideEventRepository.CleanupDecision(status = FAILURE, metadata = mapOf("overridden" to "new", "reason" to "cleanup"))
            }

            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
            assertEquals(FAILURE, event.status)
            assertEquals(mapOf("kept" to "yes", "overridden" to "new", "reason" to "cleanup"), event.metadata)
            assertTrue(event.isFirstDailyOccurrence)
            assertEquals(listOf(eventId), wideEventRepository.getCompletedWideEventIds())
        }

    @Test
    fun `when cleanup closes a second event with the same name and status on the same day, it is not the first daily occurrence`() =
        runTest {
            val firstId = insertActiveEvent()
            val secondId = insertActiveEvent()
            val decision = WideEventRepository.CleanupDecision(status = FAILURE, metadata = emptyMap())

            wideEventRepository.cleanUpWideEvent(eventId = firstId) { decision }
            wideEventRepository.cleanUpWideEvent(eventId = secondId) { decision }

            val events = wideEventRepository.getWideEvents(setOf(firstId, secondId)).associateBy { it.id }
            assertTrue(events.getValue(firstId).isFirstDailyOccurrence)
            assertFalse(events.getValue(secondId).isFirstDailyOccurrence)
        }

    @Test
    fun `when cleaning up, the decision sees the event's current state`() =
        runTest {
            val eventId = insertActiveEvent()
            wideEventRepository.addWideEventStep(
                eventId = eventId,
                step = WideEventRepository.WideEventStep(name = "step_1", success = true),
                metadata = mapOf("after_insert" to "true"),
            )
            var seen: WideEventRepository.WideEvent? = null

            wideEventRepository.cleanUpWideEvent(eventId = eventId) { event ->
                seen = event
                null
            }

            assertEquals(listOf(WideEventRepository.WideEventStep(name = "step_1", success = true)), seen?.steps)
            assertEquals("true", seen?.metadata?.get("after_insert"))
        }

    private suspend fun insertActiveEvent(metadata: Map<String, String?> = emptyMap()): Long =
        wideEventRepository.insertWideEvent(
            name = "cleanup_event",
            flowEntryPoint = null,
            metadata = metadata,
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

    @Test
    fun `when starting an interval, it is tracked correctly`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "interval_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            wideEventRepository.startInterval(
                eventId = eventId,
                name = "interval_1",
                timeout = Duration.ofSeconds(10),
                buckets = DEFAULT_BUCKETS,
                rounding = ROUND_DOWN,
            )

            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
            assertTrue(event.activeIntervals.any { it.name == "interval_1" && it.timeout == Duration.ofSeconds(10) })
        }

    @Test
    fun `when ending an interval, duration is recorded in metadata and interval removed`() =
        runTest {
            val eventId =
                wideEventRepository.insertWideEvent(
                    name = "interval_event",
                    flowEntryPoint = null,
                    metadata = emptyMap(),
                    cleanupPolicy = DEFAULT_CLEANUP_POLICY,
                    metaType = "android-test-event",
                    metaVersion = "1.0.0",
                )

            wideEventRepository.startInterval(
                eventId = eventId,
                name = "interval_1",
                timeout = Duration.ofSeconds(10),
                buckets = DEFAULT_BUCKETS,
                rounding = ROUND_DOWN,
            )

            val expectedDuration = Duration.ofSeconds(15)
            timeProvider.currentTime += expectedDuration
            val duration = wideEventRepository.endInterval(eventId, "interval_1")
            val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

            assertEquals(expectedDuration, duration)
            assertTrue(event.activeIntervals.isEmpty())
            assertTrue(event.metadata.containsKey("interval_1"))
        }

    @Test
    fun `when ending an interval with duration less than 1s, maps to zero`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofMillis(500)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("0", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with duration exactly at bucket boundary, maps to that bucket`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofSeconds(5)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("5000", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with duration between buckets, maps to lower bucket`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )

        // 7 seconds is between 5s and 10s buckets, should map to 5s (lower end)
        timeProvider.currentTime += Duration.ofSeconds(7)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("5000", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with duration greater than largest bucket, maps to largest bucket`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )

        // 15 minutes is greater than largest bucket (10 minutes)
        timeProvider.currentTime += Duration.ofMinutes(15)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals(Duration.ofMinutes(10).toMillis().toString(), event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with custom buckets, duration is mapped to nearest lower bucket`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = listOf(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2)),
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofMillis(750)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("500", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with custom buckets and duration above largest, maps to largest`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = listOf(Duration.ofMillis(100), Duration.ofSeconds(2)),
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofSeconds(10)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("2000", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with custom buckets and duration below smallest, records zero`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = listOf(Duration.ofMillis(100), Duration.ofSeconds(1)),
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofMillis(50)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("0", event.metadata["interval_1"])
    }

    @Test
    fun `when ending an interval with null buckets, records raw duration in milliseconds`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = null,
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime += Duration.ofMillis(1234)
        wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals("1234", event.metadata["interval_1"])
    }

    @Test
    fun `interval buckets survive a persistence round-trip`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )
        val customBuckets = listOf(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2))

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = customBuckets,
            rounding = ROUND_DOWN,
        )

        val readBack = wideEventRepository.getWideEvents(setOf(eventId)).single()
        val interval = readBack.activeIntervals.single { it.name == "interval_1" }
        assertEquals(customBuckets, interval.buckets)
    }

    @Test
    fun `startInterval persists buckets in ascending order regardless of input order`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )
        val unorderedBuckets = listOf(Duration.ofSeconds(2), Duration.ofMillis(100), Duration.ofMillis(500))

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = unorderedBuckets,
            rounding = ROUND_DOWN,
        )

        val readBack = wideEventRepository.getWideEvents(setOf(eventId)).single()
        val interval = readBack.activeIntervals.single { it.name == "interval_1" }
        assertEquals(
            listOf(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2)),
            interval.buckets,
        )
    }

    @Test
    fun `startInterval rejects negative bucket boundaries`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                wideEventRepository.startInterval(
                    eventId = eventId,
                    name = "interval_1",
                    timeout = null,
                    buckets = listOf(Duration.ofMillis(-1), Duration.ofMillis(100)),
                    rounding = ROUND_DOWN,
                )
            }
        }
    }

    @Test
    fun `when ending an interval with negative duration, maps to minus one bucket`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )

        timeProvider.currentTime -= Duration.ofMillis(5)
        val duration = wideEventRepository.endInterval(eventId, "interval_1")
        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()

        assertEquals(Duration.ofMillis(-5), duration)
        assertEquals("-1", event.metadata["interval_1"])
    }

    @Test
    fun `when ending a round-up interval with duration less than smallest bucket, maps to smallest bucket`() = runTest {
        assertEquals("1000", recordedRoundUpBucket(elapsed = Duration.ofMillis(500)))
    }

    @Test
    fun `when ending a round-up interval with duration between buckets, maps to upper bucket`() = runTest {
        assertEquals("10000", recordedRoundUpBucket(elapsed = Duration.ofSeconds(7)))
    }

    @Test
    fun `when ending a round-up interval with duration exactly at bucket boundary, maps to that bucket`() = runTest {
        assertEquals("5000", recordedRoundUpBucket(elapsed = Duration.ofSeconds(5)))
    }

    @Test
    fun `when ending a round-up interval with duration greater than largest bucket, maps to largest bucket`() = runTest {
        assertEquals(Duration.ofMinutes(10).toMillis().toString(), recordedRoundUpBucket(elapsed = Duration.ofMinutes(15)))
    }

    @Test
    fun `when ending a round-up interval with negative duration, maps to minus one bucket`() = runTest {
        assertEquals("-1", recordedRoundUpBucket(elapsed = Duration.ofMillis(-5)))
    }

    @Test
    fun `interval rounding survives a persistence round-trip`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "round_down_interval",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_DOWN,
        )
        wideEventRepository.startInterval(
            eventId = eventId,
            name = "round_up_interval",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_UP,
        )

        val intervals = wideEventRepository.getWideEvents(setOf(eventId)).single().activeIntervals
        assertEquals(ROUND_DOWN, intervals.single { it.name == "round_down_interval" }.rounding)
        assertEquals(ROUND_UP, intervals.single { it.name == "round_up_interval" }.rounding)
    }

    private suspend fun recordedRoundUpBucket(elapsed: Duration): String? {
        val eventId = wideEventRepository.insertWideEvent(
            name = "interval_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-test-event",
            metaVersion = "1.0.0",
        )

        wideEventRepository.startInterval(
            eventId = eventId,
            name = "interval_1",
            timeout = null,
            buckets = DEFAULT_BUCKETS,
            rounding = ROUND_UP,
        )

        timeProvider.currentTime += elapsed
        wideEventRepository.endInterval(eventId, "interval_1")
        return wideEventRepository.getWideEvents(setOf(eventId)).single().metadata["interval_1"]
    }

    @Test
    fun `when an event is the first completed today for its name and status, it is marked as the first daily occurrence`() = runTest {
        val eventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = eventId, status = SUCCESS, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(eventId))
    }

    @Test
    fun `when an event with the same name and status already completed today, it is not the first daily occurrence`() = runTest {
        val firstEventId = insertEvent(name = "test_event")
        val secondEventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = firstEventId, status = SUCCESS, metadata = emptyMap())
        wideEventRepository.setWideEventStatus(eventId = secondEventId, status = SUCCESS, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(firstEventId))
        assertFalse(isFirstDailyOccurrence(secondEventId))
    }

    @Test
    fun `when events share a name but differ in status, each is the first daily occurrence`() = runTest {
        val successEventId = insertEvent(name = "test_event")
        val failureEventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = successEventId, status = SUCCESS, metadata = emptyMap())
        wideEventRepository.setWideEventStatus(eventId = failureEventId, status = FAILURE, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(successEventId))
        assertTrue(isFirstDailyOccurrence(failureEventId))
    }

    @Test
    fun `when events share a status but differ in name, each is the first daily occurrence`() = runTest {
        val firstEventId = insertEvent(name = "test_event_1")
        val secondEventId = insertEvent(name = "test_event_2")

        wideEventRepository.setWideEventStatus(eventId = firstEventId, status = SUCCESS, metadata = emptyMap())
        wideEventRepository.setWideEventStatus(eventId = secondEventId, status = SUCCESS, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(firstEventId))
        assertTrue(isFirstDailyOccurrence(secondEventId))
    }

    @Test
    fun `when the same name and status completes later on the same UTC day, it is not the first daily occurrence`() = runTest {
        val firstEventId = insertEvent(name = "test_event")
        val secondEventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = firstEventId, status = SUCCESS, metadata = emptyMap())
        timeProvider.currentTime = Instant.parse("2025-12-03T23:59:59.00Z")
        wideEventRepository.setWideEventStatus(eventId = secondEventId, status = SUCCESS, metadata = emptyMap())

        assertFalse(isFirstDailyOccurrence(secondEventId))
    }

    @Test
    fun `when the same name and status completes on the next UTC day, it is the first daily occurrence again`() = runTest {
        val firstEventId = insertEvent(name = "test_event")
        val secondEventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = firstEventId, status = SUCCESS, metadata = emptyMap())
        timeProvider.currentTime = Instant.parse("2025-12-04T00:00:00.00Z")
        wideEventRepository.setWideEventStatus(eventId = secondEventId, status = SUCCESS, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(secondEventId))
    }

    @Test
    fun `when an event is still active, it is not marked as the first daily occurrence`() = runTest {
        val eventId = insertEvent(name = "test_event")

        assertFalse(isFirstDailyOccurrence(eventId))
    }

    @Test
    fun `when an event completes, it does not affect the daily occurrence of an event completed earlier`() = runTest {
        val firstEventId = insertEvent(name = "test_event")
        val secondEventId = insertEvent(name = "test_event")

        wideEventRepository.setWideEventStatus(eventId = firstEventId, status = SUCCESS, metadata = emptyMap())
        wideEventRepository.setWideEventStatus(eventId = secondEventId, status = SUCCESS, metadata = emptyMap())

        assertTrue(isFirstDailyOccurrence(firstEventId))
    }

    @Test
    fun `when no step is appended to the path, the event has no path`() = runTest {
        val eventId = insertEvent(name = "test_event")

        assertNull(readPath(eventId))
    }

    @Test
    fun `when steps are appended to the path, they are kept in order including returns to earlier steps`() = runTest {
        val eventId = insertEvent(name = "test_event")

        appendToPath(eventId, "started", "code_screen", "code_entry", "code_screen")

        assertEquals(
            WideEventPath(steps = listOf("started", "code_screen", "code_entry", "code_screen"), lastStep = "code_screen"),
            readPath(eventId),
        )
    }

    @Test
    fun `when a step equal to the last one is appended, the path is unchanged but its metadata is merged`() = runTest {
        val eventId = insertEvent(name = "test_event")
        appendToPath(eventId, "started", "code_screen")

        wideEventRepository.appendToWideEventPath(eventId = eventId, step = "code_screen", metadata = mapOf("method" to "qr"))

        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
        assertEquals(WideEventPath(steps = listOf("started", "code_screen"), lastStep = "code_screen"), event.path)
        assertEquals("qr", event.metadata["method"])
    }

    @Test
    fun `when a step contains commas, they are dropped before it is appended and compared`() = runTest {
        val eventId = insertEvent(name = "test_event")

        appendToPath(eventId, "code,screen", "codescreen", "code_entry")

        assertEquals(WideEventPath(steps = listOf("codescreen", "code_entry"), lastStep = "code_entry"), readPath(eventId))
    }

    @Test
    fun `when a step has nothing but commas, it is rejected`() = runTest {
        val eventId = insertEvent(name = "test_event")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { wideEventRepository.appendToWideEventPath(eventId = eventId, step = ",", metadata = emptyMap()) }
        }
        assertNull(readPath(eventId))
    }

    @Test
    fun `when the path reaches 20 steps, it keeps all of them`() = runTest {
        val eventId = insertEvent(name = "test_event")
        val steps = (1..20).map { "step_$it" }

        appendToPath(eventId, *steps.toTypedArray())

        assertEquals(WideEventPath(steps = steps, lastStep = "step_20"), readPath(eventId))
    }

    @Test
    fun `when the path would exceed 20 steps, it keeps the first 19 and ends with truncated while last step stays real`() = runTest {
        val eventId = insertEvent(name = "test_event")

        appendToPath(eventId, *(1..25).map { "step_$it" }.toTypedArray())

        assertEquals(
            WideEventPath(steps = (1..19).map { "step_$it" } + "truncated", lastStep = "step_25"),
            readPath(eventId),
        )
    }

    @Test
    fun `when appending to the path of a completed event, it fails and the path is unchanged`() = runTest {
        val eventId = insertEvent(name = "test_event")
        appendToPath(eventId, "started")
        wideEventRepository.setWideEventStatus(eventId = eventId, status = SUCCESS, metadata = emptyMap())

        assertThrows(IllegalStateException::class.java) {
            runBlocking { wideEventRepository.appendToWideEventPath(eventId = eventId, step = "code_screen", metadata = emptyMap()) }
        }
        assertEquals(WideEventPath(steps = listOf("started"), lastStep = "started"), readPath(eventId))
    }

    @Test
    fun `when an event with a path is cleaned up, it keeps the path`() = runTest {
        val eventId = insertEvent(name = "test_event")
        appendToPath(eventId, "started", "code_screen")

        wideEventRepository.cleanUpWideEvent(eventId) { WideEventRepository.CleanupDecision(status = UNKNOWN, metadata = emptyMap()) }

        val event = wideEventRepository.getWideEvents(setOf(eventId)).single()
        assertEquals(UNKNOWN, event.status)
        assertEquals(WideEventPath(steps = listOf("started", "code_screen"), lastStep = "code_screen"), event.path)
    }

    private suspend fun appendToPath(
        eventId: Long,
        vararg steps: String,
    ) {
        steps.forEach { step -> wideEventRepository.appendToWideEventPath(eventId = eventId, step = step, metadata = emptyMap()) }
    }

    private suspend fun readPath(eventId: Long): WideEventPath? = wideEventRepository.getWideEvents(setOf(eventId)).single().path

    private suspend fun insertEvent(name: String): Long =
        wideEventRepository.insertWideEvent(
            name = name,
            flowEntryPoint = null,
            metadata = emptyMap(),
            cleanupPolicy = DEFAULT_CLEANUP_POLICY,
            metaType = "android-${name.replace('_', '-')}",
            metaVersion = "1.0.0",
        )

    private suspend fun isFirstDailyOccurrence(eventId: Long): Boolean =
        wideEventRepository.getWideEvents(setOf(eventId)).single().isFirstDailyOccurrence

    companion object {
        val DEFAULT_CLEANUP_POLICY =
            WideEventRepository.CleanupPolicy.OnTimeout(
                duration = Duration.ofDays(7),
                status = WideEventRepository.WideEventStatus.UNKNOWN,
                metadata = emptyMap(),
            )

        val DEFAULT_BUCKETS: List<Duration> = listOf(
            Duration.ofSeconds(1),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            Duration.ofSeconds(30),
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),
        )
    }
}
