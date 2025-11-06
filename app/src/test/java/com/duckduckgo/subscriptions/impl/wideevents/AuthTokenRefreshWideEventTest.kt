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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

class AuthTokenRefreshWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val networkProtectionState: NetworkProtectionState = mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature =
        FakeFeatureToggleFactory
            .create(PrivacyProFeature::class.java)
            .apply { sendAuthTokenRefreshWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var authWideEvent: AuthTokenRefreshWideEventImpl

    @Before
    fun setup() = runBlocking {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        whenever(networkProtectionState.isRunning()).thenReturn(false)

        authWideEvent = AuthTokenRefreshWideEventImpl(
            wideEventClient = wideEventClient,
            privacyProFeature = { privacyProFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            networkProtectionState = { networkProtectionState },
            processName = "main",
        )
    }

    @Test
    fun `onStart starts a new flow and total interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        verify(wideEventClient).flowStart(
            name = "auth-token-refresh",
            flowEntryPoint = null,
            metadata = mapOf(
                "subscription_status" to SubscriptionStatus.UNKNOWN.statusName,
                "netp_is_enabled" to "false",
                "netp_is_running" to "false",
                "process_name" to "main",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = false),
        )

        verify(wideEventClient).intervalStart(123L, "total_duration_ms_bucketed", null)
    }

    @Test
    fun `onStart ends previous flow if one is active`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1L))

        authWideEvent.onStart(SubscriptionStatus.WAITING)

        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(2L))

        authWideEvent.onStart(SubscriptionStatus.AUTO_RENEWABLE)

        verify(wideEventClient).intervalEnd(1L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(1L, FlowStatus.Unknown, emptyMap())
    }

    @Test
    fun `onTokenRead sends step and starts jwks interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(10L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokenRead()

        verify(wideEventClient).flowStep(10L, "token_read", true, emptyMap())
        verify(wideEventClient).intervalStart(10L, "fetch_jwks_latency_ms_bucketed", null)
    }

    @Test
    fun `onJwksFetched ends jwks interval and starts tokens interval`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(11L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onJwksFetched()

        verify(wideEventClient).intervalEnd(11L, "fetch_jwks_latency_ms_bucketed")
        verify(wideEventClient).flowStep(11L, "jwks_fetch", true, emptyMap())
        verify(wideEventClient).intervalStart(11L, "refresh_access_token_latency_ms_bucketed", null)
    }

    @Test
    fun `onTokensFetched ends tokens interval and sends step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(12L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokensFetched()

        verify(wideEventClient).intervalEnd(12L, "refresh_access_token_latency_ms_bucketed")
        verify(wideEventClient).flowStep(12L, "token_request", true, emptyMap())
    }

    @Test
    fun `onTokensValidated sends validation step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(13L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onTokensValidated()

        verify(wideEventClient).flowStep(13L, "token_validation", true, emptyMap())
    }

    @Test
    fun `onBackendErrorResponse sends token_request failure with metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(14L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onBackendErrorResponse("backend-err")

        verify(wideEventClient).flowStep(
            wideEventId = 14L,
            stepName = "token_request",
            success = false,
            metadata = mapOf("backend_error_response" to "backend-err"),
        )
    }

    @Test
    fun `onPlayLoginSuccess sends step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(15L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onPlayLoginSuccess()

        verify(wideEventClient).flowStep(15L, "play_login", true, emptyMap())
    }

    @Test
    fun `onPlayLoginFailure sends failure step, finishes flow and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(16L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        val ex = IllegalStateException("boom")
        authWideEvent.onPlayLoginFailure(signedOut = true, refreshException = ex, loginError = "sign-in-required")

        verify(wideEventClient).flowStep(
            wideEventId = 16L,
            stepName = "play_login",
            success = false,
            metadata = mapOf("play_login_error" to "sign-in-required"),
        )
        verify(wideEventClient).intervalEnd(16L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(16L, FlowStatus.Failure("IllegalStateException: boom"), emptyMap())

        reset(wideEventClient)

        // id cleared -> subsequent calls produce no client interactions
        authWideEvent.onSuccess()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onUnknownAccountError cancels flow and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(17L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onUnknownAccountError()

        verify(wideEventClient).intervalEnd(17L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(17L, FlowStatus.Cancelled, emptyMap())

        reset(wideEventClient)
        authWideEvent.onTokensValidated()
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onSuccess finishes with Success and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(18L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onSuccess()

        verify(wideEventClient).intervalEnd(18L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(18L, FlowStatus.Success, emptyMap())

        reset(wideEventClient)
        authWideEvent.onFailure(IllegalArgumentException("x"))
        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `onFailure finishes with Failure and clears id`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(19L))
        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)

        authWideEvent.onFailure(IllegalArgumentException("nope"))

        verify(wideEventClient).intervalEnd(19L, "total_duration_ms_bucketed")
        verify(wideEventClient).flowFinish(19L, FlowStatus.Failure("IllegalArgumentException: nope"), emptyMap())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() = runTest {
        privacyProFeature.sendAuthTokenRefreshWideEvent().setRawStoredState(Toggle.State(false))

        authWideEvent.onStart(SubscriptionStatus.UNKNOWN)
        authWideEvent.onTokensFetched()

        verifyNoInteractions(wideEventClient)
    }
}
