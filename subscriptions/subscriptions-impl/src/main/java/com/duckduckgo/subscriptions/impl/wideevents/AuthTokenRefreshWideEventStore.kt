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

package com.duckduckgo.subscriptions.impl.wideevents

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * What [AuthTokenRefreshWideEvent] reads and records about the token refresh. Nothing else should depend on it,
 * so that the token refresh never relies on data kept only for telemetry.
 */
interface AuthTokenRefreshWideEventStore {
    suspend fun getRefreshHistory(): TokenRefreshHistory
    suspend fun recordRefreshSuccess(
        at: Instant,
        processName: String,
    )

    suspend fun recordRefreshFailure()
    suspend fun clearRefreshHistory()
    suspend fun isTokenStoreAvailable(): Boolean
    suspend fun hasRefreshTokenEntry(): Boolean
}

data class TokenRefreshHistory(
    val failureStreak: Int,
    val lastSuccessAt: Instant?,
    val lastSuccessProcess: String? = null,
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAuthTokenRefreshWideEventStore @Inject constructor(
    private val subscriptionsDataStore: SubscriptionsDataStore,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : AuthTokenRefreshWideEventStore {

    // Kept apart from the token store so the history stays readable when the token store is not
    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true)
    }

    override suspend fun getRefreshHistory(): TokenRefreshHistory = withContext(dispatcherProvider.io()) {
        TokenRefreshHistory(
            failureStreak = preferences.getInt(KEY_FAILURE_STREAK, 0),
            lastSuccessAt = preferences.getLong(KEY_LAST_SUCCESS_AT, 0L).takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) },
            lastSuccessProcess = preferences.getString(KEY_LAST_SUCCESS_PROCESS, null),
        )
    }

    override suspend fun recordRefreshSuccess(
        at: Instant,
        processName: String,
    ) = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) {
            putInt(KEY_FAILURE_STREAK, 0)
            putLong(KEY_LAST_SUCCESS_AT, at.toEpochMilli())
            putString(KEY_LAST_SUCCESS_PROCESS, processName)
        }
    }

    override suspend fun recordRefreshFailure() = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) {
            putInt(KEY_FAILURE_STREAK, preferences.getInt(KEY_FAILURE_STREAK, 0) + 1)
        }
    }

    override suspend fun clearRefreshHistory() = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) { clear() }
    }

    override suspend fun isTokenStoreAvailable(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.isAvailable()
    }

    override suspend fun hasRefreshTokenEntry(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.hasRefreshTokenV2Entry()
    }

    private companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.wideevents.auth_token_refresh"
        const val KEY_FAILURE_STREAK = "KEY_FAILURE_STREAK"
        const val KEY_LAST_SUCCESS_AT = "KEY_LAST_SUCCESS_AT"
        const val KEY_LAST_SUCCESS_PROCESS = "KEY_LAST_SUCCESS_PROCESS"
    }
}
