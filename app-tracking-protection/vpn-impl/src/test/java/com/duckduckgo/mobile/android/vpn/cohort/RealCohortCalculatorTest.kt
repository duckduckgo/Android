/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.cohort

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.IsoFields

class RealCohortCalculatorTest {

    private lateinit var cohortCalculator: CohortCalculator

    @Before
    fun setup() {
        cohortCalculator = RealCohortCalculator()
    }

    @Test
    fun whenLocalDateIsFirstDayOfLastDayOf2021ThenReturnWeeklyCohort() {
        val date = LocalDate.of(2022, 1, 1)
        val now = date.plusWeeks(3)

        val cohort = cohortCalculator.calculateCohortForDate(date, now)
        val success = (cohort == "2021-week-52") || (cohort == "2022-JANUARY") || (cohort == "2022-q1") || (cohort == "2022-h1")
        assertEquals("2021-week-52", cohort)
    }

    @Test
    fun whenLocalDateIsFirstDayOfLastDayOf2021ThenReturnMonthlyCohort() {
        val date = LocalDate.of(2022, 1, 1)
        val now = date.plusWeeks(12)

        val cohort = cohortCalculator.calculateCohortForDate(date, now)
        assertEquals("2022-JANUARY", cohort)
    }

    @Test
    fun whenLocalDateIsFirstDayOfLastDayOf2021ThenReturnQuarterCohort() {
        val date = LocalDate.of(2022, 1, 1)
        val now = date.plusWeeks(25)

        val cohort = cohortCalculator.calculateCohortForDate(date, now)
        assertEquals("2022-q1", cohort)
    }

    @Test
    fun whenLocalDateIsFirstDayOfLastDayOf2021ThenReturnHalfYearCohort() {
        val date = LocalDate.of(2022, 1, 1)
        val now = date.plusWeeks(51)

        val cohort = cohortCalculator.calculateCohortForDate(date, now)
        assertEquals("2022-h1", cohort)
    }

    @Test
    fun whenLocalDateIsFirstDayOfLastDayOf2021ThenReturnUniqueCohort() {
        val date = LocalDate.of(2022, 1, 1)
        val now = date.plusWeeks(53)

        val cohort = cohortCalculator.calculateCohortForDate(date, now)
        assertEquals("-", cohort)
    }

    @Test
    fun whenLocalDateNowThenReturnWeeklyCohort() {
        val date = LocalDate.now()
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        assertEquals("$year-week-${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDate4WeeksAgoThenReturnWeeklyCohort() {
        val date = LocalDate.now().minusWeeks(4)
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        assertEquals("$year-week-${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDateMoreThan4WeeksAgoThenReturnMonthCohort() {
        val date = LocalDate.now().minusWeeks(5)
        val year = date.year
        assertEquals("$year-${date.month}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDate13WeeksAgoThenReturnMonthCohort() {
        val date = LocalDate.now().minusWeeks(13)
        val year = date.year
        assertEquals("$year-${date.month}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDateMoreThan13WeeksAgoThenReturnQuarterCohort() {
        val date = LocalDate.now().minusWeeks(14)
        val year = date.year
        assertEquals("$year-q${date.get(IsoFields.QUARTER_OF_YEAR)}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDate26WeeksAgoThenReturnQuarterCohort() {
        val date = LocalDate.now().minusWeeks(26)
        val year = date.year
        assertEquals("$year-q${date.get(IsoFields.QUARTER_OF_YEAR)}", cohortCalculator.calculateCohortForDate(date))
    }

    @Test
    fun whenLocalDateMoreThan26WeeksAgoThenReturnHalfCohort() {
        val date = LocalDate.now().minusWeeks(27)
        val year = date.year
        val half = if (date.monthValue > 6) "2" else "1"
        assertEquals("$year-h$half", cohortCalculator.calculateCohortForDate(date))
    }
}
