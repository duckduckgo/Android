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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SubscriptionRestoreWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature =
        FakeFeatureToggleFactory
            .create(PrivacyProFeature::class.java)
            .apply { sendSubscriptionRestoreWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var subscriptionRestoreWideEvent: SubscriptionRestoreWideEventImpl

    @Before
    fun setup() {
        subscriptionRestoreWideEvent =
            SubscriptionRestoreWideEventImpl(
                wideEventClient = wideEventClient,
                privacyProFeature = { privacyProFeature },
                dispatchers = coroutineRule.testDispatcherProvider,
                coroutineScope = coroutineRule.testScope,
            )
    }

    @Test
    fun `onEmailRestoreFlowStarted starts a new flow with email platform`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(123L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)

        verify(wideEventClient).flowStart(
            name = "subscription-restore",
            flowEntryPoint = "funnel_appsettings_android",
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            metadata = mapOf(
                "restore_platform" to "email_address",
                "is_purchase_attempt" to "false",
            ),
        )
        verify(wideEventClient).intervalStart(wideEventId = 123L, key = "restore_latency_ms_bucketed")
    }

    @Test
    fun `onGooglePlayRestoreFlowStarted starts a new flow with google account platform`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(456L))

        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStarted(isOriginWeb = false)

        verify(wideEventClient).flowStart(
            name = "subscription-restore",
            flowEntryPoint = "funnel_appsettings_android",
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            metadata = mapOf(
                "restore_platform" to "google_play",
                "is_purchase_attempt" to "false",
            ),
        )
        verify(wideEventClient).intervalStart(wideEventId = 456L, key = "restore_latency_ms_bucketed")
    }

    @Test
    fun `onGooglePlayRestoreFlowStartedOnPurchaseAttempt starts a new flow with purchase attempt flag`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(789L))

        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStartedOnPurchaseAttempt()

        verify(wideEventClient).flowStart(
            name = "subscription-restore",
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            metadata = mapOf(
                "restore_platform" to "google_play",
                "is_purchase_attempt" to "true",
            ),
        )
        verify(wideEventClient).intervalStart(wideEventId = 789L, key = "restore_latency_ms_bucketed")
    }

    @Test
    fun `onEmailRestoreSuccess ends interval and finishes flow with success`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onEmailRestoreSuccess()

        verify(wideEventClient).intervalEnd(wideEventId = 100L, key = "restore_latency_ms_bucketed")
        verify(wideEventClient).flowFinish(wideEventId = 100L, status = FlowStatus.Success)
    }

    @Test
    fun `onGooglePlayRestoreSuccess ends interval and finishes flow with success`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(200L))

        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onGooglePlayRestoreSuccess()

        verify(wideEventClient).intervalEnd(wideEventId = 200L, key = "restore_latency_ms_bucketed")
        verify(wideEventClient).flowFinish(wideEventId = 200L, status = FlowStatus.Success)
    }

    @Test
    fun `onGooglePlayRestoreFailure finishes flow with failure`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(300L))

        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onGooglePlayRestoreFailure("some-error")

        verify(wideEventClient).flowFinish(
            wideEventId = 300L,
            status = FlowStatus.Failure(reason = "some-error"),
        )
    }

    @Test
    fun `starting a new flow when one is active finishes the previous flow with Unknown status`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(1L))
            .thenReturn(Result.success(2L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStarted(isOriginWeb = false)

        verify(wideEventClient).flowFinish(wideEventId = 1L, status = FlowStatus.Unknown)
    }

    @Test
    fun `success without starting flow fetches flow id from client`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(700L)))

        subscriptionRestoreWideEvent.onEmailRestoreSuccess()

        verify(wideEventClient).getFlowIds("subscription-restore")
        verify(wideEventClient).intervalEnd(wideEventId = 700L, key = "restore_latency_ms_bucketed")
        verify(wideEventClient).flowFinish(wideEventId = 700L, status = FlowStatus.Success)
    }

    @Test
    fun `success without starting flow and no flow ids does nothing`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        subscriptionRestoreWideEvent.onEmailRestoreSuccess()

        verify(wideEventClient).getFlowIds("subscription-restore")
        verifyNoMoreInteractions(wideEventClient)
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() = runTest {
        privacyProFeature.sendSubscriptionRestoreWideEvent().setRawStoredState(Toggle.State(false))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onGooglePlayRestoreFlowStartedOnPurchaseAttempt()
        subscriptionRestoreWideEvent.onEmailRestoreSuccess()
        subscriptionRestoreWideEvent.onGooglePlayRestoreSuccess()
        subscriptionRestoreWideEvent.onGooglePlayRestoreFailure("error")

        verifyNoInteractions(wideEventClient)
    }

    @Test
    fun `flowStart failure does not call intervalStart`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("error")))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)

        verify(wideEventClient).flowStart(any(), anyOrNull(), any(), any())
        verify(wideEventClient, never()).intervalStart(any(), any(), any())
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged records activation flow started step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged("https://duckduckgo.com/subscriptions/activation-flow")
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 100L, stepName = "activation_flow_started")
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged records email input step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged(
            "https://duckduckgo.com/subscriptions/activation-flow/this-device/activate-by-email",
        )
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 100L, stepName = "email_address_input")
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged records one time password input step`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged(
            "https://duckduckgo.com/subscriptions/activation-flow/this-device/activate-by-email/otp",
        )
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 100L, stepName = "one_time_password_input")
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged ignores query parameters when matching path`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged("https://duckduckgo.com/subscriptions/activation-flow?token=abc&ref=xyz")
        advanceUntilIdle()

        verify(wideEventClient).flowStep(wideEventId = 100L, stepName = "activation_flow_started")
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged does not record step for unmatched URL`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(100L))

        subscriptionRestoreWideEvent.onEmailRestoreFlowStarted(isOriginWeb = false)
        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged("https://duckduckgo.com/some/other/path")
        advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `onSubscriptionWebViewUrlChanged does nothing without active flow`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        subscriptionRestoreWideEvent.onSubscriptionWebViewUrlChanged("https://duckduckgo.com/subscriptions/activation-flow")
        advanceUntilIdle()

        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }
}
