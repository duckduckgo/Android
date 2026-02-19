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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.ActiveOfferType
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
import com.duckduckgo.subscriptions.impl.repository.Subscription
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.TimeUnit

class FreeTrialConversionWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val wideEventClient: WideEventClient = mock()
    private val authRepository: AuthRepository = mock()
    private val timeProvider: CurrentTimeProvider = mock()
    private val pixelSender: SubscriptionPixelSender = mock()

    @SuppressLint("DenyListedApi")
    private val privacyProFeature: PrivacyProFeature =
        FakeFeatureToggleFactory
            .create(PrivacyProFeature::class.java)
            .apply { sendFreeTrialConversionWideEvent().setRawStoredState(Toggle.State(true)) }

    private lateinit var freeTrialConversionWideEvent: FreeTrialConversionWideEventImpl

    @Before
    fun setup() {
        freeTrialConversionWideEvent = FreeTrialConversionWideEventImpl(
            wideEventClient = wideEventClient,
            privacyProFeature = { privacyProFeature },
            authRepository = authRepository,
            timeProvider = timeProvider,
            dispatchers = coroutineRule.testDispatcherProvider,
            pixelSender = pixelSender,
        )
    }

    @Test
    fun `onFreeTrialStarted starts flow with correct metadata and fires pixel`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any())).thenReturn(Result.success(123L))
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))

        freeTrialConversionWideEvent.onFreeTrialStarted("ddg.privacy.pro.yearly.renews.us")

        verify(pixelSender).reportFreeTrialStart()
        verify(wideEventClient).flowStart(
            name = "free-trial-conversion",
            flowEntryPoint = null,
            metadata = mapOf("free_trial_plan" to "ddg.privacy.pro.yearly.renews.us"),
            cleanupPolicy = CleanupPolicy.OnTimeout(
                duration = Duration.ofDays(8),
                flowStatus = FlowStatus.Failure("timeout"),
            ),
        )
    }

    @Test
    fun `onFreeTrialStarted does not restart flow if one already exists but still fires pixel`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))

        freeTrialConversionWideEvent.onFreeTrialStarted("ddg.privacy.pro.yearly.renews.us")

        verify(pixelSender).reportFreeTrialStart()
        verify(wideEventClient, never()).flowStart(any(), any(), any(), any())
    }

    @Test
    fun `onVpnActivatedSuccessfully records D1 step and fires pixel when activated within first day`() = runTest {
        val subscriptionStartedAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12)
        val subscription = createSubscription(
            startedAt = subscriptionStartedAt,
            activeOffers = listOf(ActiveOfferType.TRIAL),
        )

        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))
        whenever(authRepository.getSubscription()).thenReturn(subscription)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()

        verify(pixelSender).reportFreeTrialVpnActivation("vpn_activated_d1", "google")
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "vpn_activated_d1",
            success = true,
        )
    }

    @Test
    fun `onVpnActivatedSuccessfully records D2-7 step and fires pixel when activated after first day`() = runTest {
        val subscriptionStartedAt = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        val subscription = createSubscription(
            startedAt = subscriptionStartedAt,
            activeOffers = listOf(ActiveOfferType.TRIAL),
        )

        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))
        whenever(authRepository.getSubscription()).thenReturn(subscription)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()

        verify(pixelSender).reportFreeTrialVpnActivation("vpn_activated_d2_to_d7", "google")
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "vpn_activated_d2_to_d7",
            success = true,
        )
    }

    @Test
    fun `onVpnActivatedSuccessfully does not record step or fire pixel if not in free trial`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))
        whenever(authRepository.getSubscription()).thenReturn(createSubscription())
        whenever(authRepository.isFreeTrialActive()).thenReturn(false)

        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()

        verify(pixelSender, never()).reportFreeTrialVpnActivation(any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
    }

    @Test
    fun `onVpnActivatedSuccessfully only records wide event step once per flow but fires pixel each time`() = runTest {
        val subscriptionStartedAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)
        val subscription = createSubscription(
            startedAt = subscriptionStartedAt,
            activeOffers = listOf(ActiveOfferType.TRIAL),
        )

        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))
        whenever(authRepository.getSubscription()).thenReturn(subscription)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        // First activation
        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()
        // Second activation - wide event step should be ignored but pixel fires
        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()

        // Pixel fires each time VPN activates during free trial
        verify(pixelSender, times(2)).reportFreeTrialVpnActivation("vpn_activated_d1", "google")

        // Wide event step only recorded once
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "vpn_activated_d1",
            success = true,
        )
        verify(wideEventClient).getFlowIds(any())
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `onSubscriptionRefreshed finishes flow with success when free trial converts`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))

        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = true,
            isFreeTrial = false,
            isSubscriptionActive = true,
        )

        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onSubscriptionRefreshed finishes flow with failure when free trial expires`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))

        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = true,
            isFreeTrial = false,
            isSubscriptionActive = false,
        )

        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Failure("expired"),
            metadata = emptyMap(),
        )
    }

    @Test
    fun `onSubscriptionRefreshed does not finish flow if still in free trial`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))

        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = true,
            isFreeTrial = true,
            isSubscriptionActive = true,
        )

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `onSubscriptionRefreshed does not finish flow if was not free trial`() = runTest {
        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(listOf(123L)))

        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = false,
            isFreeTrial = false,
            isSubscriptionActive = true,
        )

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun `when feature disabled then no wide events are sent but pixels still fire`() = runTest {
        privacyProFeature.sendFreeTrialConversionWideEvent().setRawStoredState(Toggle.State(false))

        val subscriptionStartedAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(12)
        val subscription = createSubscription(
            startedAt = subscriptionStartedAt,
            activeOffers = listOf(ActiveOfferType.TRIAL),
        )

        whenever(wideEventClient.getFlowIds(any())).thenReturn(Result.success(emptyList()))
        whenever(authRepository.getSubscription()).thenReturn(subscription)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)
        whenever(timeProvider.currentTimeMillis()).thenReturn(System.currentTimeMillis())

        freeTrialConversionWideEvent.onFreeTrialStarted("ddg.privacy.pro.yearly.renews.us")
        freeTrialConversionWideEvent.onVpnActivatedSuccessfully()
        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = true,
            isFreeTrial = false,
            isSubscriptionActive = true,
        )

        // Pixels still fire regardless of wide event feature flag
        verify(pixelSender).reportFreeTrialStart()
        verify(pixelSender).reportFreeTrialVpnActivation("vpn_activated_d1", "google")

        // Wide events do not fire when feature is disabled
        verify(wideEventClient, never()).flowStart(any(), any(), any(), any())
        verify(wideEventClient, never()).flowStep(any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun `flow resets after conversion and pixel fires for new trial`() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any())).thenReturn(Result.success(456L))
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))
            .thenReturn(Result.success(emptyList()))

        // First, finish the flow via conversion
        freeTrialConversionWideEvent.onSubscriptionRefreshed(
            wasFreeTrial = true,
            isFreeTrial = false,
            isSubscriptionActive = true,
        )

        verify(wideEventClient).flowFinish(
            wideEventId = 123L,
            status = FlowStatus.Success,
            metadata = emptyMap(),
        )

        // Now starting a new free trial should create a new flow and fire pixel
        freeTrialConversionWideEvent.onFreeTrialStarted("ddg.privacy.pro.monthly.renews.us")

        verify(pixelSender).reportFreeTrialStart()
        verify(wideEventClient).flowStart(
            name = eq("free-trial-conversion"),
            flowEntryPoint = anyOrNull(),
            metadata = eq(mapOf("free_trial_plan" to "ddg.privacy.pro.monthly.renews.us")),
            cleanupPolicy = any(),
        )
    }

    private fun createSubscription(
        productId: String = "ddg.privacy.pro.yearly.renews.us",
        startedAt: Long = System.currentTimeMillis(),
        status: SubscriptionStatus = SubscriptionStatus.AUTO_RENEWABLE,
        activeOffers: List<ActiveOfferType> = emptyList(),
    ): Subscription {
        return Subscription(
            productId = productId,
            billingPeriod = "yearly",
            startedAt = startedAt,
            expiresOrRenewsAt = startedAt + TimeUnit.DAYS.toMillis(7),
            status = status,
            platform = "google",
            activeOffers = activeOffers,
        )
    }
}
