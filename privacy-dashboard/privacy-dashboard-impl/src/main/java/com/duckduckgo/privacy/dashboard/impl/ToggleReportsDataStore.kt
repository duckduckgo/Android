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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_PROMPTS_DISMISSED
import com.duckduckgo.privacy.dashboard.impl.SharedPreferencesToggleReportsDataStore.Keys.TOGGLE_REPORTS_SENT
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

    suspend fun canPrompt(): Boolean
}

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

    private val jsonAdapter: JsonAdapter<ToggleReportsSetting> by lazy {
        moshi.newBuilder().add(KotlinJsonAdapterFactory()).build().adapter(ToggleReportsSetting::class.java)
    }

    private object Keys {
        val TOGGLE_REPORTS_SENT = stringSetPreferencesKey(name = "TOGGLE_REPORTS_SENT")
        val TOGGLE_REPORTS_PROMPTS_DISMISSED = stringSetPreferencesKey(name = "TOGGLE_REPORTS_PROMPTS_DISMISSED")
    }

    private companion object {
        const val DEFAULT_INTERVAL = 172800
        const val DEFAULT_MAX_PROMPT = 3
    }

    override suspend fun getDismissLogicEnabled(): Boolean {
        return toggleReportsFeature.self().getSettings()?.let {
            jsonAdapter.fromJson(it)
        }?.dismissLogicEnabled ?: false
    }

    override suspend fun getDismissInterval(): Int {
        return toggleReportsFeature.self().getSettings()?.let {
            jsonAdapter.fromJson(it)
        }?.dismissInterval ?: DEFAULT_INTERVAL
    }

    override suspend fun getPromptLimitLogicEnabled(): Boolean {
        return toggleReportsFeature.self().getSettings()?.let {
            jsonAdapter.fromJson(it)
        }?.promptLimitLogicEnabled ?: false
    }

    override suspend fun getPromptInterval(): Int {
        return toggleReportsFeature.self().getSettings()?.let {
            jsonAdapter.fromJson(it)
        }?.promptInterval ?: DEFAULT_INTERVAL
    }

    override suspend fun getMaxPromptCount(): Int {
        return toggleReportsFeature.self().getSettings()?.let {
            jsonAdapter.fromJson(it)
        }?.maxPromptCount ?: DEFAULT_MAX_PROMPT
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
                prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED] = currentSet
            }
        }
    }

    override suspend fun getPromptsDismissed(): List<String> {
        return store.data.firstOrNull()?.let { prefs ->
            prefs[TOGGLE_REPORTS_PROMPTS_DISMISSED]
        }?.toList() ?: emptyList()
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
                currentSet.add(sendTime)
                prefs[TOGGLE_REPORTS_SENT] = currentSet
            }
        }
    }

    override suspend fun getReportsSent(): List<String> {
        return store.data.firstOrNull()?.let { prefs ->
            prefs[TOGGLE_REPORTS_SENT]
        }?.toList() ?: emptyList()
    }

    override suspend fun canPrompt(): Boolean =
        withContext(dispatcherProvider.io()) {
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
