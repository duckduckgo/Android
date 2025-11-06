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

package com.duckduckgo.app.attributed.metrics.store

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class RealAttributedMetricsDateUtilsTest {

    private lateinit var testee: RealAttributedMetricsDateUtils

    @Before
    fun setup() {
        testee = RealAttributedMetricsDateUtils()
    }

    @Test
    fun whenGetCurrentDateThenReturnsFormattedDateInET() {
        val result = testee.getCurrentDate()

        // Verify it matches the expected format yyyy-MM-dd
        val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
        assertTrue("Date should match yyyy-MM-dd format", dateRegex.matches(result))

        // Verify it's parseable
        val parsedDate = LocalDate.parse(result, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        assertTrue("Date should be parseable", parsedDate != null)
    }

    @Test
    fun whenGetDateFromTimestampThenReturnsDateInET() {
        // 2024-01-01 00:00:00 UTC (which is 2023-12-31 19:00:00 EST)
        val timestamp = 1704067200000L

        val result = testee.getDateFromTimestamp(timestamp)

        assertEquals("2023-12-31", result)
    }

    @Test
    fun whenGetDateFromTimestampForMiddayUTCThenReturnsCorrectDateInET() {
        // 2024-06-15 12:00:00 UTC (which is 2024-06-15 08:00:00 EDT)
        val timestamp = 1718452800000L

        val result = testee.getDateFromTimestamp(timestamp)

        assertEquals("2024-06-15", result)
    }

    @Test
    fun whenGetDateFromTimestampForMidnightETThenReturnsCorrectDate() {
        // 2024-07-04 04:00:00 UTC (which is 2024-07-04 00:00:00 EDT)
        val timestamp = 1720065600000L

        val result = testee.getDateFromTimestamp(timestamp)

        assertEquals("2024-07-04", result)
    }

    @Test
    fun whenGetDateMinusDaysThenReturnsDateInPast() {
        val currentDate = testee.getCurrentDate()
        val sevenDaysAgo = testee.getDateMinusDays(7)

        val current = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val past = LocalDate.parse(sevenDaysAgo, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        assertEquals(7, current.toEpochDay() - past.toEpochDay())
    }

    @Test
    fun whenGetDateMinusDaysWithZeroThenReturnsCurrentDate() {
        val currentDate = testee.getCurrentDate()
        val result = testee.getDateMinusDays(0)

        assertEquals(currentDate, result)
    }

    @Test
    fun whenGetDateMinusDaysWithOneThenReturnsYesterday() {
        val currentDate = testee.getCurrentDate()
        val yesterday = testee.getDateMinusDays(1)

        val current = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val past = LocalDate.parse(yesterday, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        assertEquals(1, current.toEpochDay() - past.toEpochDay())
    }

    @Test
    fun whenDaysSinceWithDateStringThenReturnsPositiveForPastDate() {
        val sevenDaysAgo = testee.getDateMinusDays(7)

        val result = testee.daysSince(sevenDaysAgo)

        assertEquals(7, result)
    }

    @Test
    fun whenDaysSinceWithDateStringForTodayThenReturnsZero() {
        val result = testee.daysSince(testee.getCurrentDate())

        assertEquals(0, result)
    }

    @Test
    fun whenGetDateMinusDaysWithLargeNumberThenReturnsCorrectDate() {
        val currentDate = testee.getCurrentDate()
        val thirtyDaysAgo = testee.getDateMinusDays(30)

        val current = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val past = LocalDate.parse(thirtyDaysAgo, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        assertEquals(30, current.toEpochDay() - past.toEpochDay())
    }

    @Test
    fun whenDaysSinceWithDateStringForPastMonthThenReturnsCorrectDays() {
        val thirtyDaysAgo = testee.getDateMinusDays(30)

        val result = testee.daysSince(thirtyDaysAgo)

        assertEquals(30, result)
    }
}
