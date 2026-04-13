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

package com.duckduckgo.subscriptions.impl.pixels

import android.annotation.SuppressLint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestWorkerBuilder
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class PaywallNotSeenWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixelSender: SubscriptionPixelSender = mock()
    private val paywallMetricsManager: PaywallMetricsManager = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val subscriptions: Subscriptions = mock()
    private val subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    @Before
    fun setup() {
        subscriptionsFeature.schedulePaywallNotSeenPixels().setRawStoredState(State(enable = true))
    }

    private fun buildWorker(dayBucket: String?): PaywallNotSeenWorker {
        val inputData = dayBucket?.let {
            androidx.work.workDataOf(PaywallNotSeenWorker.KEY_DAY_BUCKET to it)
        } ?: androidx.work.Data.EMPTY

        return TestWorkerBuilder
            .from(ApplicationProvider.getApplicationContext(), PaywallNotSeenWorker::class.java)
            .setInputData(inputData)
            .build()
            .also { worker ->
                worker.pixelSender = pixelSender
                worker.paywallMetricsManager = paywallMetricsManager
                worker.appBuildConfig = appBuildConfig
                worker.subscriptions = subscriptions
                worker.subscriptionFeature = subscriptionsFeature
            }
    }

    @Test
    fun `when feature flag is disabled then pixel is not fired`() = runTest {
        subscriptionsFeature.schedulePaywallNotSeenPixels().setRawStoredState(State(enable = false))

        val result = buildWorker("d0").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen(any(), any(), any(), any())
    }

    @Test
    fun `when day bucket is missing then worker returns failure`() = runTest {
        val result = buildWorker(dayBucket = null).doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `when user is not eligible then pixel is not fired`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)

        val result = buildWorker("d0").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen(any(), any(), any(), any())
    }

    @Test
    fun `when user has active subscription then pixel is not fired`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        val result = buildWorker("d0").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen(any(), any(), any(), any())
    }

    @Test
    fun `when paywall was already seen then pixel is not fired`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(true)

        val result = buildWorker("d0").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen(any(), any(), any(), any())
    }

    @Test
    fun `when pixel was already fired for this day then pixel is not fired again`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d3")).thenReturn(true)

        val result = buildWorker("d3").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen(any(), any(), any(), any())
    }

    @Test
    fun `when paywall not seen and pixel not fired then fires pixel and marks day as fired`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d7")).thenReturn(false)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(paywallMetricsManager.privacyDashboardEverOpened).thenReturn(false)
        whenever(paywallMetricsManager.subscriptionPromoShown).thenReturn(false)

        val result = buildWorker("d7").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender).reportPaywallNotSeen("d7", false, false, false)
        verify(paywallMetricsManager).markNotSeenDayFired("d7")
    }

    @Test
    fun `when app is a reinstall then returning_user is true`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d0")).thenReturn(false)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(paywallMetricsManager.privacyDashboardEverOpened).thenReturn(false)
        whenever(paywallMetricsManager.subscriptionPromoShown).thenReturn(false)

        buildWorker("d0").doWork()

        verify(pixelSender).reportPaywallNotSeen("d0", true, false, false)
    }

    @Test
    fun `when app is a fresh install then returning_user is false`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d0")).thenReturn(false)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(paywallMetricsManager.privacyDashboardEverOpened).thenReturn(false)
        whenever(paywallMetricsManager.subscriptionPromoShown).thenReturn(false)

        buildWorker("d0").doWork()

        verify(pixelSender).reportPaywallNotSeen("d0", false, false, false)
    }

    @Test
    fun `when privacy dashboard was opened then privacy_dashboard_ever_opened is true`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d0")).thenReturn(false)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(paywallMetricsManager.privacyDashboardEverOpened).thenReturn(true)
        whenever(paywallMetricsManager.subscriptionPromoShown).thenReturn(false)

        buildWorker("d0").doWork()

        verify(pixelSender).reportPaywallNotSeen("d0", false, true, false)
    }

    @Test
    fun `when subscription promo was shown then subscription_promo_shown is true`() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d0")).thenReturn(false)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
        whenever(paywallMetricsManager.privacyDashboardEverOpened).thenReturn(false)
        whenever(paywallMetricsManager.subscriptionPromoShown).thenReturn(true)

        buildWorker("d0").doWork()

        verify(pixelSender).reportPaywallNotSeen("d0", false, false, true)
    }
}
