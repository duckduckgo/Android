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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.COOL_DOWN_DAYS
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.DISMISS_STREAK_RESET_DAYS
import com.duckduckgo.brokensite.impl.SharedPreferencesDuckPlayerDataStore.Keys.MAX_DISMISS_STREAK
import com.duckduckgo.brokensite.impl.di.BrokenSitePrompt
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface BrokenSitePomptDataStore {
    suspend fun setMaxDismissStreak(maxDismissStreak: Int)
    fun getMaxDismissStreak(): Int

    suspend fun setDismissStreakResetDays(days: Int)
    fun getDismissStreakResetDays(): Int

    suspend fun setCoolDownDays(days: Int)
    fun getCoolDownDays(): Int
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDuckPlayerDataStore @Inject constructor(
    @BrokenSitePrompt private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : BrokenSitePomptDataStore {

    private object Keys {
        val MAX_DISMISS_STREAK = intPreferencesKey(name = "MAX_DISMISS_STREAK")
        val DISMISS_STREAK_RESET_DAYS = intPreferencesKey(name = "DISMISS_STREAK_RESET_DAYS")
        val COOL_DOWN_DAYS = intPreferencesKey(name = "COOL_DOWN_DAYS")
    }

    private val maxDismissStreak: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[MAX_DISMISS_STREAK] ?: 3
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 3)

    private val dismissStreakResetDays: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[DISMISS_STREAK_RESET_DAYS] ?: 30
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 30)

    private val coolDownDays: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[COOL_DOWN_DAYS] ?: 7
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 7)

    override suspend fun setMaxDismissStreak(maxDismissStreak: Int) {
        store.edit { prefs -> prefs[MAX_DISMISS_STREAK] = maxDismissStreak }
    }

    override fun getMaxDismissStreak(): Int = maxDismissStreak.value

    override suspend fun setDismissStreakResetDays(days: Int) {
        store.edit { prefs -> prefs[DISMISS_STREAK_RESET_DAYS] = days }
    }

    override fun getDismissStreakResetDays(): Int = dismissStreakResetDays.value

    override suspend fun setCoolDownDays(days: Int) {
        store.edit { prefs -> prefs[COOL_DOWN_DAYS] = days }
    }

    override fun getCoolDownDays(): Int = coolDownDays.value
}