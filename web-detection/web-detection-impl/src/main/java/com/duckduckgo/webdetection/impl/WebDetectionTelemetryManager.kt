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

package com.duckduckgo.webdetection.impl

import android.content.SharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.webdetection.api.WebDetectionPixels
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Manages telemetry for web detection (e.g., adwall detection).
 *
 * This class handles:
 * - Incrementing daily and weekly counters
 * - Firing daily and weekly pixels
 */
interface WebDetectionTelemetryManager {
    /**
     * Handle a telemetry event from a detector.
     * @param type The type of detection (e.g., "adwall")
     * @param detectorId The full detector ID (e.g., "adwalls.generic")
     */
    fun handleTelemetry(type: String, detectorId: String)

    /**
     * Fire daily and weekly pixels if needed.
     * Should be called periodically (e.g., on app launch).
     */
    fun firePixelsIfNeeded()
}

@ContributesBinding(AppScope::class)
class RealWebDetectionTelemetryManager @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val pixels: WebDetectionPixels,
    private val featureFlags: WebDetectionFeatureFlags,
) : WebDetectionTelemetryManager {

    override fun handleTelemetry(type: String, detectorId: String) {
        if (type != TYPE_ADWALL) return

        incrementAdwallCounters()
    }

    override fun firePixelsIfNeeded() {
        if (!featureFlags.adwallTelemetryPixelEnabled()) return

        fireDailyPixelIfNeeded()
        fireWeeklyPixelIfNeeded()
    }

    private fun incrementAdwallCounters() {
        val currentDaily = sharedPreferences.getInt(KEY_DAILY_COUNT, 0)
        val currentWeekly = sharedPreferences.getInt(KEY_WEEKLY_COUNT, 0)

        sharedPreferences.edit()
            .putInt(KEY_DAILY_COUNT, currentDaily + 1)
            .putInt(KEY_WEEKLY_COUNT, currentWeekly + 1)
            .apply()
    }

    private fun fireDailyPixelIfNeeded() {
        val today = LocalDate.now()
        val lastDailyDateString = sharedPreferences.getString(KEY_LAST_DAILY_DATE, null)

        if (lastDailyDateString != null) {
            val lastDate = LocalDate.parse(lastDailyDateString)
            if (lastDate == today) return // Already fired today
        }

        val count = sharedPreferences.getInt(KEY_DAILY_COUNT, 0)

        // Fire pixel if count > 0 or zero-count pixels are enabled
        if (count > 0 || featureFlags.adwallZeroCountPixelEnabled()) {
            pixels.fireAdwallDailyPixel(count)
        }

        // Reset daily counter and update last pixel date
        sharedPreferences.edit()
            .putInt(KEY_DAILY_COUNT, 0)
            .putString(KEY_LAST_DAILY_DATE, today.toString())
            .apply()
    }

    private fun fireWeeklyPixelIfNeeded() {
        val today = LocalDate.now()
        val lastWeeklyDateString = sharedPreferences.getString(KEY_LAST_WEEKLY_DATE, null)

        if (lastWeeklyDateString != null) {
            val lastDate = LocalDate.parse(lastWeeklyDateString)
            val daysSinceLastPixel = ChronoUnit.DAYS.between(lastDate, today)
            if (daysSinceLastPixel < 7) return // Not yet a week
        }

        val count = sharedPreferences.getInt(KEY_WEEKLY_COUNT, 0)

        // Fire pixel if count > 0 or zero-count pixels are enabled
        if (count > 0 || featureFlags.adwallZeroCountPixelEnabled()) {
            pixels.fireAdwallWeeklyPixel(count)
        }

        // Reset weekly counter and update last pixel date
        sharedPreferences.edit()
            .putInt(KEY_WEEKLY_COUNT, 0)
            .putString(KEY_LAST_WEEKLY_DATE, today.toString())
            .apply()
    }

    companion object {
        private const val TYPE_ADWALL = "adwall"
        private const val KEY_DAILY_COUNT = "webdetection_adwall_daily_count"
        private const val KEY_WEEKLY_COUNT = "webdetection_adwall_weekly_count"
        private const val KEY_LAST_DAILY_DATE = "webdetection_adwall_last_daily_date"
        private const val KEY_LAST_WEEKLY_DATE = "webdetection_adwall_last_weekly_date"
    }
}
