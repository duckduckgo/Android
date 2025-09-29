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
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class RealEventRepositoryTest {
    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var db: AttributedMetricsDatabase
    private lateinit var eventDao: EventDao
    private lateinit var testDateProvider: FakeDateProvider
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
        testDateProvider = FakeDateProvider()
        repository =
            RealEventRepository(
                eventDao = eventDao,
                dateProvider = testDateProvider,
                coroutineScope = TestScope(coroutineTestRule.testDispatcher),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenCollectEventFirstTimeForTodayThenInsertNewRecord() =
        runTest {
            testDateProvider.setCurrentDate("2024-01-01")

            repository.collectEvent("test_event")

            val events = eventDao.getEventsByNameAndTimeframe("test_event", "2024-01-01")
            assert(events.size == 1)
            assert(events[0].count == 1)
            assert(events[0].eventName == "test_event")
            assert(events[0].day == "2024-01-01")
        }

    @Test
    fun whenCollectEventMultipleTimesForTodayThenIncrementCount() =
        runTest {
            testDateProvider.setCurrentDate("2024-01-01")

            repository.collectEvent("test_event")
            repository.collectEvent("test_event")
            repository.collectEvent("test_event")

            val events = eventDao.getEventsByNameAndTimeframe("test_event", "2024-01-01")
            assert(events.size == 1)
            assert(events[0].count == 3)
        }

    @Test
    fun whenGetEventStatsWithNoEventsThenReturnZeros() =
        runTest {
            testDateProvider.setCurrentDate("2024-01-01")

            val stats = repository.getEventStats("test_event", days = 7)

            assert(stats.daysWithEvents == 0)
            assert(stats.totalEvents == 0)
            assert(stats.rollingAverage == 0.0)
        }

    @Test
    fun whenGetEventStatsThenCalculateCorrectly() =
        runTest {
            // Setup data for 3 days
            testDateProvider.setCurrentDate("2024-01-01")
            eventDao.insertEvent(EventEntity("test_event", count = 2, day = "2024-01-01"))
            eventDao.insertEvent(EventEntity("test_event", count = 3, day = "2023-12-31"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2023-12-30"))

            val stats = repository.getEventStats("test_event", days = 7)

            assert(stats.daysWithEvents == 3)
            assert(stats.totalEvents == 6)
            assert(stats.rollingAverage == 6.0 / 7.0)
        }

    @Test
    fun whenDeleteOldEventsThenRemoveOnlyOlderThanSpecified() =
        runTest {
            // Setup data
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2024-01-01"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2023-12-31"))
            eventDao.insertEvent(EventEntity("test_event", count = 1, day = "2023-12-25"))

            testDateProvider.setCurrentDate("2024-01-01")
            repository.deleteOldEvents(olderThanDays = 5)

            val remainingEvents = eventDao.getEventsByNameAndTimeframe("test_event", "2023-12-25")
            assert(remainingEvents.size == 2)
            assert(remainingEvents.none { it.day == "2023-12-25" })
        }
}

class FakeDateProvider : DateProvider {
    private var currentDate = "2024-01-01"

    fun setCurrentDate(date: String) {
        currentDate = date
    }

    override fun getCurrentDate(): String = currentDate

    override fun getDateMinusDays(days: Int): String =
        LocalDate.parse(currentDate).minusDays(days.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
