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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.FAILURE
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStatus.SUCCESS
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository.WideEventStep
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WideEventRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val database: WideEventDatabase = Room
        .inMemoryDatabaseBuilder(
            context = ApplicationProvider.getApplicationContext(),
            klass = WideEventDatabase::class.java,
        )
        .addTypeConverter(WideEventEntityTypeConverters(Moshi.Builder().build()))
        .build()

    private val wideEventRepository: WideEventRepository = WideEventRepositoryImpl(
        database = database,
        wideEventDao = database.wideEventDao(),
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `when wide event is inserted, then returns a valid id`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        assertTrue(eventId > 0)
    }

    @Test
    fun `when getting active wide events ids by name, returns valid event id`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "key1" to "value1",
                "key2" to null,
            ),
        )

        assertTrue(eventId > 0)

        val events = wideEventRepository.getActiveWideEventIdsByName("test_event")

        assertTrue(events.size == 1)
        assertTrue(events.single() == eventId)
    }

    @Test
    fun `when getting active wide events ids by name, does not return completed event ids`() = runTest {
        val completedEventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        val activeEventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
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
    fun `when getting complete wide event ids, does not return active events`() = runTest {
        val eventId1 = wideEventRepository.insertWideEvent(
            name = "test1",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        val eventId2 = wideEventRepository.insertWideEvent(
            name = "test2",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        val eventId3 = wideEventRepository.insertWideEvent(
            name = "test3",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        val eventId4 = wideEventRepository.insertWideEvent(
            name = "test4",
            flowEntryPoint = null,
            metadata = emptyMap(),
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

        val completedEventIds = wideEventRepository.getCompletedWideEventIdsFlow().first()
        assertTrue(completedEventIds == setOf(eventId1, eventId4))
    }

    @Test
    fun `when getting multiple events, returns events with all previously stored parameters`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "key1" to "value1",
                "key2" to null,
            ),
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
                steps == listOf(
                    WideEventStep(name = "step_A", success = true),
                    WideEventStep(name = "step_B", success = false),
                ),
            )
            assertTrue(
                metadata == mapOf(
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
    fun `when delete event then it is no longer accessible`() = runTest {
        val completedEventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        val activeEventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = null,
            metadata = emptyMap(),
        )

        // complete wide event
        wideEventRepository.setWideEventStatus(
            eventId = completedEventId,
            status = SUCCESS,
            metadata = emptyMap(),
        )

        wideEventRepository.deleteWideEvent(completedEventId)
        assertTrue(wideEventRepository.getCompletedWideEventIdsFlow().first().isEmpty())
        assertTrue(wideEventRepository.getActiveWideEventIdsByName("test_event") == listOf(activeEventId))

        wideEventRepository.deleteWideEvent(activeEventId)
        assertTrue(wideEventRepository.getActiveWideEventIdsByName("test_event").isEmpty())
    }

    @Test
    fun `when getting active wide events ids by name, does not return event if name doesn't match`() = runTest {
        val eventId = wideEventRepository.insertWideEvent(
            name = "test_event",
            flowEntryPoint = "app_settings",
            metadata = mapOf(
                "key1" to "value1",
                "key2" to null,
            ),
        )

        assertTrue(eventId > 0)

        val events = wideEventRepository.getActiveWideEventIdsByName("some_other_event")

        assertTrue(events.isEmpty())
    }
}
