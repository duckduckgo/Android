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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Utility interface for handling date operations in the Attributed Metrics feature.
 *
 * This interface provides methods for:
 * - Getting the current date in a standardized format
 * - Calculating days between dates
 * - Generating dates relative to the current date
 *
 * All dates are handled in the format "yyyy-MM-dd" for consistency across the feature.
 * This format is used for both storage and calculations.
 *
 * Example usage:
 * ```
 * // Get today's date
 * val today = dateUtils.getCurrentDate() // returns "2025-10-03"
 *
 * // Get a date 7 days ago
 * val lastWeek = dateUtils.getDateMinusDays(7) // returns "2025-09-26"
 *
 * // Calculate days since a specific date
 * val daysSince = dateUtils.daysSince("2025-09-01") // returns number of days
 * ```
 */
interface AttributedMetricsDateUtils {
    /**
     * Gets the current date formatted as "yyyy-MM-dd".
     *
     * @return The current date as a string in the format "yyyy-MM-dd"
     */
    fun getCurrentDate(): String

    /**
     * Calculates the number of days between a given date and the current date.
     *
     * @param date The reference date in "yyyy-MM-dd" format
     * @return The number of days between the reference date and current date.
     *         Positive if the reference date is in the past,
     *         negative if it's in the future,
     *         zero if it's today.
     */
    fun daysSince(date: String): Int

    /**
     * Gets a date that is a specified number of days before the current date.
     *
     * @param days The number of days to subtract from the current date
     * @return The calculated date as a string in "yyyy-MM-dd" format
     */
    fun getDateMinusDays(days: Int): String
}

@ContributesBinding(AppScope::class)
class RealAttributedMetricsDateUtils @Inject constructor() : AttributedMetricsDateUtils {
    override fun getCurrentDate(): String = getCurrentLocalDate().format(DATE_FORMATTER)

    override fun daysSince(date: String): Int {
        val initDate = LocalDate.parse(date, DATE_FORMATTER)
        return ChronoUnit.DAYS.between(initDate, getCurrentLocalDate()).toInt()
    }

    override fun getDateMinusDays(days: Int): String = getCurrentLocalDate().minusDays(days.toLong()).format(DATE_FORMATTER)

    private fun getCurrentLocalDate(): LocalDate = LocalDate.now()

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
