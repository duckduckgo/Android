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

package com.duckduckgo.brokensite.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.COOL_DOWN_DAYS
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.DISMISS_EVENTS
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.DISMISS_STREAK_RESET_DAYS
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.MAX_DISMISS_STREAK
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.NEXT_SHOWN_DATE
import com.duckduckgo.brokensite.impl.di.BrokenSitePrompt
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface BrokenSitePromptDataStore {
    suspend fun setMaxDismissStreak(maxDismissStreak: Int)
    suspend fun getMaxDismissStreak(): Int

    suspend fun setDismissStreakResetDays(days: Int)
    suspend fun getDismissStreakResetDays(): Int

    suspend fun setCoolDownDays(days: Long)
    suspend fun getCoolDownDays(): Long

    suspend fun addDismissal(dismissal: LocalDateTime)
    suspend fun clearAllDismissals()
    suspend fun getDismissalCountBetween(t1: LocalDateTime, t2: LocalDateTime): Int
    suspend fun deleteAllExpiredDismissals(expiryDate: String, zoneId: ZoneId)

    suspend fun setNextShownDate(nextShownDate: LocalDateTime?)
    suspend fun getNextShownDate(): LocalDateTime?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDuckPlayerDataStore @Inject constructor(
    @BrokenSitePrompt private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrokenSitePromptDataStore {

    private object Keys {
        val MAX_DISMISS_STREAK = intPreferencesKey(name = "MAX_DISMISS_STREAK")
        val DISMISS_STREAK_RESET_DAYS = intPreferencesKey(name = "DISMISS_STREAK_RESET_DAYS")
        val COOL_DOWN_DAYS = longPreferencesKey(name = "COOL_DOWN_DAYS")
        val DISMISS_EVENTS = stringSetPreferencesKey(name = "DISMISS_EVENTS")
        val NEXT_SHOWN_DATE = stringPreferencesKey(name = "NEXT_SHOWN_DATE")
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val maxDismissStreak: Flow<Int?> = store.data
        .map { prefs ->
            prefs[MAX_DISMISS_STREAK]
        }
        .distinctUntilChanged()

    private val dismissStreakResetDays: Flow<Int?> = store.data
        .map { prefs ->
            prefs[DISMISS_STREAK_RESET_DAYS]
        }
        .distinctUntilChanged()

    private val coolDownDays: Flow<Long?> = store.data
        .map { prefs ->
            prefs[COOL_DOWN_DAYS]
        }
        .distinctUntilChanged()

    private val nextShownDate: Flow<String?> = store.data
        .map { prefs ->
            prefs[NEXT_SHOWN_DATE]
        }
        .distinctUntilChanged()

    override suspend fun setMaxDismissStreak(maxDismissStreak: Int) {
        store.edit { prefs -> prefs[MAX_DISMISS_STREAK] = maxDismissStreak }
    }

    override suspend fun getMaxDismissStreak(): Int = maxDismissStreak.firstOrNull() ?: 3

    override suspend fun setDismissStreakResetDays(days: Int) {
        store.edit { prefs -> prefs[DISMISS_STREAK_RESET_DAYS] = days }
    }

    override suspend fun getDismissStreakResetDays(): Int = dismissStreakResetDays.firstOrNull() ?: 30

    override suspend fun setCoolDownDays(days: Long) {
        store.edit { prefs -> prefs[COOL_DOWN_DAYS] = days }
    }

    override suspend fun getCoolDownDays(): Long = coolDownDays.firstOrNull() ?: 7

    override suspend fun setNextShownDate(nextShownDate: LocalDateTime?) {
        store.edit { prefs ->

            nextShownDate?.let {
                prefs[NEXT_SHOWN_DATE] = formatter.format(nextShownDate)
            } ?: run {
                prefs.remove(NEXT_SHOWN_DATE)
            }
        }
    }

    override suspend fun addDismissal(dismissal: LocalDateTime) {
        store.edit { prefs ->
            prefs[DISMISS_EVENTS] = (prefs[DISMISS_EVENTS]?.toSet() ?: emptySet()).plus(formatter.format(dismissal))
        }
    }

    override suspend fun clearAllDismissals() {
        store.edit { prefs ->
            prefs.remove(DISMISS_EVENTS)
        }
    }

    override suspend fun getDismissalCountBetween(
        t1: LocalDateTime,
        t2: LocalDateTime,
    ): Int {
        val allDismissEvents = store.data.map { prefs ->
            prefs[DISMISS_EVENTS]?.toSet() ?: emptySet()
        }.firstOrNull() ?: emptySet()

        return allDismissEvents.count { dateString: String ->
            try {
                val eventDateTime = LocalDateTime.parse(dateString, formatter)
                eventDateTime.isAfter(t1) && eventDateTime.isBefore(t2)
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun deleteAllExpiredDismissals(expiryDate: String, zoneId: ZoneId) {
        val expiryInstant = Instant.parse(expiryDate)

        store.edit { prefs ->
            val allDismissEvents = prefs[DISMISS_EVENTS]?.toSet() ?: emptySet()

            val validDismissEvents = allDismissEvents.filterTo(mutableSetOf()) { dateString ->
                runCatching {
                    LocalDateTime.parse(dateString, formatter)
                        .atZone(zoneId)
                        .toInstant() > expiryInstant
                }.getOrDefault(false)
            }
            prefs[DISMISS_EVENTS] = validDismissEvents
        }
    }

    override suspend fun getNextShownDate(): LocalDateTime? {
        return nextShownDate.firstOrNull()?.let { LocalDateTime.parse(it, formatter) }
    }
}
