/*
 * Copyright (c) 2021 DuckDuckGo
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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.ChronoUnit
import org.threeten.bp.temporal.IsoFields
import javax.inject.Inject

interface CohortCalculator {
    fun calculateCohortForDate(localDate: LocalDate): String
}

@ContributesBinding(AppScope::class)
class RealCohortCalculator @Inject constructor() : CohortCalculator {

    override fun calculateCohortForDate(localDate: LocalDate): String {
        val weeksSinceDate = ChronoUnit.WEEKS.between(localDate, LocalDate.now())

        return when {
            weeksSinceDate <= WEEKS_TO_MONTHLY_COHORT -> {
                weeklyCohortName(localDate)
            }
            weeksSinceDate <= WEEKS_TO_QUARTER_COHORT -> {
                monthlyCohortName(localDate)
            }
            weeksSinceDate <= WEEKS_TO_HALF_COHORT -> {
                quarterCohortName(localDate)
            }
            weeksSinceDate <= WEEKS_TO_SINGLE_COHORT -> {
                halfCohortName(localDate)
            }
            else -> {
                singleCohortName()
            }
        }
    }

    private fun weeklyCohortName(localDate: LocalDate): String {
        return "${localDate.year}-week-${weekOfYear(localDate)}"
    }

    private fun monthlyCohortName(localDate: LocalDate): String {
        return "${localDate.year}-${localDate.month}"
    }

    private fun quarterCohortName(localDate: LocalDate): String {
        return "${localDate.year}-q${localDate.get(IsoFields.QUARTER_OF_YEAR)}"
    }

    private fun halfCohortName(localDate: LocalDate): String {
        val half = if (localDate.monthValue > 6) "2" else "1"
        return "${localDate.year}-h$half"
    }

    private fun singleCohortName(): String {
        return "-"
    }

    private fun weekOfYear(localDate: LocalDate): Int {
        return localDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }

    companion object {
        private const val WEEKS_TO_MONTHLY_COHORT = 4
        private const val WEEKS_TO_QUARTER_COHORT = 13
        private const val WEEKS_TO_HALF_COHORT = 26
        private const val WEEKS_TO_SINGLE_COHORT = 52
    }
}
