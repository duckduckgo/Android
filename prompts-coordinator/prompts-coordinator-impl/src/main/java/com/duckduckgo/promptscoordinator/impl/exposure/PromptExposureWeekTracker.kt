/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.promptscoordinator.impl.exposure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.impl.di.PromptsCoordinatorStore
import dagger.SingleInstanceIn
import javax.inject.Inject

/**
 * Owns the per-install-week counters behind the prompt exposure pixels.
 *
 * Nothing is scheduled: every operation first rolls the week over if the install has entered a new
 * one, inside the same [DataStore.edit] transaction. Transactions are serialised, so whichever caller
 * runs first in a new week does the rollover and the others see it done; ordering never matters.
 *
 * Every operation returns null, and changes nothing, when the install age is unknown.
 */
@SingleInstanceIn(AppScope::class)
class PromptExposureWeekTracker @Inject constructor(
    @PromptsCoordinatorStore private val store: DataStore<Preferences>,
    private val appInstall: AppInstall,
) {

    data class AppOpen(
        val weekIndex: Long,
        val daysSinceInstall: Long,
        val opensPrevWeek: Int,
    )

    data class Exposure(
        val daysSinceInstall: Long,
        val nthInWeek: Int,
        val opensPrevWeek: Int,
    )

    /** Counts a foreground of the app. Only app opens feed `opens_prev_week`. */
    suspend fun recordAppOpen(): AppOpen? = update { prefs, days ->
        prefs[OPENS_CURRENT_WEEK_KEY] = (prefs[OPENS_CURRENT_WEEK_KEY] ?: 0) + 1
        AppOpen(
            weekIndex = checkNotNull(prefs[WEEK_INDEX_KEY]),
            daysSinceInstall = days,
            opensPrevWeek = prefs[OPENS_PREV_WEEK_KEY] ?: 0,
        )
    }

    /** Counts a prompt shown this week and returns its ordinal, starting at 1. */
    suspend fun recordPromptShown(): Exposure? = update { prefs, days -> countExposure(prefs, days) }

    /**
     * Counts the New Tab Page card for [messageId] unless it was already counted this week, in which
     * case nothing changes and null is returned.
     */
    suspend fun recordNtpCardShown(messageId: String): Exposure? = update { prefs, days ->
        val reported = prefs[REPORTED_NTP_CARD_IDS_KEY].orEmpty()
        if (messageId in reported) return@update null
        prefs[REPORTED_NTP_CARD_IDS_KEY] = reported + messageId
        countExposure(prefs, days)
    }

    private fun countExposure(prefs: MutablePreferences, days: Long): Exposure {
        val nth = (prefs[NTH_IN_WEEK_KEY] ?: 0) + 1
        prefs[NTH_IN_WEEK_KEY] = nth
        return Exposure(
            daysSinceInstall = days,
            nthInWeek = nth,
            opensPrevWeek = prefs[OPENS_PREV_WEEK_KEY] ?: 0,
        )
    }

    private suspend fun <T> update(block: (MutablePreferences, Long) -> T?): T? {
        val days = appInstall.getInstallAge()?.inWholeDays ?: return null
        var result: T? = null
        store.edit { prefs ->
            rollIfNeeded(prefs, weekIndexOf(days))
            result = block(prefs, days)
        }
        return result
    }

    private fun rollIfNeeded(prefs: MutablePreferences, currentWeekIndex: Long) {
        val storedWeekIndex = prefs[WEEK_INDEX_KEY]
        // Same week, or the clock moved back: stay in the stored week rather than wipe its counts.
        if (storedWeekIndex != null && currentWeekIndex <= storedWeekIndex) return

        // Skipped weeks had no opens: carrying a stale count forward would misclassify returning users.
        val isNextWeek = storedWeekIndex != null && currentWeekIndex == storedWeekIndex + 1
        prefs[OPENS_PREV_WEEK_KEY] = if (isNextWeek) prefs[OPENS_CURRENT_WEEK_KEY] ?: 0 else 0
        prefs[OPENS_CURRENT_WEEK_KEY] = 0
        prefs[NTH_IN_WEEK_KEY] = 0
        prefs.remove(REPORTED_NTP_CARD_IDS_KEY)
        prefs[WEEK_INDEX_KEY] = currentWeekIndex
    }

    private companion object {
        val WEEK_INDEX_KEY = longPreferencesKey("week_index")
        val NTH_IN_WEEK_KEY = intPreferencesKey("nth_in_week")
        val OPENS_CURRENT_WEEK_KEY = intPreferencesKey("opens_current_week")
        val OPENS_PREV_WEEK_KEY = intPreferencesKey("opens_prev_week")
        val REPORTED_NTP_CARD_IDS_KEY = stringSetPreferencesKey("reported_ntp_card_ids")
    }
}
