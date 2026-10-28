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

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition
import com.duckduckgo.app.statistics.wideevents.WideEventDefinition.Version
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.io.Closeable
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AuthTokenRefreshWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val networkProtectionState: NetworkProtectionState = mock()

    @SuppressLint("DenyListedApi")
    private val subscriptionsFeature: SubscriptionsFeature =
        FakeFeatureToggleFactory
            .create(SubscriptionsFeature::class.java)
            .apply { sendAuthTokenRefreshWideEvent().setRawStoredState(Toggle.State(true)) }

    private var verifiedInstall = true
    private val isVerifiedPlayStoreInstall = object : IsVerifiedPlayStoreInstall {
        override fun invoke(): Boolean = verifiedInstall
    }
    private val timeProvider: CurrentTimeProvider = mock()

    private lateinit var authWideEvent: AuthTokenRefreshWideEventImpl

    @Before
    fun setup() = runBlocking {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(networkProtectionState.isRunning()).thenReturn(false)
        whenever(timeProvider.currentTimeMillis()).thenReturn(NOW.toEpochMilli())

        authWideEvent = AuthTokenRefreshWideEventImpl(
            wideEventClient = wideEventClient,
            subscriptionsFeature = lazy { subscriptionsFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            networkProtectionState = lazy { networkProtectionState },
            isVerifiedPlayStoreInstall = lazy { isVerifiedPlayStoreInstall },
            timeProvider = timeProvider,
            processName = "main",
        )
    }

    private suspend fun startFlow(
        subscriptionStatus: SubscriptionStatus = SubscriptionStatus.UNKNOWN,
        subscriptionPlatform: String? = "google",
        refreshHistory: TokenRefreshHistory = TokenRefreshHistory(failureStreak = 0, lastSuccessAt = null),
    ) = authWideEvent.onStart(subscriptionStatus, subscriptionPlatform, refreshHistory)

    @Test
    fun `onStart starts a new flow and total interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(123L))

        startFlow(
            subscriptionStatus = SubscriptionStatus.UNKNOWN,
            subscriptionPlatform = "google",
            refreshHistory = TokenRefreshHistory(failureStreak = 0, lastSuccessAt = null),
        )

        verify(wideEventClient).flowStart(
            name = "auth-token-refresh",
            flowEntryPoint = null,
            metadata = mapOf(
                "subscription_status" to SubscriptionStatus.UNKNOWN.statusName,
                "subscription_platform" to "google",
                "netp_is_enabled" to "false",
                "netp_is_running" to "false",
                "process_name" to "main",
                "verified_install" to "true",
                "consecutive_failures_bucket" to "0",
                "time_since_last_success_h_bucketed" to "-1",
                "last_refresh_process" to "unknown",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
            definition = WideEventDefinition(version = Version(minor = 1, patch = 3)),
        )

        verify(wideEventClient).intervalStart(123L, "total_duration_ms_bucketed", null)
        verify(wideEventClient).intervalStart(
            wideEventId = 123L,
            key = "token_refresh_lock_wait_ms_bucketed",
            timeout = null,
            buckets = setOf(100.milliseconds, 500.milliseconds, 1.seconds, 3.seconds, 10.seconds, 30.seconds, 60.seconds),
        )
    }

    @Test
    fun `onStart ends previous flow if one is active`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        startFlow(subscriptionStatus = SubscriptionStatus.WAITING)

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(2L))

        startFlow(subscriptionStatus = SubscriptionStatus.AUTO_RENEWABLE)

        verify(wideEventClient).intervalEnd(1L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(1L, FlowStatus.Unknown, emptyMap())
    }

    @Test
    fun `onCrossProcessLockAcquired ends lock wait interval and sends successful step with outcome`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(21L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onCrossProcessLockAcquired(Result.success(Closeable {}))

        verify(wideEventClient).intervalEnd(21L, "token_refresh_lock_wait_ms_bucketed")
        verify(wideEventClient).flowStep(21L, "lock_acquire", true, mapOf("lock_outcome" to "acquired"))
    }

    @Test
    fun `onCrossProcessLockAcquired with fallback timeout outcome sends failed step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(23L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onCrossProcessLockAcquired(Result.failure(timeoutCancellationException()))

        verify(wideEventClient).flowStep(23L, "lock_acquire", false, mapOf("lock_outcome" to "timeout"))
    }

    @Test
    fun `onCrossProcessLockAcquired with fallback error outcome sends failed step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(24L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onCrossProcessLockAcquired(Result.failure(RuntimeException()))

        verify(wideEventClient).flowStep(24L, "lock_acquire", false, mapOf("lock_outcome" to "error"))
    }

    @Test
    fun `onTokenRead sends step and starts jwks interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(10L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokenRead()

        verify(wideEventClient).flowStep(10L, "token_read", true, emptyMap())
        verify(wideEventClient).intervalStart(10L, "fetch_jwks_latency_ms_bucketed", null)
    }

    @Test
    fun `onJwksFetched ends jwks interval and starts tokens interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(11L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onJwksFetched()

        verify(wideEventClient).intervalEnd(11L, "fetch_jwks_latency_ms_bucketed")
        verify(wideEventClient).flowStep(11L, "jwks_fetch", true, emptyMap())
        verify(wideEventClient).intervalStart(11L, "refresh_access_token_latency_ms_bucketed", null)
    }

    @Test
    fun `onTokensFetched ends tokens interval and sends step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(12L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokensFetched()

        verify(wideEventClient).intervalEnd(12L, "refresh_access_token_latency_ms_bucketed")
        verify(wideEventClient).flowStep(12L, "token_request", true, emptyMap())
    }

    @Test
    fun `onTokensValidated sends validation step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(13L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokensValidated()

        verify(wideEventClient).flowStep(13L, "token_validation", true, emptyMap())
    }

    @Test
    fun `onAccessTokenValidationFailed sends failed validation step for access token`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(25L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onAccessTokenValidationFailed(InvalidSignature)

        verify(wideEventClient).flowStep(
            wideEventId = 25L,
            stepName = "token_validation",
            success = false,
            metadata = mapOf(
                "token_validation_failed_token" to "access",
                "token_validation_failure_reason" to "invalid_signature",
            ),
        )
    }

    @Test
    fun `onRefreshTokenValidationFailed sends failed validation step for refresh token`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(26L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onRefreshTokenValidationFailed(KeyNotFound)

        verify(wideEventClient).flowStep(
            wideEventId = 26L,
            stepName = "token_validation",
            success = false,
            metadata = mapOf(
                "token_validation_failed_token" to "refresh",
                "token_validation_failure_reason" to "key_not_found",
            ),
        )
    }

    @Test
    fun `token validation failure reasons are mapped to param values`() = runTest {
        val expectedValues = mapOf(
            Expired(clockSkew = null) to "expired",
            NotYetValid(clockSkew = null) to "not_yet_valid",
            InvalidSignature to "invalid_signature",
            KeyNotFound to "key_not_found",
            JwksParseError to "jwks_parse_error",
            Malformed to "malformed",
            Unsupported to "unsupported",
            MissingClaim to "missing_claim",
            InvalidIssuer to "invalid_issuer",
            InvalidAudience to "invalid_audience",
            InvalidScope to "invalid_scope",
            InvalidClaim to "invalid_claim",
            ClaimsParseError to "claims_parse_error",
            Other to "other",
        )

        expectedValues.forEach { (reason, expectedValue) ->
            assertEquals(expectedValue, accessTokenValidationFailureMetadata(reason)["token_validation_failure_reason"])
            assertEquals(expectedValue, refreshTokenValidationFailureMetadata(reason)["token_validation_failure_reason"])
        }
    }

    @Test
    fun `Expired with clock skew sends bucketed clock skew`() = runTest {
        val metadata = accessTokenValidationFailureMetadata(Expired(clockSkew = 2.hours + 10.minutes))

        assertEquals(
            mapOf(
                "token_validation_failed_token" to "access",
                "token_validation_failure_reason" to "expired",
                "token_validation_clock_skew_s_bucketed" to "7200",
            ),
            metadata,
        )
    }

    @Test
    fun `NotYetValid with clock skew sends bucketed clock skew`() = runTest {
        val metadata = refreshTokenValidationFailureMetadata(NotYetValid(clockSkew = (-90).seconds))

        assertEquals(
            mapOf(
                "token_validation_failed_token" to "refresh",
                "token_validation_failure_reason" to "not_yet_valid",
                "token_validation_clock_skew_s_bucketed" to "-60",
            ),
            metadata,
        )
    }

    @Test
    fun `Expired and NotYetValid without clock skew do not send clock skew`() = runTest {
        assertFalse("token_validation_clock_skew_s_bucketed" in accessTokenValidationFailureMetadata(Expired(clockSkew = null)))
        assertFalse("token_validation_clock_skew_s_bucketed" in accessTokenValidationFailureMetadata(NotYetValid(clockSkew = null)))
    }

    @Test
    fun `clock skew is rounded toward zero to bucket boundaries and clamped`() = runTest {
        val expectedBuckets = listOf(
            Duration.ZERO to "0",
            999.milliseconds to "0",
            (-999).milliseconds to "0",
            1.seconds to "1",
            (-1).seconds to "-1",
            1999.milliseconds to "1",
            2.seconds to "2",
            (-2).seconds to "-2",
            4.seconds to "2",
            (-4).seconds to "-2",
            5.seconds to "5",
            (-5).seconds to "-5",
            14.seconds to "5",
            15.seconds to "15",
            29.seconds to "15",
            30.seconds to "30",
            60.seconds to "60",
            119.seconds to "60",
            120.seconds to "120",
            300.seconds to "300",
            600.seconds to "600",
            1800.seconds to "1800",
            3600.seconds to "3600",
            7200.seconds to "7200",
            10800.seconds to "7200",
            14399.seconds to "7200",
            14400.seconds to "14400",
            28799.seconds to "14400",
            28800.seconds to "28800",
            (-28800).seconds to "-28800",
            43200.seconds to "28800",
            86399.seconds to "28800",
            86400.seconds to "86400",
            (-86400).seconds to "-86400",
            30.days to "86400",
            (-30).days to "-86400",
        )

        expectedBuckets.forEach { (clockSkew, expectedBucket) ->
            assertEquals(
                "clock skew $clockSkew",
                expectedBucket,
                accessTokenValidationFailureMetadata(Expired(clockSkew = clockSkew))["token_validation_clock_skew_s_bucketed"],
            )
        }
    }

    private suspend fun accessTokenValidationFailureMetadata(reason: TokenValidationFailureReason): Map<String, String> =
        captureTokenValidationFailureMetadata { authWideEvent.onAccessTokenValidationFailed(reason) }

    private suspend fun refreshTokenValidationFailureMetadata(reason: TokenValidationFailureReason): Map<String, String> =
        captureTokenValidationFailureMetadata { authWideEvent.onRefreshTokenValidationFailed(reason) }

    private suspend fun captureTokenValidationFailureMetadata(block: suspend () -> Unit): Map<String, String> {
        reset(wideEventClient)
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(27L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        block()

        val metadataCaptor = argumentCaptor<Map<String, String>>()
        verify(wideEventClient).flowStep(eq(27L), eq("token_validation"), eq(false), metadataCaptor.capture())
        return metadataCaptor.firstValue
    }

    @Test
    fun `onBackendErrorResponse sends token_request failure with metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(14L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onBackendErrorResponse(backendErrorResponse = "backend-err", backendErrorCode = 3)

        verify(wideEventClient).flowStep(
            wideEventId = 14L,
            stepName = "token_request",
            success = false,
            metadata = mapOf("backend_error_response" to "backend-err", "backend_error_code" to "3"),
        )
    }

    @Test
    fun `onBackendErrorResponse without error code sends only error`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(14L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onBackendErrorResponse(backendErrorResponse = "backend-err", backendErrorCode = null)

        verify(wideEventClient).flowStep(
            wideEventId = 14L,
            stepName = "token_request",
            success = false,
            metadata = mapOf("backend_error_response" to "backend-err"),
        )
    }

    @Test
    fun `onBackendErrorResponse without error sends only error code`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(14L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onBackendErrorResponse(backendErrorResponse = null, backendErrorCode = 0)

        verify(wideEventClient).flowStep(
            wideEventId = 14L,
            stepName = "token_request",
            success = false,
            metadata = mapOf("backend_error_code" to "0"),
        )
    }

    @Test
    fun `onBackendErrorResponse sends error code outside known range as is`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(14L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onBackendErrorResponse(backendErrorResponse = "backend-err", backendErrorCode = 7)

        verify(wideEventClient).flowStep(
            wideEventId = 14L,
            stepName = "token_request",
            success = false,
            metadata = mapOf("backend_error_response" to "backend-err", "backend_error_code" to "7"),
        )
    }

    @Test
    fun `onPlayLoginSuccess sends step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(15L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onPlayLoginSuccess()

        verify(wideEventClient).flowStep(
            wideEventId = 15L,
            stepName = "play_login",
            success = true,
            metadata = mapOf("recovery_strategy" to "play_login", "recovery_outcome" to "recovered"),
        )
    }

    @Test
    fun `onPlayLoginFailure sends failure step, finishes flow and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(16L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        val ex = IllegalStateException("boom")
        authWideEvent.onPlayLoginFailure(signedOut = true, refreshException = ex, loginError = "sign-in-required")

        verify(wideEventClient).flowStep(
            wideEventId = 16L,
            stepName = "play_login",
            success = false,
            metadata = mapOf(
                "play_login_error" to "sign-in-required",
                "recovery_strategy" to "play_login",
                "recovery_outcome" to "signed_out",
            ),
        )
        verify(wideEventClient).intervalEnd(16L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(16L, FlowStatus.Failure("IllegalStateException"), mapOf("signed_out" to "true"))

        reset(wideEventClient)

        // id cleared -> subsequent calls produce no client interactions
        authWideEvent.onSuccess()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onPlayLoginFailure without signing the user out logs signedOut as false`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(20L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onPlayLoginFailure(signedOut = false, refreshException = IllegalStateException("boom"), loginError = "UnknownError")

        verify(wideEventClient).flowStep(
            wideEventId = 20L,
            stepName = "play_login",
            success = false,
            metadata = mapOf(
                "play_login_error" to "UnknownError",
                "recovery_strategy" to "play_login",
                "recovery_outcome" to "deferred",
            ),
        )
        verify(wideEventClient).flowFinish(20L, FlowStatus.Failure("IllegalStateException"), mapOf("signed_out" to "false"))
    }

    @Test
    fun `onUnknownAccountError cancels flow and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(17L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onUnknownAccountError()

        verify(wideEventClient).intervalEnd(17L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(17L, FlowStatus.Cancelled, emptyMap())

        reset(wideEventClient)
        authWideEvent.onTokensValidated()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onSkipped aborts flow and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(21L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onSkipped()

        verify(wideEventClient).flowAbort(21L)
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
        verify(wideEventClient, never()).intervalEnd(eq(21L), eq("total_duration_ms_bucketed"))

        reset(wideEventClient)
        authWideEvent.onSuccess()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onSuccess finishes with Success and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(18L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onSuccess()

        verify(wideEventClient).intervalEnd(18L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(18L, FlowStatus.Success, emptyMap())

        reset(wideEventClient)
        authWideEvent.onFailure(IllegalArgumentException("x"))
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onFailure finishes with Failure and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(19L))
        startFlow(subscriptionStatus = SubscriptionStatus.UNKNOWN)

        authWideEvent.onFailure(IllegalArgumentException("nope"))

        verify(wideEventClient).intervalEnd(19L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(19L, FlowStatus.Failure("IllegalArgumentException"), emptyMap())
    }

    // TimeoutCancellationException has an internal constructor, so a real instance is obtained via withTimeout
    private suspend fun timeoutCancellationException(): TimeoutCancellationException = try {
        withTimeout(1) { awaitCancellation() }
    } catch (e: TimeoutCancellationException) {
        e
    }

    @Test
    fun `onStart maps the subscription platform to a bounded value`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        listOf("google" to "google", "stripe" to "stripe", "apple" to "apple", "ios" to "apple", "web" to "unknown", null to "unknown")
            .forEach { (platform, expected) ->
                startFlow(subscriptionPlatform = platform)
                verify(wideEventClient).flowStart(
                    any(),
                    anyOrNull(),
                    argThat { this["subscription_platform"] == expected },
                    any(),
                    any(),
                    any(),
                )
                clearInvocations(wideEventClient)
            }
    }

    @Test
    fun `onStart maps the last refreshing process to a bounded value`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        listOf("main" to "main", ":vpn" to ":vpn", ":pir" to ":pir", "ERROR" to "unknown", null to "unknown")
            .forEach { (process, expected) ->
                startFlow(refreshHistory = TokenRefreshHistory(failureStreak = 0, lastSuccessAt = null, lastSuccessProcess = process))
                verify(wideEventClient).flowStart(any(), anyOrNull(), argThat { this["last_refresh_process"] == expected }, any(), any(), any())
                clearInvocations(wideEventClient)
            }
    }

    @Test
    fun `onStart reports an unverified install`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))
        verifiedInstall = false

        startFlow()

        verify(wideEventClient).flowStart(any(), anyOrNull(), argThat { this["verified_install"] == "false" }, any(), any(), any())
    }

    @Test
    fun `onStart buckets the failure streak downwards`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        listOf(0 to "0", 1 to "1", 2 to "2", 4 to "3", 7 to "5", 19 to "10", 49 to "20", 50 to "50", 500 to "50")
            .forEach { (streak, expected) ->
                startFlow(refreshHistory = TokenRefreshHistory(failureStreak = streak, lastSuccessAt = null))
                verify(wideEventClient).flowStart(
                    any(),
                    anyOrNull(),
                    argThat { this["consecutive_failures_bucket"] == expected },
                    any(),
                    any(),
                    any(),
                )
                clearInvocations(wideEventClient)
            }
    }

    @Test
    fun `onStart buckets hours since the last success downwards and clamps at 30 days`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(1L))

        listOf(
            30.minutes to "0",
            1.hours to "1",
            3.hours to "1",
            4.hours to "4",
            23.hours to "4",
            2.days to "24",
            5.days to "72",
            10.days to "168",
            30.days to "720",
            400.days to "720",
        ).forEach { (age, expected) ->
            startFlow(refreshHistory = TokenRefreshHistory(failureStreak = 0, lastSuccessAt = NOW.minusMillis(age.inWholeMilliseconds)))
            verify(wideEventClient).flowStart(
                any(),
                anyOrNull(),
                argThat { this["time_since_last_success_h_bucketed"] == expected },
                any(),
                any(),
                any(),
            )
            clearInvocations(wideEventClient)
        }
    }

    @Test
    fun `onTokenReadFailed sends failed token_read step with the store error`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(30L))
        startFlow()

        authWideEvent.onTokenReadFailed(TokenStoreError.MISSING)

        verify(wideEventClient).flowStep(30L, "token_read", false, mapOf("store_error" to "missing"))
        verify(wideEventClient, never()).intervalStart(eq(30L), eq("fetch_jwks_latency_ms_bucketed"), anyOrNull(), anyOrNull())
    }

    @Test
    fun `onTokensSaved sends token_save step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(31L))
        startFlow()

        authWideEvent.onTokensSaved()

        verify(wideEventClient).flowStep(31L, "token_save", true, emptyMap())
    }

    @Test
    fun `onTokenSaveFailed sends failed token_save step with the store error`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any()))
            .thenReturn(Result.success(32L))
        startFlow()

        authWideEvent.onTokenSaveFailed(TokenStoreError.WRITE_FAILED)

        verify(wideEventClient).flowStep(32L, "token_save", false, mapOf("store_error" to "write_failed"))
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() = runTest {
        subscriptionsFeature.sendAuthTokenRefreshWideEvent().setRawStoredState(Toggle.State(false))

        startFlow()
        authWideEvent.onTokensFetched()

        verifyNoInteractions(wideEventClient)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-28T12:00:00Z")
    }
}
