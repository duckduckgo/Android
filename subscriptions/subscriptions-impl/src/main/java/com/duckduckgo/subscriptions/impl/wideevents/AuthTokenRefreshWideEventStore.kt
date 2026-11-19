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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.store.SubscriptionsDataStore
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/**
 * What [AuthTokenRefreshWideEvent] reads and records about the token store. Nothing else should depend on it,
 * so that the token refresh never relies on data kept only for telemetry.
 */
interface AuthTokenRefreshWideEventStore {
    suspend fun getRefreshHistory(): TokenRefreshHistory
    suspend fun recordRefreshSuccess(
        at: Instant,
        processName: String,
    )

    suspend fun recordRefreshFailure()
    suspend fun isTokenStoreAvailable(): Boolean
    suspend fun hasRefreshTokenEntry(): Boolean
}

data class TokenRefreshHistory(
    val failureStreak: Int,
    val lastSuccessAt: Instant?,
    val lastSuccessProcess: String? = null,
)

@ContributesBinding(AppScope::class)
class RealAuthTokenRefreshWideEventStore @Inject constructor(
    private val subscriptionsDataStore: SubscriptionsDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : AuthTokenRefreshWideEventStore {

    override suspend fun getRefreshHistory(): TokenRefreshHistory = withContext(dispatcherProvider.io()) {
        TokenRefreshHistory(
            failureStreak = subscriptionsDataStore.tokenRefreshFailureStreak,
            lastSuccessAt = subscriptionsDataStore.lastTokenRefreshSuccessAt?.let { Instant.ofEpochMilli(it) },
            lastSuccessProcess = subscriptionsDataStore.lastTokenRefreshProcess,
        )
    }

    override suspend fun recordRefreshSuccess(
        at: Instant,
        processName: String,
    ) = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.tokenRefreshFailureStreak = 0
        subscriptionsDataStore.lastTokenRefreshSuccessAt = at.toEpochMilli()
        subscriptionsDataStore.lastTokenRefreshProcess = processName
    }

    override suspend fun recordRefreshFailure() = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.tokenRefreshFailureStreak += 1
    }

    override suspend fun isTokenStoreAvailable(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.isAvailable()
    }

    override suspend fun hasRefreshTokenEntry(): Boolean = withContext(dispatcherProvider.io()) {
        subscriptionsDataStore.hasRefreshTokenV2Entry()
    }
}
