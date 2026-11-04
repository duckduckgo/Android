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
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition.Version
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.ClaimsParseError
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Expired
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidAudience
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidClaim
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidIssuer
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidScope
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.InvalidSignature
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.JwksParseError
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.KeyNotFound
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Malformed
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.MissingClaim
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.NotYetValid
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Other
import com.duckduckgo.subscriptions.impl.auth.TokenValidationFailureReason.Unsupported
import com.duckduckgo.subscriptions.impl.repository.TokenRefreshHistory
import com.duckduckgo.verifiedinstallation.IsVerifiedPlayStoreInstall
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

interface AuthTokenRefreshWideEvent {
    suspend fun onStart(
        subscriptionStatus: SubscriptionStatus,
        subscriptionPlatform: String?,
        refreshHistory: TokenRefreshHistory,
    )

    suspend fun onCrossProcessLockAcquired(result: Result<Closeable>)
    suspend fun onTokenRead()
    suspend fun onTokenReadFailed(storeError: TokenStoreError)
    suspend fun onJwksFetched()
    suspend fun onTokensFetched()
    suspend fun onBackendErrorResponse(
        backendErrorResponse: String?,
        backendErrorCode: Int?,
    )

    suspend fun onTokensValidated()
    suspend fun onAccessTokenValidationFailed(reason: TokenValidationFailureReason)
    suspend fun onRefreshTokenValidationFailed(reason: TokenValidationFailureReason)
    suspend fun onUnknownAccountError()
    suspend fun onPlayLoginSuccess()
    suspend fun onPlayLoginFailure(
        signedOut: Boolean,
        refreshException: Exception,
        loginError: String,
    )

    suspend fun onTokensSaved()
    suspend fun onTokenSaveFailed(storeError: TokenStoreError)
    suspend fun onSkipped()
    suspend fun onSuccess()
    suspend fun onFailure(e: Exception)
}

enum class TokenStoreError(val paramValue: String) {
    UNAVAILABLE("unavailable"),
    MISSING("missing"),
    UNREADABLE("unreadable"),
    WRITE_FAILED("write_failed"),
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AuthTokenRefreshWideEventImpl @Inject constructor(
    private val wideEventClient: WideEventClient,
    private val subscriptionsFeature: Lazy<SubscriptionsFeature>,
    private val dispatchers: DispatcherProvider,
    private val networkProtectionState: Lazy<NetworkProtectionState>,
    private val isVerifiedPlayStoreInstall: Lazy<IsVerifiedPlayStoreInstall>,
    private val timeProvider: CurrentTimeProvider,
    @ProcessName val processName: String,
) : AuthTokenRefreshWideEvent {

    private var ongoingTokenRefreshWideEventId: Long? = null

    override suspend fun onStart(
        subscriptionStatus: SubscriptionStatus,
        subscriptionPlatform: String?,
        refreshHistory: TokenRefreshHistory,
    ) {
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
                    KEY_SUBSCRIPTION_PLATFORM to subscriptionPlatform.toPlatformParamValue(),
                    KEY_NETP_IS_ENABLED to runCatching { networkProtectionState.value.isEnabled().toString() }.getOrDefault(""),
                    KEY_NETP_IS_RUNNING to runCatching { networkProtectionState.value.isRunning().toString() }.getOrDefault(""),
                    KEY_PROCESS_NAME to processName,
                    KEY_VERIFIED_INSTALL to withContext(dispatchers.io()) { isVerifiedPlayStoreInstall.value().toString() },
                    KEY_CONSECUTIVE_FAILURES to refreshHistory.failureStreak.toFailureStreakBucket().toString(),
                    KEY_TIME_SINCE_LAST_SUCCESS to refreshHistory.lastSuccessAt.toHoursSinceBucket(timeProvider.currentTimeMillis()).toString(),
                    KEY_LAST_REFRESH_PROCESS to refreshHistory.lastSuccessProcess.toProcessParamValue(),
                ),
                definition = WideEventDefinition(version = Version(minor = 1, patch = 3)),
            )
            .getOrNull()
            ?.also { wideEventId ->
                wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
                wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_LOCK_WAIT, buckets = LOCK_WAIT_BUCKETS)
            }
    }

    override suspend fun onCrossProcessLockAcquired(result: Result<Closeable>) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_LOCK_WAIT)
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_LOCK_ACQUIRE,
                success = result.isSuccess,
                metadata = mapOf(KEY_LOCK_OUTCOME to result.toOutcomeValue()),
            )
        }
    }

    private fun Result<Closeable>.toOutcomeValue(): String = when {
        isSuccess -> "acquired"
        exceptionOrNull() is TimeoutCancellationException -> "timeout"
        else -> "error"
    }

    override suspend fun onTokenRead() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_TOKEN_READ)
            wideEventClient.intervalStart(wideEventId = wideEventId, key = INTERVAL_GET_JWKS)
        }
    }

    override suspend fun onTokenReadFailed(storeError: TokenStoreError) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_TOKEN_READ,
                success = false,
                metadata = mapOf(KEY_STORE_ERROR to storeError.paramValue),
            )
        }
    }

    override suspend fun onTokensSaved() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(wideEventId = wideEventId, stepName = STEP_TOKEN_SAVE)
        }
    }

    override suspend fun onTokenSaveFailed(storeError: TokenStoreError) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_TOKEN_SAVE,
                success = false,
                metadata = mapOf(KEY_STORE_ERROR to storeError.paramValue),
            )
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

    override suspend fun onAccessTokenValidationFailed(reason: TokenValidationFailureReason) {
        onTokenValidationFailed(failedToken = "access", reason = reason)
    }

    override suspend fun onRefreshTokenValidationFailed(reason: TokenValidationFailureReason) {
        onTokenValidationFailed(failedToken = "refresh", reason = reason)
    }

    private suspend fun onTokenValidationFailed(
        failedToken: String,
        reason: TokenValidationFailureReason,
    ) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_TOKEN_VALIDATION,
                success = false,
                metadata = buildMap {
                    put(KEY_TOKEN_VALIDATION_FAILED_TOKEN, failedToken)
                    put(KEY_TOKEN_VALIDATION_FAILURE_REASON, reason.toParamValue())
                    reason.clockSkew?.let { put(KEY_TOKEN_VALIDATION_CLOCK_SKEW, it.toBucketedSeconds().toString()) }
                },
            )
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
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_PLAY_LOGIN,
                metadata = mapOf(
                    KEY_RECOVERY_STRATEGY to RECOVERY_STRATEGY_PLAY_LOGIN,
                    KEY_RECOVERY_OUTCOME to RECOVERY_OUTCOME_RECOVERED,
                ),
            )
        }
    }

    override suspend fun onPlayLoginFailure(
        signedOut: Boolean,
        refreshException: Exception,
        loginError: String,
    ) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_PLAY_LOGIN,
                success = false,
                metadata = mapOf(
                    KEY_PLAY_LOGIN_ERROR to loginError,
                    KEY_RECOVERY_STRATEGY to RECOVERY_STRATEGY_PLAY_LOGIN,
                    KEY_RECOVERY_OUTCOME to if (signedOut) RECOVERY_OUTCOME_SIGNED_OUT else RECOVERY_OUTCOME_DEFERRED,
                ),
            )
            wideEventClient.intervalEnd(wideEventId = wideEventId, key = INTERVAL_TOTAL_DURATION)
            wideEventClient.flowFinish(
                wideEventId = wideEventId,
                status = FlowStatus.Failure(reason = refreshException.toErrorString()),
                metadata = mapOf(KEY_SIGNED_OUT to signedOut.toString()),
            )
            ongoingTokenRefreshWideEventId = null
        }
    }

    override suspend fun onSkipped() {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowAbort(wideEventId = wideEventId)
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

    override suspend fun onBackendErrorResponse(
        backendErrorResponse: String?,
        backendErrorCode: Int?,
    ) {
        if (!isFeatureEnabled()) return
        ongoingTokenRefreshWideEventId?.let { wideEventId ->
            wideEventClient.flowStep(
                wideEventId = wideEventId,
                stepName = STEP_TOKEN_REQUEST,
                success = false,
                metadata = buildMap {
                    backendErrorResponse?.let { put(KEY_BACKEND_ERROR_RESPONSE, it) }
                    backendErrorCode?.let { put(KEY_BACKEND_ERROR_CODE, it.toString()) }
                },
            )
        }
    }

    private suspend fun isFeatureEnabled(): Boolean = withContext(dispatchers.io()) {
        subscriptionsFeature.value.sendAuthTokenRefreshWideEvent().isEnabled()
    }

    private companion object {
        const val AUTH_TOKEN_REFRESH_FEATURE_NAME = "auth-token-refresh"

        const val STEP_LOCK_ACQUIRE = "lock_acquire"
        const val STEP_TOKEN_READ = "token_read"
        const val STEP_JWKS_FETCH = "jwks_fetch"
        const val STEP_TOKEN_REQUEST = "token_request"
        const val STEP_TOKEN_VALIDATION = "token_validation"
        const val STEP_PLAY_LOGIN = "play_login"
        const val STEP_TOKEN_SAVE = "token_save"

        const val INTERVAL_LOCK_WAIT = "token_refresh_lock_wait_ms_bucketed"
        const val INTERVAL_GET_JWKS = "fetch_jwks_latency_ms_bucketed"
        const val INTERVAL_GET_TOKENS = "refresh_access_token_latency_ms_bucketed"
        const val INTERVAL_TOTAL_DURATION = "total_duration_ms_bucketed"

        val LOCK_WAIT_BUCKETS = setOf(100.milliseconds, 500.milliseconds, 1.seconds, 3.seconds, 10.seconds, 30.seconds, 60.seconds)

        const val KEY_LOCK_OUTCOME = "lock_outcome"
        const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
        const val KEY_BACKEND_ERROR_RESPONSE = "backend_error_response"
        const val KEY_BACKEND_ERROR_CODE = "backend_error_code"
        const val KEY_PLAY_LOGIN_ERROR = "play_login_error"
        const val KEY_SIGNED_OUT = "signed_out"
        const val KEY_NETP_IS_ENABLED = "netp_is_enabled"
        const val KEY_NETP_IS_RUNNING = "netp_is_running"
        const val KEY_PROCESS_NAME = "process_name"
        const val KEY_TOKEN_VALIDATION_FAILED_TOKEN = "token_validation_failed_token"
        const val KEY_TOKEN_VALIDATION_FAILURE_REASON = "token_validation_failure_reason"
        const val KEY_TOKEN_VALIDATION_CLOCK_SKEW = "token_validation_clock_skew_s_bucketed"
        const val KEY_SUBSCRIPTION_PLATFORM = "subscription_platform"
        const val KEY_VERIFIED_INSTALL = "verified_install"
        const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures_bucket"
        const val KEY_TIME_SINCE_LAST_SUCCESS = "time_since_last_success_h_bucketed"
        const val KEY_LAST_REFRESH_PROCESS = "last_refresh_process"
        const val KEY_STORE_ERROR = "store_error"
        const val KEY_RECOVERY_STRATEGY = "recovery_strategy"
        const val KEY_RECOVERY_OUTCOME = "recovery_outcome"

        const val RECOVERY_STRATEGY_PLAY_LOGIN = "play_login"
        const val RECOVERY_OUTCOME_RECOVERED = "recovered"
        const val RECOVERY_OUTCOME_DEFERRED = "deferred"
        const val RECOVERY_OUTCOME_SIGNED_OUT = "signed_out"
    }
}

private fun Exception.toErrorString(): String = javaClass.simpleName

private val KNOWN_PROCESS_NAMES = setOf("main", ":vpn", ":pir", ":autofill", ":fire")

private fun String?.toProcessParamValue(): String = if (this in KNOWN_PROCESS_NAMES) this!! else "unknown"

private fun String?.toPlatformParamValue(): String = when (this?.lowercase()) {
    "google" -> "google"
    "stripe" -> "stripe"
    "apple", "ios" -> "apple"
    else -> "unknown"
}

private val FAILURE_STREAK_BUCKETS = listOf(0, 1, 2, 3, 5, 10, 20, 50)

private fun Int.toFailureStreakBucket(): Int = FAILURE_STREAK_BUCKETS.last { it <= this.coerceAtLeast(0) }

private val HOURS_SINCE_SUCCESS_BUCKETS = listOf(0L, 1L, 4L, 24L, 72L, 168L, 720L)

private fun Instant?.toHoursSinceBucket(nowMillis: Long): Long {
    if (this == null) return -1
    val hours = ((nowMillis - toEpochMilli()) / MILLIS_PER_HOUR).coerceAtLeast(0)
    return HOURS_SINCE_SUCCESS_BUCKETS.last { it <= hours }
}

private const val MILLIS_PER_HOUR = 3_600_000L

private val CLOCK_SKEW_BUCKETS = listOf(
    Duration.ZERO,
    1.seconds,
    2.seconds,
    5.seconds,
    15.seconds,
    30.seconds,
    1.minutes,
    2.minutes,
    5.minutes,
    10.minutes,
    30.minutes,
    1.hours,
    2.hours,
    4.hours,
    8.hours,
    24.hours,
)

private fun TokenValidationFailureReason.toParamValue(): String = when (this) {
    is Expired -> "expired"
    is NotYetValid -> "not_yet_valid"
    InvalidSignature -> "invalid_signature"
    KeyNotFound -> "key_not_found"
    JwksParseError -> "jwks_parse_error"
    Malformed -> "malformed"
    Unsupported -> "unsupported"
    MissingClaim -> "missing_claim"
    InvalidIssuer -> "invalid_issuer"
    InvalidAudience -> "invalid_audience"
    InvalidScope -> "invalid_scope"
    InvalidClaim -> "invalid_claim"
    ClaimsParseError -> "claims_parse_error"
    Other -> "other"
}

private val TokenValidationFailureReason.clockSkew: Duration?
    get() = when (this) {
        is Expired -> clockSkew
        is NotYetValid -> clockSkew
        else -> null
    }

/**
 * Rounds the magnitude down to the nearest bucket boundary, keeping the sign.
 */
private fun Duration.toBucketedSeconds(): Long {
    val bucket = CLOCK_SKEW_BUCKETS.last { it <= absoluteValue }.inWholeSeconds
    return if (isNegative()) -bucket else bucket
}
