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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_MAX_PROMPT_COUNT
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPTS_DISMISSED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_SENT
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

interface ToggleReportsDataStore {

    suspend fun getDismissLogicEnabled(): Boolean

    suspend fun getPromptLimitLogicEnabled(): Boolean

    suspend fun getDismissInterval(): Int

    suspend fun getPromptInterval(): Int

    suspend fun getMaxPromptCount(): Int

    suspend fun insertTogglePromptDismiss()

    suspend fun getReportsSent(): List<String>

    suspend fun insertTogglePromptSend()

    suspend fun getPromptsDismissed(): List<String>

    suspend fun lastPromptWasAccepted(): Boolean

    suspend fun setLastPromptWasAccepted(accepted: Boolean)

    suspend fun canPrompt(): Boolean
}

@JsonClass(generateAdapter = true)
data class ToggleReportsSetting(
    @field:Json(name = "dismissLogicEnabled")
    val dismissLogicEnabled: Boolean,
    @field:Json(name = "dismissInterval")
    val dismissInterval: Int,
    @field:Json(name = "promptLimitLogicEnabled")
    val promptLimitLogicEnabled: Boolean,
    @field:Json(name = "promptInterval")
    val promptInterval: Int,
    @field:Json(name = "maxPromptCount")
    val maxPromptCount: Int,
)

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesToggleReportsDataStore @Inject constructor(
    @ToggleReports private val store: DataStore<Preferences>,
    private val moshi: Moshi,
    private val toggleReportsFeature: ToggleReportsFeature,
    private val dispatcherProvider: DispatcherProvider,
) : ToggleReportsDataStore {

    private val jsonAdapter by lazy {
        moshi.adapter(ToggleReportsSetting::class.java)
    }

    private object Keys {
        val TOGGLE_REPORTS_SENT = stringSetPreferencesKey(name = "TOGGLE_REPORTS_SENT")
        val TOGGLE_REPORTS_PROMPTS_DISMISSED = stringSetPreferencesKey(name = "TOGGLE_REPORTS_PROMPTS_DISMISSED")
        val TOGGLE_REPORTS_MAX_PROMPT_COUNT = intPreferencesKey(name = "TOGGLE_REPORTS_MAX_PROMPT_COUNT")
        val TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED = booleanPreferencesKey(name = "TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED")
    }

    private companion object {
        const val DEFAULT_INTERVAL = 172800
        const val DEFAULT_MAX_PROMPT = 3
    }

    override suspend fun getDismissLogicEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext toggleReportsFeature.self().getSettings()?.let { jsonAdapter.fromJson(it) }?.dismissLogicEnabled ?: false
    }

    override suspend fun getDismissInterval(): Int = withContext(dispatcherProvider.io()) {
        return@withContext toggleReportsFeature.self().getSettings()?.let { jsonAdapter.fromJson(it) }?.dismissInterval ?: DEFAULT_INTERVAL
    }

    override suspend fun getPromptLimitLogicEnabled(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext toggleReportsFeature.self().getSettings()?.let { jsonAdapter.fromJson(it) }?.promptLimitLogicEnabled ?: false
    }

    override suspend fun getPromptInterval(): Int = withContext(dispatcherProvider.io()) {
        return@withContext toggleReportsFeature.self().getSettings()?.let { jsonAdapter.fromJson(it) }?.promptInterval ?: DEFAULT_INTERVAL
    }

    override suspend fun getMaxPromptCount(): Int = withContext(dispatcherProvider.io()) {
        return@withContext store.data.first()[TOGGLE_REPORTS_MAX_PROMPT_COUNT] ?: DEFAULT_MAX_PROMPT
    }

    override suspend fun insertTogglePromptDismiss() {
        withContext(dispatcherProvider.io()) {
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
    }

    override suspend fun getPromptsDismissed(): List<String> = withContext(dispatcherProvider.io()) {
        return@withContext store.data.first().let { prefs ->
            prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED]?.toList() ?: emptyList()
        }
    }

    override suspend fun insertTogglePromptSend() {
        withContext(dispatcherProvider.io()) {
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
    }

    override suspend fun getReportsSent(): List<String> = withContext(dispatcherProvider.io()) {
        return@withContext store.data.first().let { prefs ->
            prefs[TOGGLE_REPORTS_SENT]?.toList() ?: emptyList()
        }
    }

    override suspend fun lastPromptWasAccepted(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext store.data.first()[TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED] ?: false
    }

    override suspend fun setLastPromptWasAccepted(accepted: Boolean) {
        Timber.v("KateTest--> lastPromptAccepted stored: $accepted")
        withContext(dispatcherProvider.io()) {
            store.edit {
                    prefs ->
                prefs[TOGGLE_REPORTS_LAST_PROMPT_WAS_ACCEPTED] = accepted
            }
        }
    }

    override suspend fun canPrompt(): Boolean = withContext(dispatcherProvider.io()) {
        val currentTimeMillis = DatabaseDateFormatter.millisIso8601()

        suspend fun checkRecentSends(): Boolean {
            val reportsSent = getReportsSent()
            val promptInterval = getPromptInterval()
            val maxPromptCount = getMaxPromptCount()
            return reportsSent.count { sendTime ->
                currentTimeMillis - sendTime.toLong() <= promptInterval * 1000L
            } < maxPromptCount
        }

        suspend fun checkDismissInterval(): Boolean {
            val promptsDismissed = getPromptsDismissed()
            val dismissInterval = getDismissInterval()
            return currentTimeMillis - (promptsDismissed.maxOfOrNull { it.toLong() } ?: 0) >
                dismissInterval * 1000L
        }

        return@withContext when {
            getPromptLimitLogicEnabled() && getDismissLogicEnabled() ->
                checkRecentSends() && checkDismissInterval()
            getPromptLimitLogicEnabled() -> checkRecentSends()
            getDismissLogicEnabled() -> checkDismissInterval()
            else -> true
        }
    }
}
