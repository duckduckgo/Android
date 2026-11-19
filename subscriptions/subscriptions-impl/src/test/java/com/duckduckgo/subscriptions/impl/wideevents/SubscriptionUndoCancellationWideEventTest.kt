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

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.wideevents.CleanupPolicy
import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.MONTHLY_PLAN_US
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@SuppressLint("DenyListedApi")
class SubscriptionUndoCancellationWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()

    private val subscriptionsFeature: SubscriptionsFeature =
        FakeFeatureToggleFactory
            .create(SubscriptionsFeature::class.java)
            .apply { sendSubscriptionUndoCancellationWideEvent().setRawStoredState(Toggle.State(true)) }

    private val timeProvider = object : CurrentTimeProvider {
        override fun elapsedRealtime(): Long = NOW
        override fun currentTimeMillis(): Long = NOW
        override fun localDateTimeNow() = throw UnsupportedOperationException()
    }

    private lateinit var wideEvent: SubscriptionUndoCancellationWideEventImpl

    @Before
    fun setup() {
        wideEvent = SubscriptionUndoCancellationWideEventImpl(
            wideEventClient = wideEventClient,
            subscriptionsFeature = lazy { subscriptionsFeature },
            dispatchers = coroutineRule.testDispatcherProvider,
            timeProvider = timeProvider,
        )
    }

    @Test
    fun `onFlowStarted starts flow with plan, free trial and days to expiry`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(123L))

        wideEvent.onFlowStarted(planId = MONTHLY_PLAN_US, isFreeTrial = true, expiresAt = NOW + 5.days.inWholeMilliseconds)

        verify(wideEventClient).flowStart(
            name = eq("subscription-undo-cancellation"),
            flowEntryPoint = eq(null),
            metadata = eq(
                mapOf(
                    "plan" to MONTHLY_PLAN_US,
                    "free_trial" to "true",
                    "days_to_expiry" to "d4_to_d7",
                ),
            ),
            cleanupPolicy = eq(CleanupPolicy.OnProcessStart(ignoreIfIntervalTimeoutPresent = true)),
            samplingProbability = any(),
            definition = any(),
        )
    }

    @Test
    fun `onFlowStarted buckets days to expiry`() = runTest {
        whenever(wideEventClient.flowStart(any(), any(), any(), any(), any(), any())).thenReturn(Result.success(123L))

        mapOf(
            NOW + 5.hours.inWholeMilliseconds to "d0",
            NOW + 1.days.inWholeMilliseconds to "d1_to_d3",
            NOW + 7.days.inWholeMilliseconds to "d4_to_d7",
            NOW + 8.days.inWholeMilliseconds to "d8_plus",
        ).forEach { (expiresAt, bucket) ->
            wideEvent.onFlowStarted(planId = MONTHLY_PLAN_US, isFreeTrial = false, expiresAt = expiresAt)

            verify(wideEventClient).flowStart(
                name = any(),
                flowEntryPoint = anyOrNullString(),
                metadata = eq(mapOf("plan" to MONTHLY_PLAN_US, "free_trial" to "false", "days_to_expiry" to bucket)),
                cleanupPolicy = any(),
                samplingProbability = any(),
                definition = any(),
            )
        }
    }

    @Test
    fun `onEligibilityChecked in app sends step with in_app method`() = runTest {
        givenFlowStarted()

        wideEvent.onEligibilityChecked(canUndoInApp = true)

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "check_eligibility",
            success = true,
            metadata = mapOf("method" to "in_app"),
        )
        verify(wideEventClient, never()).intervalStart(any(), any(), any(), any())
    }

    @Test
    fun `onEligibilityChecked not in app sends step with store method and starts interval`() = runTest {
        givenFlowStarted()

        wideEvent.onEligibilityChecked(canUndoInApp = false)

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "check_eligibility",
            success = false,
            metadata = mapOf("method" to "store"),
        )
        verify(wideEventClient).intervalStart(
            wideEventId = eq(123L),
            key = eq("activation_latency_ms_bucketed"),
            timeout = eq(10.minutes),
            buckets = any(),
        )
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onBillingFlowInitSuccess sends step`() = runTest {
        givenFlowStarted()

        wideEvent.onBillingFlowInitSuccess()

        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = "billing_flow_init", success = true)
    }

    @Test
    fun `onBillingFlowInitFailure sends failed step and finishes with failure`() = runTest {
        givenFlowStarted()

        wideEvent.onBillingFlowInitFailure("Billing error: BILLING_UNAVAILABLE")

        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = "billing_flow_init", success = false)
        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure(reason = "Billing error: BILLING_UNAVAILABLE"),
        )
    }

    @Test
    fun `onBillingFlowPurchaseSuccess starts interval and sends step`() = runTest {
        givenFlowStarted()

        wideEvent.onBillingFlowPurchaseSuccess()

        verify(wideEventClient).intervalStart(
            wideEventId = eq(123L),
            key = eq("activation_latency_ms_bucketed"),
            timeout = eq(10.minutes),
            buckets = any(),
        )
        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = "billing_flow_purchase", success = true)
    }

    @Test
    fun `onBillingFlowPurchaseFailure sends failed step and finishes with failure`() = runTest {
        givenFlowStarted()

        wideEvent.onBillingFlowPurchaseFailure("ITEM_UNAVAILABLE")

        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = "billing_flow_purchase", success = false)
        verify(wideEventClient).flowFinish(wideEventId = 123L, status = FlowStatus.Failure(reason = "ITEM_UNAVAILABLE"))
    }

    @Test
    fun `onUserCancelled finishes with cancelled`() = runTest {
        givenFlowStarted()

        wideEvent.onUserCancelled()

        verify(wideEventClient).flowFinish(wideEventId = 123L, status = FlowStatus.Cancelled)
    }

    @Test
    fun `onConfirmationSuccess ends interval, sends step and finishes with success`() = runTest {
        givenFlowStarted()

        wideEvent.onConfirmationSuccess()

        verify(wideEventClient).intervalEnd(wideEventId = 123L, key = "activation_latency_ms_bucketed")
        verify(wideEventClient).flowStep(wideEventId = 123L, stepName = "confirm_purchase", success = true)
        verify(wideEventClient).flowFinish(wideEventId = 123L, status = FlowStatus.Success)
    }

    @Test
    fun `onConfirmationFailure sends failed step with reason and keeps flow open`() = runTest {
        givenFlowStarted()

        wideEvent.onConfirmationFailure("confirm_not_active")

        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "confirm_purchase",
            success = false,
            metadata = mapOf("confirm_failure_reason" to "confirm_not_active"),
        )
        verify(wideEventClient, never()).intervalEnd(any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onSubscriptionUpdated from not auto renewable to auto renewable finishes with success`() = runTest {
        givenFlowStarted()

        wideEvent.onSubscriptionUpdated(
            oldStatus = SubscriptionStatus.NOT_AUTO_RENEWABLE,
            newStatus = SubscriptionStatus.AUTO_RENEWABLE,
        )

        verify(wideEventClient).intervalEnd(wideEventId = 123L, key = "activation_latency_ms_bucketed")
        verify(wideEventClient).flowFinish(wideEventId = 123L, status = FlowStatus.Success)
    }

    @Test
    fun `onSubscriptionUpdated with other transitions does not finish flow`() = runTest {
        givenFlowStarted()

        wideEvent.onSubscriptionUpdated(SubscriptionStatus.NOT_AUTO_RENEWABLE, SubscriptionStatus.NOT_AUTO_RENEWABLE)
        wideEvent.onSubscriptionUpdated(SubscriptionStatus.NOT_AUTO_RENEWABLE, SubscriptionStatus.EXPIRED)
        wideEvent.onSubscriptionUpdated(SubscriptionStatus.WAITING, SubscriptionStatus.AUTO_RENEWABLE)

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `when feature disabled then does nothing`() = runTest {
        subscriptionsFeature.sendSubscriptionUndoCancellationWideEvent().setRawStoredState(Toggle.State(false))

        wideEvent.onFlowStarted(planId = MONTHLY_PLAN_US, isFreeTrial = false, expiresAt = NOW)
        wideEvent.onEligibilityChecked(canUndoInApp = true)
        wideEvent.onConfirmationSuccess()

        verifyNoInteractions(wideEventClient)
    }

    private suspend fun givenFlowStarted() {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))
    }

    private fun anyOrNullString(): String? = org.mockito.kotlin.anyOrNull()

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
