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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class SubscriptionSwitchWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = org.mockito.kotlin.mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature =
        FakeFeatureToggleFactory
            .create(PrivacyProFeature::class.java)
            .apply { sendSubscriptionSwitchWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var subscriptionSwitchWideEvent: SubscriptionSwitchWideEventImpl

    @Before
    fun setup() {
        subscriptionSwitchWideEvent = SubscriptionSwitchWideEventImpl(
            wideEventClient = wideEventClient,
            privacyProFeature = { privacyProFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `onSwitchFlowStarted starts flow with correct metadata`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any())).thenReturn(Result.success(123L))

        subscriptionSwitchWideEvent.onSwitchFlowStarted(
            context = "subscription_settings",
            fromPlan = "ddg-privacy-pro-monthly-renews-us",
            toPlan = "ddg-privacy-pro-yearly-renews-us",
        )

        // fromPlan is monthly, so switchType should be computed as "upgrade"
        verify(wideEventClient).flowStart(
            name = "subscription-switch",
            flowEntryPoint = "subscription_settings",
            metadata = mapOf(
                "from_plan" to "ddg-privacy-pro-monthly-renews-us",
                "to_plan" to "ddg-privacy-pro-yearly-renews-us",
                "switch_type" to "upgrade",
            ),
            cleanupPolicy = CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true),
        )
    }

    @Test
    fun `onCurrentSubscriptionValidated sends successful step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onCurrentSubscriptionValidated()

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "validate_current_subscription",
            success = true,
        )
    }

    @Test
    fun `onValidationFailure sends failure step and finishes flow with error`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onValidationFailure("User not signed in")

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "validate_current_subscription",
            success = false,
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure("User not signed in"),
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onTargetPlanRetrieved sends successful step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onTargetPlanRetrieved()

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "retrieve_target_plan",
            success = true,
        )
    }

    @Test
    fun `onTargetPlanRetrievalFailure sends failure step and finishes flow`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onTargetPlanRetrievalFailure()

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "retrieve_target_plan",
            success = false,
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure("Target plan not found"),
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onBillingFlowInitSuccess sends successful step`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onBillingFlowInitSuccess()

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "billing_flow_init",
            success = true,
        )
    }

    @Test
    fun `onBillingFlowInitFailure sends failure step and finishes flow`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onBillingFlowInitFailure("Missing product details")

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "billing_flow_init",
            success = false,
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure("Missing product details"),
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onUserCancelled finishes flow with cancelled status`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onUserCancelled()

        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Cancelled,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onPlayBillingSwitchSuccess starts interval and sends flowStep`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(444L)))

        subscriptionSwitchWideEvent.onPlayBillingSwitchSuccess()

        verify(wideEventClient).intervalStart(
            wideEventId = eq(444L),
            key = eq("activation_latency_ms_bucketed"),
            timeout = any(),
        )
        verify(wideEventClient).flowStep(
            wideEventId = eq(444L),
            stepName = eq("billing_flow_switch"),
            success = eq(true),
            metadata = eq(emptyMap()),
        )
    }

    @Test
    fun `onSwitchConfirmationSuccess ends interval, sends step, and finishes flow with success`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onSwitchConfirmationSuccess()

        verify(wideEventClient).intervalEnd(
            wideEventId = 123L,
            key = "activation_latency_ms_bucketed",
        )
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "confirm_switch",
            success = true,
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onSubscriptionUpdated from WAITING to ACTIVE finishes flow with success`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onSubscriptionUpdated(
            oldStatus = SubscriptionStatus.WAITING,
            newStatus = SubscriptionStatus.AUTO_RENEWABLE,
        )

        verify(wideEventClient).intervalEnd(
            wideEventId = 123L,
            key = "activation_latency_ms_bucketed",
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onSubscriptionUpdated does not finish flow if not transitioning from WAITING to ACTIVE`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        // Test various status transitions that should NOT finish the flow
        subscriptionSwitchWideEvent.onSubscriptionUpdated(
            oldStatus = SubscriptionStatus.AUTO_RENEWABLE,
            newStatus = SubscriptionStatus.GRACE_PERIOD,
        )

        subscriptionSwitchWideEvent.onSubscriptionUpdated(
            oldStatus = SubscriptionStatus.WAITING,
            newStatus = SubscriptionStatus.INACTIVE,
        )

        subscriptionSwitchWideEvent.onSubscriptionUpdated(
            oldStatus = SubscriptionStatus.UNKNOWN,
            newStatus = SubscriptionStatus.AUTO_RENEWABLE,
        )

        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onUIRefreshed sends step without success parameter`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onUIRefreshed()

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "ui_refresh",
        )
    }

    @Test
    fun `onSwitchFailed finishes flow with failure status and error`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        subscriptionSwitchWideEvent.onSwitchFailed("Unexpected error")

        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure("Unexpected error"),
            metadata = emptyMap(),
        )
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when feature disabled then no events are sent`() = runTest {
        privacyProFeature.sendSubscriptionSwitchWideEvent().setRawStoredState(Toggle.State(false))

        // Mock getFlowIds to return empty list when methods try to retrieve flow ID
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        subscriptionSwitchWideEvent.onSwitchFlowStarted(
            context = "subscription_settings",
            fromPlan = "ddg-privacy-pro-monthly-renews-us",
            toPlan = "ddg-privacy-pro-yearly-renews-us",
        )
        subscriptionSwitchWideEvent.onCurrentSubscriptionValidated()
        subscriptionSwitchWideEvent.onTargetPlanRetrieved()
        subscriptionSwitchWideEvent.onBillingFlowInitSuccess()
        subscriptionSwitchWideEvent.onPlayBillingSwitchSuccess()
        subscriptionSwitchWideEvent.onSwitchConfirmationSuccess()

        verify(wideEventClient, never()).flowStart(any(), any(), any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onSwitchConfirmationSuccess clears cachedFlowId after finishing`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(100L)))

        subscriptionSwitchWideEvent.onSwitchConfirmationSuccess()

        verify(wideEventClient).flowFinish(
            wideEventId = 100L,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )

        // Reset and verify that cachedFlowId was cleared
        org.mockito.kotlin.reset(wideEventClient)
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))

        subscriptionSwitchWideEvent.onSwitchConfirmationSuccess()
        verify(wideEventClient).getFlowIds("subscription-switch")
        verifyNoMoreInteractions(wideEventClient)
    }
}
