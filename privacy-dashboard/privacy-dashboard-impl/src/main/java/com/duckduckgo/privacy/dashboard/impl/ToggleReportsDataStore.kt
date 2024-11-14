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

package com.duckduckgo.privacy.dashboard.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_DISMISS_INTERVAL
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_MAX_PROMPT_COUNT
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPTS_DISMISSED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPT_INTERVAL
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_RC
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_SENT
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

interface ToggleReportsDataStore {
    fun getToggleReportsRemoteConfigJson(): String

    suspend fun setToggleReportsRemoteConfigJson(value: String)

    suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean)

    suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean)

    suspend fun storeDismissInterval(dismissInterval: Int)

    suspend fun storePromptInterval(promptInterval: Int)

    suspend fun storeMaxPromptCount(maxPromptCount: Int)

    suspend fun insertTogglePromptDismiss()

    suspend fun insertTogglePromptSend()

    fun canPrompt(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesToggleReportsDataStore @Inject constructor(
    @ToggleReports private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ToggleReportsDataStore {

    private object Keys {
        val TOGGLE_REPORTS_RC = stringPreferencesKey(name = "TOGGLE_REPORTS_RC")
        val TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED = booleanPreferencesKey(name = "TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED")
        val TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED = booleanPreferencesKey(name = "TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED")
        val TOGGLE_REPORTS_SENT = stringSetPreferencesKey(name = "TOGGLE_REPORTS_SENT")
        val TOGGLE_REPORTS_PROMPTS_DISMISSED = stringSetPreferencesKey(name = "TOGGLE_REPORTS_PROMPTS_DISMISSED")
        val TOGGLE_REPORTS_DISMISS_INTERVAL = intPreferencesKey(name = "TOGGLE_REPORTS_DISMISS_INTERVAL")
        val TOGGLE_REPORTS_PROMPT_INTERVAL = intPreferencesKey(name = "TOGGLE_REPORTS_PROMPT_INTERVAL")
        val TOGGLE_REPORTS_MAX_PROMPT_COUNT = intPreferencesKey(name = "TOGGLE_REPORTS_MAX_PROMPT_COUNT")
    }

    private val toggleReportsRC: StateFlow<String> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_RC] ?: "{}"
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "{}")

    private val promptLimitLogicEnabled: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED] ?: true
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val dismissLogicEnabled: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED] ?: true
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val reportsSent: StateFlow<List<String>> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_SENT]?.toList() ?: listOf()
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, listOf())

    private val reportPromptsDismissed: StateFlow<List<String>> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED]?.toList() ?: listOf()
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, listOf())

    // Default to 48 hours
    private val reportDismissInterval: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_INTERVAL] ?: 172800
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 172800)

    // Default to 48 hours
    private val reportPromptInterval: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_INTERVAL] ?: 172800
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 172800)

    // Default to max 3 accepted prompts
    private val maxPromptCount: StateFlow<Int> = store.data
        .map { prefs ->
            prefs[TOGGLE_REPORTS_MAX_PROMPT_COUNT] ?: 3
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, 3)

    override fun getToggleReportsRemoteConfigJson(): String {
        return toggleReportsRC.value
    }

    override suspend fun setToggleReportsRemoteConfigJson(value: String) {
        store.edit { prefs -> prefs[TOGGLE_REPORTS_RC] = value }
    }

    override suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED] = dismissLogicEnabled
        }
    }

    override suspend fun storeDismissInterval(dismissInterval: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_INTERVAL] = dismissInterval
        }
    }

    override suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED] = promptLimitLogicEnabled
        }
    }

    override suspend fun storePromptInterval(promptInterval: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_INTERVAL] = promptInterval
        }
    }

    override suspend fun storeMaxPromptCount(maxPromptCount: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_MAX_PROMPT_COUNT] = maxPromptCount
        }
    }

    override suspend fun insertTogglePromptDismiss() {
        store.edit { prefs ->
            val currentSet = reportPromptsDismissed.value.toMutableSet()
            val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
            val interval = reportDismissInterval.value
            currentSet.removeAll { storedTimestamp ->
                val storedTime = storedTimestamp.toLong()
                (currentTimeMillis - storedTime > interval * 1000L)
            }
            val dismissTime = DatabaseDateFormatter.millisIso8601().toString()
            currentSet.add(dismissTime)
            Timber.v("KateTest--> dismissTime stored: $dismissTime")
            prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED] = currentSet
        }
    }

    override suspend fun insertTogglePromptSend() {
        store.edit { prefs ->
            val currentSet = reportsSent.value.toMutableSet()
            val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
            val interval = reportPromptInterval.value
            currentSet.removeAll { storedTimestamp ->
                val storedTime = storedTimestamp.toLong()
                (currentTimeMillis - storedTime > interval * 1000L)
            }
            val sendTime = DatabaseDateFormatter.millisIso8601().toString()
            Timber.v("KateTest--> sendTime stored: $sendTime")
            currentSet.add(sendTime)
            prefs[TOGGLE_REPORTS_SENT] = currentSet
        }
    }

    override fun canPrompt(): Boolean {
        val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
        Timber.v("KateTest--> currentTime as DBdateFormatter.MillisIso08601: $currentTimeMillis")
        fun checkRecentSends() = reportsSent.value.count { sendTime ->
            currentTimeMillis - sendTime.toLong() <= reportPromptInterval.value * 1000L
        } < maxPromptCount.value
        Timber.v("KateTest--> checkRecentSends: ${checkRecentSends()} WITH " +
            "reportPromptInterval: ${reportPromptInterval.value} AND" +
            " reportsSentCount: ${reportsSent.value.count()}")
        fun checkDismissInterval() = currentTimeMillis - (reportPromptsDismissed.value.maxOrNull()?.toLong() ?: 0) >
            reportDismissInterval.value * 1000L
        Timber.v("KateTest--> checkDismissInterval: ${checkDismissInterval()} WITH " +
            "reportDismissInterval: ${reportDismissInterval.value} AND reportPromptsDismissed:" +
            " ${reportPromptsDismissed.value.maxOrNull()}")
        return when {
            promptLimitLogicEnabled.value && dismissLogicEnabled.value ->
                checkRecentSends() && checkDismissInterval()
            promptLimitLogicEnabled.value -> checkRecentSends()
            dismissLogicEnabled.value -> checkDismissInterval()
            else -> true
        }
    }
}
