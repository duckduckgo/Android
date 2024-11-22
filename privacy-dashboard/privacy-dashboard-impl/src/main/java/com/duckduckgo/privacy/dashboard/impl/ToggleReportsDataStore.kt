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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_DISMISS_INTERVAL
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_MAX_PROMPT_COUNT
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPTS_DISMISSED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPT_INTERVAL
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_SENT
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import timber.log.Timber

interface ToggleReportsDataStore {

    suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean)

    suspend fun getDismissLogicEnabled(): Boolean

    suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean)

    suspend fun getPromptLimitLogicEnabled(): Boolean

    suspend fun storeDismissInterval(dismissInterval: Int)

    suspend fun getDismissInterval(): Int

    suspend fun storePromptInterval(promptInterval: Int)

    suspend fun getPromptInterval(): Int

    suspend fun storeMaxPromptCount(maxPromptCount: Int)

    suspend fun getMaxPromptCount(): Int

    suspend fun insertTogglePromptDismiss()

    suspend fun getReportsSent(): List<String>

    suspend fun insertTogglePromptSend()

    suspend fun getPromptsDismissed(): List<String>

    suspend fun lastPromptWasAccepted(): Boolean

    suspend fun setLastPromptWasAccepted(accepted: Boolean)

    suspend fun canPrompt(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesToggleReportsDataStore @Inject constructor(
    @ToggleReports private val store: DataStore<Preferences>,
    // @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ToggleReportsDataStore {

    private object Keys {
        val TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED = booleanPreferencesKey(name = "TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED")
        val TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED = booleanPreferencesKey(name = "TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED")
        val TOGGLE_REPORTS_SENT = stringSetPreferencesKey(name = "TOGGLE_REPORTS_SENT")
        val TOGGLE_REPORTS_PROMPTS_DISMISSED = stringSetPreferencesKey(name = "TOGGLE_REPORTS_PROMPTS_DISMISSED")
        val TOGGLE_REPORTS_DISMISS_INTERVAL = intPreferencesKey(name = "TOGGLE_REPORTS_DISMISS_INTERVAL")
        val TOGGLE_REPORTS_PROMPT_INTERVAL = intPreferencesKey(name = "TOGGLE_REPORTS_PROMPT_INTERVAL")
        val TOGGLE_REPORTS_MAX_PROMPT_COUNT = intPreferencesKey(name = "TOGGLE_REPORTS_MAX_PROMPT_COUNT")
        val TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED = booleanPreferencesKey(name = "TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED")
    }

    private companion object {
        const val DEFAULT_INTERVAL = 172800
        const val DEFAULT_MAX_PROMPT = 3
    }

    override suspend fun storeDismissLogicEnabled(dismissLogicEnabled: Boolean) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED] = dismissLogicEnabled
        }
    }

    override suspend fun getDismissLogicEnabled(): Boolean {
        return store.data.first()[TOGGLE_REPORTS_DISMISS_LOGIC_ENABLED] ?: false
    }

    override suspend fun storeDismissInterval(dismissInterval: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_DISMISS_INTERVAL] = dismissInterval
        }
    }

    override suspend fun getDismissInterval(): Int {
        return store.data.first()[TOGGLE_REPORTS_DISMISS_INTERVAL] ?: DEFAULT_INTERVAL
    }

    override suspend fun storePromptLimitLogicEnabled(promptLimitLogicEnabled: Boolean) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED] = promptLimitLogicEnabled
        }
    }

    override suspend fun getPromptLimitLogicEnabled(): Boolean {
        return store.data.first()[TOGGLE_REPORTS_PROMPT_LIMIT_LOGIC_ENABLED] ?: false
    }

    override suspend fun storePromptInterval(promptInterval: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_PROMPT_INTERVAL] = promptInterval
        }
    }

    override suspend fun getPromptInterval(): Int {
        return store.data.first()[TOGGLE_REPORTS_PROMPT_INTERVAL] ?: DEFAULT_INTERVAL
    }

    override suspend fun storeMaxPromptCount(maxPromptCount: Int) {
        store.edit { prefs ->
            prefs[TOGGLE_REPORTS_MAX_PROMPT_COUNT] = maxPromptCount
        }
    }

    override suspend fun getMaxPromptCount(): Int {
        return store.data.first()[TOGGLE_REPORTS_MAX_PROMPT_COUNT] ?: DEFAULT_MAX_PROMPT
    }

    override suspend fun insertTogglePromptDismiss() {
        store.edit { prefs ->
            val currentSet = getPromptsDismissed().toMutableSet()
            val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
            val interval = getDismissInterval()
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

    override suspend fun getPromptsDismissed(): List<String> {
        return store.data.first().let { prefs ->
            prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED]?.toList() ?: emptyList()
        }
    }

    override suspend fun insertTogglePromptSend() {
        store.edit { prefs ->
            val currentSet = getReportsSent().toMutableSet()
            val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
            val interval = getPromptInterval()
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

    override suspend fun getReportsSent(): List<String> {
        return store.data.first().let { prefs ->
            prefs[TOGGLE_REPORTS_SENT]?.toList() ?: emptyList()
        }
    }

    override suspend fun lastPromptWasAccepted(): Boolean {
        Timber.v("KateTest--> lastPromptWasAccepted called in datastore")
        return store.data.first()[TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED] ?: false
    }

    override suspend fun setLastPromptWasAccepted(accepted: Boolean) {
        Timber.v("KateTest--> lastPromptAccepted stored: $accepted")
        store.edit {
                prefs ->
            prefs[TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED] = accepted
        }
    }

    override suspend fun canPrompt(): Boolean {
        val currentTimeMillis = DatabaseDateFormatter.millisIso8601()
        Timber.v("KateTest--> currentTime as DBdateFormatter.MillisIso08601: $currentTimeMillis")

        suspend fun checkRecentSends(): Boolean {
            val reportsSent = getReportsSent()
            val promptInterval = getPromptInterval()
            val maxPromptCount = getMaxPromptCount()
            return reportsSent.count { sendTime ->
                currentTimeMillis - sendTime.toLong() <= promptInterval * 1000L
            } < maxPromptCount
        }

        Timber.v(
            "KateTest--> checkRecentSends: ${checkRecentSends()} WITH " +
                "reportPromptInterval: ${getPromptInterval()} AND" +
                " reportsSentCount: ${getReportsSent().count()}",
        )

        suspend fun checkDismissInterval(): Boolean {
            val promptsDismissed = getPromptsDismissed()
            val dismissInterval = getDismissInterval()
            return currentTimeMillis - (promptsDismissed.maxOfOrNull { it.toLong() } ?: 0) >
                dismissInterval * 1000L
        }

        Timber.v(
            "KateTest--> checkDismissInterval: ${checkDismissInterval()} WITH " +
                "reportDismissInterval: ${getDismissInterval()} AND reportPromptsDismissed:" +
                " ${getPromptsDismissed().maxOfOrNull { it }}",
        )

        return when {
            getPromptLimitLogicEnabled() && getDismissLogicEnabled() ->
                checkRecentSends() && checkDismissInterval()
            getPromptLimitLogicEnabled() -> checkRecentSends()
            getDismissLogicEnabled() -> checkDismissInterval()
            else -> true
        }
    }
}
