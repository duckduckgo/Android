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
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.time.Duration

class SubscriptionPurchaseWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature =
        FakeFeatureToggleFactory
            .create(PrivacyProFeature::class.java)
            .apply { sendSubscriptionPurchaseWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var subscriptionPurchaseWideEvent: SubscriptionPurchaseWideEventImpl

    @Before
    fun setup() {
        subscriptionPurchaseWideEvent =
            SubscriptionPurchaseWideEventImpl(
                wideEventClient = wideEventClient,
                privacyProFeature = { privacyProFeature },
                dispatchers = coroutineRule.testDispatcherProvider,
            )
    }

    @Test
    fun `onPurchaseFlowStarted starts a new flow`() =
        runTest {
            whenever(wideEventClient.flowStart(any(), any(), any(), any()))
                .thenReturn(Result.success(123L))

            subscriptionPurchaseWideEvent.onPurchaseFlowStarted("sub_id", true, "app_settings")

            verify(wideEventClient).flowStart(
                name = "subscription-purchase",
                flowEntryPoint = "app_settings",
                metadata =
                    mapOf(
                        "subscription_identifier" to "sub_id",
                        "free_trial_eligible" to "true",
                    ),
                cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
            )
        }

    @Test
    fun `onSubscriptionRefreshSuccess sends flowStep`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(456L)))

            subscriptionPurchaseWideEvent.onSubscriptionRefreshSuccess()

            verify(wideEventClient).flowStep(
                wideEventId = 456L,
                stepName = "refresh_subscription",
                success = true,
            )
        }

    @Test
    fun `onSubscriptionRefreshFailure sends flowStep and flowFinish`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(456L)))
            val ex = IllegalStateException("boom")

            subscriptionPurchaseWideEvent.onSubscriptionRefreshFailure(ex)

            verify(wideEventClient).flowStep(456L, "refresh_subscription", false)
            verify(wideEventClient).flowFinish(
                wideEventId = 456L,
                status = FlowStatus.Failure("IllegalStateException: boom"),
                metadata = emptyMap(),
            )
        }

    @Test
    fun `onPurchaseConfirmationSuccess finishes flow and clears cachedFlowId`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(100L)))

            subscriptionPurchaseWideEvent.onPurchaseConfirmationSuccess()

            verify(wideEventClient).flowStep(100L, "confirm_purchase", true)
            verify(wideEventClient).intervalEnd(100L, "activation_latency_ms_bucketed")
            verify(wideEventClient).flowFinish(
                wideEventId = 100L,
                status = FlowStatus.Success,
                metadata = emptyMap(),
            )

            // getFlowIds client does not return ids of finished flows
            reset(wideEventClient)
            whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))

            subscriptionPurchaseWideEvent.onPurchaseConfirmationSuccess()
            verify(wideEventClient).getFlowIds("subscription-purchase")
            verifyNoMoreInteractions(wideEventClient)
        }

    @Test
    fun `onSubscriptionUpdated with active status finishes with Success`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(200L)))

            subscriptionPurchaseWideEvent.onSubscriptionUpdated(SubscriptionStatus.AUTO_RENEWABLE)

            verify(wideEventClient).flowFinish(
                wideEventId = 200L,
                status = FlowStatus.Success,
                metadata = emptyMap(),
            )
        }

    @SuppressLint("DenyListedApi")
    @Test
    fun `feature disabled results in no interactions`() =
        runTest {
            privacyProFeature.sendSubscriptionPurchaseWideEvent().setRawStoredState(Toggle.State(false))

            subscriptionPurchaseWideEvent.onPurchaseFlowStarted("id", true, null)
            subscriptionPurchaseWideEvent.onSubscriptionRefreshSuccess()

            verifyNoInteractions(wideEventClient)
        }

    @Test
    fun `onBillingFlowInitFailure sends flowStep and flowFinish`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(111L)))

            subscriptionPurchaseWideEvent.onBillingFlowInitFailure("init-error")

            verify(wideEventClient).flowStep(111L, "billing_flow_init", false)
            verify(wideEventClient).flowFinish(
                wideEventId = 111L,
                status = FlowStatus.Failure("init-error"),
                metadata = emptyMap(),
            )
        }

    @Test
    fun `onBillingFlowPurchaseFailure sends flowStep and flowFinish`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(222L)))

            subscriptionPurchaseWideEvent.onBillingFlowPurchaseFailure("purchase-error")

            verify(wideEventClient).flowStep(222L, "billing_flow_purchase", false)
            verify(wideEventClient).flowFinish(
                wideEventId = 222L,
                status = FlowStatus.Failure("purchase-error"),
                metadata = emptyMap(),
            )
        }

    @Test
    fun `onPurchaseCancelledByUser finishes flow with Cancelled`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(333L)))

            subscriptionPurchaseWideEvent.onPurchaseCancelledByUser()

            verify(wideEventClient).flowFinish(
                wideEventId = 333L,
                status = FlowStatus.Cancelled,
                metadata = emptyMap(),
            )
        }

    @Test
    fun `onExistingSubscriptionRestored aborts flow`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(444L)))

            subscriptionPurchaseWideEvent.onExistingSubscriptionRestored()

            verify(wideEventClient).flowAbort(444L)
        }

    @Test
    fun `onPurchaseFailed finishes flow with Failure`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(555L)))

            subscriptionPurchaseWideEvent.onPurchaseFailed("failure-reason")

            verify(wideEventClient).flowFinish(
                wideEventId = 555L,
                status = FlowStatus.Failure("failure-reason"),
                metadata = emptyMap(),
            )
        }

    @Test
    fun `onSubscriptionUpdated with null status finishes flow with Unknown`() =
        runTest {
            whenever(wideEventClient.getFlowIds(any()))
                .thenReturn(Result.success(listOf(666L)))

            subscriptionPurchaseWideEvent.onSubscriptionUpdated(null)

            verify(wideEventClient).flowFinish(
                wideEventId = 666L,
                status = FlowStatus.Unknown,
                metadata = emptyMap(),
            )
        }
}
