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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
 * All dates are handled in Eastern Time (ET) and formatted as "yyyy-MM-dd" for consistency.
 * This format is used for both storage and calculations. The timezone ensures that day
 * boundaries align with business operations in ET.
 *
 * Example usage:
 * ```
 * // Get today's date in ET
 * val today = dateUtils.getCurrentDate() // returns "2025-10-03" (if it's Oct 3rd in ET)
 *
 * // Get a date 7 days ago in ET
 * val lastWeek = dateUtils.getDateMinusDays(7) // returns "2025-09-26"
 *
 * // Calculate days since a specific date in ET
 * // Note: The calculation uses ET midnight as the boundary for day changes
 * val daysSince = dateUtils.daysSince("2025-09-01") // returns number of days
 * ```
 *
 * Note: All date operations use Eastern Time (ET) timezone. This means:
 * - Day changes occur at midnight ET
 * - Date comparisons and calculations are based on ET dates
 * - The returned date strings represent dates in ET
 */
interface AttributedMetricsDateUtils {
    /**
     * Gets the current date in Eastern Time formatted as "yyyy-MM-dd".
     *
     * @return The current date in ET as a string in the format "yyyy-MM-dd"
     */
    fun getCurrentDate(): String

    /**
     * Calculates the number of days between a given date and the current date in Eastern Time.
     * Day boundaries are determined using midnight ET.
     *
     * @param date The reference date in "yyyy-MM-dd" format (interpreted in ET)
     * @return The number of days between the reference date and current date.
     *         Positive if the reference date is in the past,
     *         negative if it's in the future,
     *         zero if it's today.
     */
    fun daysSince(date: String): Int

    /**
     * Calculates the number of days between a given timestamp and the current date in Eastern Time.
     * Day boundaries are determined using midnight ET.
     *
     * @param timestamp The reference timestamp in milliseconds since epoch (Unix timestamp)
     * @return The number of days between the reference timestamp and current date.
     *         Positive if the reference timestamp is in the past,
     *         negative if it's in the future,
     *         zero if it's today.
     */
    fun daysSince(timestamp: Long): Int

    /**
     * Gets a date that is a specified number of days before the current date in Eastern Time.
     * Day boundaries are determined using midnight ET.
     *
     * @param days The number of days to subtract from the current date
     * @return The calculated date as a string in "yyyy-MM-dd" format (in ET)
     */
    fun getDateMinusDays(days: Int): String
}

@ContributesBinding(AppScope::class)
class RealAttributedMetricsDateUtils @Inject constructor() : AttributedMetricsDateUtils {
    override fun getCurrentDate(): String = getCurrentZonedDateTime().format(DATE_FORMATTER)

    override fun daysSince(date: String): Int {
        // Parse the input date and set it to start of day (midnight) in ET
        val initDate = ZonedDateTime.of(
            LocalDate.parse(date, DATE_FORMATTER),
            LocalTime.MIDNIGHT,
            ET_ZONE,
        )
        return ChronoUnit.DAYS.between(initDate, getCurrentZonedDateTime()).toInt()
    }

    override fun daysSince(timestamp: Long): Int {
        val etZone = ZoneId.of("America/New_York")
        val installInstant = Instant.ofEpochMilli(timestamp)
        val nowInstant = Instant.now()

        val installInEt = installInstant.atZone(etZone)
        val nowInEt = nowInstant.atZone(etZone)

        return ChronoUnit.DAYS.between(installInEt.toLocalDate(), nowInEt.toLocalDate()).toInt()
    }

    override fun getDateMinusDays(days: Int): String = getCurrentZonedDateTime().minusDays(days.toLong()).format(DATE_FORMATTER)

    private fun getCurrentZonedDateTime(): ZonedDateTime = ZonedDateTime.now(ET_ZONE)

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ET_ZONE = ZoneId.of("America/New_York")
    }
}
