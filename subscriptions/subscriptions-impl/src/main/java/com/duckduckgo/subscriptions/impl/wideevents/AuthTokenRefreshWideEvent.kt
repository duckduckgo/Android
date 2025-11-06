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

package com.duckduckgo.subscriptions.impl.wideevents

import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy.OnProcessStart
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AuthTokenRefreshWideEvent {
    suspend fun onStart(subscriptionStatus: SubscriptionStatus)
    suspend fun onTokenRead()
    suspend fun onJwksFetched()
    suspend fun onTokensFetched()
    suspend fun onBackendErrorResponse(backendErrorResponse: String)
    suspend fun onTokensValidated()
    suspend fun onUnknownAccountError()
    suspend fun onPlayLoginSuccess()
    suspend fun onPlayLoginFailure(signedOut: Boolean, refreshException: Exception, loginError: String)
    suspend fun onSuccess()
    suspend fun onFailure(e: Exception)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthTokenRefreshWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val privacyProFeature: Lazy<PrivacyProFeature>,
    private val dispatchers: DispatcherProvider,
    private val networkProtectionState: Lazy<NetworkProtectionState>,
    @ProcessName val processName: String,
) : AuthTokenRefreshWideEvent {

    private var ongoingTokenRefreshWideEventId: Long? = null

    override suspend fun onStart(subscriptionStatus: SubscriptionStatus) {
        if (!isFeatureEnabled()) return

        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Unknown)
        }

        ongoingTokenRefreshWideEventId = wideEventClient
            .flowStart(
                name = AUTH_TOKEN_REFRESH_FEATURE_NAME,
                cleanupPolicy = OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
                metadata = mapOf(
                    KEY_SUBSCRIPTION_STATUS to subscriptionStatus.statusName,
                    KEY_NETP_IS_ENABLED to runCatching { networkProtectionState.get().isEnabled().toString() }.getOrDefault(""),
                    KEY_NETP_IS_RUNNING to runCatching { networkProtectionState.get().isRunning().toString() }.getOrDefault(""),
                    KEY_PROCESS_NAME to processName,
                ),
            )
            .getOrNull()
            ?.also { wideEventId ->
                wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            }
    }

    override suspend fun onTokenRead() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_TOKEN_READ)
            wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_GET_JWKS)
        }
    }

    override suspend fun onJwksFetched() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_GET_JWKS)
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_JWKS_FETCH)
            wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_GET_TOKENS)
        }
    }

    override suspend fun onTokensFetched() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_GET_TOKENS)
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_TOKEN_REQUEST)
        }
    }

    override suspend fun onTokensValidated() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_TOKEN_VALIDATION)
        }
    }

    override suspend fun onUnknownAccountError() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Cancelled)
            ongoingTokenRefreshWideEventId = null
        }
    }

    override suspend fun onPlayLoginSuccess() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_PLAY_LOGIN)
        }
    }

    override suspend fun onPlayLoginFailure(signedOut: Boolean, refreshException: Exception, loginError: String) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_PLAY_LOGIN,
                success = false,
                metadata = mapOf(KEY_PLAY_LOGIN_ERROR to loginError),
            )
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Failure(reason = refreshException.toErrorString()))
            ongoingTokenRefreshWideEventId = null
        }
    }

    override suspend fun onSuccess() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Success)
            ongoingTokenRefreshWideEventId = null
        }
    }

    override suspend fun onFailure(e: Exception) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(wideEventId = wideEventId, status = FlowStatus.Failure(reason = e.toErrorString()))
            ongoingTokenRefreshWideEventId = null
        }
    }

    override suspend fun onBackendErrorResponse(backendErrorResponse: String) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_TOKEN_REQUEST,
                success = false,
                metadata = mapOf(KEY_BACKEND_ERROR_RESPONSE to backendErrorResponse),
            )
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        privacyProFeature.get().sendAuthTokenRefreshWideEvent().isEnabled()
    }

    private companion object {
        const val AUTH_TOKEN_REFRESH_FEATURE_NAME = "auth-token-refresh"

        const val STEP_TOKEN_READ = "token_read"
        const val STEP_JWKS_FETCH = "jwks_fetch"
        const val STEP_TOKEN_REQUEST = "token_request"
        const val STEP_TOKEN_VALIDATION = "token_validation"
        const val STEP_PLAY_LOGIN = "play_login"

        const val INTERVAL_GET_JWKS = "fetch_jwks_latency_ms_bucketed"
        const val INTERVAL_GET_TOKENS = "refresh_access_token_latency_ms_bucketed"
        const val INTERVAL_TOTAL_DURATION = "total_duration_ms_bucketed"

        const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
        const val KEY_BACKEND_ERROR_RESPONSE = "backend_error_response"
        const val KEY_PLAY_LOGIN_ERROR = "play_login_error"
        const val KEY_NETP_IS_ENABLED = "netp_is_enabled"
        const val KEY_NETP_IS_RUNNING = "netp_is_running"
        const val KEY_PROCESS_NAME = "process_name"
    }
}

private fun Exception.toErrorString(): String =
    listOfNotNull(javaClass.simpleName, message).joinToString(": ")
