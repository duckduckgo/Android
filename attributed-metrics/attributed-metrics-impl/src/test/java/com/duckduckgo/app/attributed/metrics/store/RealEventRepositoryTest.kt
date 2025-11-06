/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.attributed.metrics.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.attributed.metrics.FakeAttributedMetricsDateUtils
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RealEventRepositoryTest {
    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var db: AttributedMetricsDatabase
    private lateinit var eventDao: EventDao
    private lateinit var testDateProvider: FakeAttributedMetricsDateUtils
    private lateinit var repository: RealEventRepository

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AttributedMetricsDatabase::class.java,
                ).build()
        eventDao = db.eventDao()
        testDateProvider = FakeAttributedMetricsDateUtils(LocalDate.of(2025, 10, 3))
        repository =
            RealEventRepository(
                eventDao = eventDao,
                attributedMetricsDateUtils = testDateProvider,
                appCoroutineScope = coroutineTestRule.testScope,
                dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenCollectEventFirstTimeForTodayThenInsertNewRecord() =
        runTest {
            testDateProvider.testDate = LocalDate.of(2025, 10, 3)

            repository.collectEvent("test_event")

            val events = eventDao.getEventsByNameAndTimeframe("test_event", "2025-10-03", "2025-10-03")
            assert(events.size == 1)
            assert(events[0].count == 1)
            assert(events[0].eventName == "test_event")
            assert(events[0].day == "2025-10-03")
        }

    @Test
    fun whenCollectEventMultipleTimesForTodayThenIncrementCount() =
        runTest {
            testDateProvider.testDate = LocalDate.of(2025, 10, 3)

            repository.collectEvent("test_event")
            repository.collectEvent("test_event")
            repository.collectEvent("test_event")

            val events = eventDao.getEventsByNameAndTimeframe("test_event", "2025-10-03", "2025-10-03")
            assert(events.size == 1)
            assert(events[0].count == 3)
        }

    @Test
    fun whenGetEventStatsWithNoEventsThenReturnZeros() =
        runTest {
            testDateProvider.testDate = LocalDate.of(2025, 10, 3)

            val stats = repository.getEventStats("test_event", days = 7)

            assert(stats.daysWithEvents == 0)
            assert(stats.totalEvents == 0)
            assert(stats.rollingAverage == 0.0)
        }

    @Test
    fun whenGetEventStatsWithDataOnEveryDayThenCalculateCorrectlyUsingPreviousDaysWindow() =
        runTest {
            // Setup data for 3 days
            testDateProvider.testDate = LocalDate.of(2025, 10, 8)
            eventDao.insertEvent(EventEntity("test_event", count = 3, day = "2025-10-08"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-07"))
            eventDao.insertEvent(EventEntity("test_event", count = 2, day = "2025-10-06"))
            eventDao.insertEvent(EventEntity("test_event", count = 3, day = "2025-10-05"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-04"))
            eventDao.insertEvent(EventEntity("test_event", count = 2, day = "2025-10-03"))
            eventDao.insertEvent(EventEntity("test_event", count = 3, day = "2025-10-02"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-01"))

            val stats = repository.getEventStats("test_event", days = 7)

            assert(stats.daysWithEvents == 7)
            assert(stats.totalEvents == 13)
            assert(stats.rollingAverage == 13.0 / 7.0)
        }

    @Test
    fun whenGetEventStatsWithMissingDaysDataThenCalculateCorrectlyUsingPreviousDaysWindow() =
        runTest {
            // Setup data for 3 days
            testDateProvider.testDate = LocalDate.of(2025, 10, 8)
            eventDao.insertEvent(EventEntity("test_event", count = 3, day = "2025-10-08"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-07"))
            eventDao.insertEvent(EventEntity("test_event", count = 2, day = "2025-10-06"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-04"))
            eventDao.insertEvent(EventEntity("test_event", count = 2, day = "2025-10-03"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-01"))

            val stats = repository.getEventStats("test_event", days = 7)

            assert(stats.daysWithEvents == 5)
            assert(stats.totalEvents == 7)
            assert(stats.rollingAverage == 7.0 / 7.0)
        }

    @Test
    fun whenDeleteAllEventsThenRemoveAllEvents() =
        runTest {
            // Setup data
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-03"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-10-02"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2025-09-03"))

            repository.deleteAllEvents()

            val remainingEvents = eventDao.getEventsByNameAndTimeframe("test_event", "2025-09-03", "2025-10-03")
            assert(remainingEvents.isEmpty())
        }
}
