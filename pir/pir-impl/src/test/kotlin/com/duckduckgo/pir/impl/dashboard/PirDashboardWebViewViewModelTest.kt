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

package com.duckduckgo.pir.impl.dashboard

import app.cash.turbine.test
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewViewModel.Command.LaunchSubscriptionPurchase
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute
import com.duckduckgo.pir.impl.pixels.PirInteractionReporter
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PirDashboardWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirPixelSender: PirPixelSender = mock()
    private val pirInteractionReporter: PirInteractionReporter = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val pirRepository: PirRepository = mock()
    private val subscriptions: Subscriptions = mock()
    private val subscriptionStatus = MutableStateFlow(SubscriptionStatus.UNKNOWN)

    private val route = PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir")

    private lateinit var testee: PirDashboardWebViewViewModel

    @Before
    fun setUp() {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatus)
        testee = PirDashboardWebViewViewModel(
            pirPixelSender = pirPixelSender,
            pirInteractionReporter = pirInteractionReporter,
            appBuildConfig = appBuildConfig,
            pirRepository = pirRepository,
            subscriptions = subscriptions,
            appCoroutineScope = coroutineTestRule.testScope,
        )
    }

    @Test
    fun `test emits launch purchase command carrying the routes parameters`() = runTest {
        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(
                PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir"),
            )

            assertEquals(
                LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"),
                awaitItem(),
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test ignores a second purchase request while one is already outstanding`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)
            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())

            testee.onSubscriptionPurchaseRequested(route)

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test allows a purchase request again after returning to the dashboard`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            testee.onSubscriptionPurchaseRequested(route)
            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())

            testee.onStart(mock())
            testee.onSubscriptionPurchaseRequested(route)

            assertEquals(LaunchSubscriptionPurchase(origin = "funnel_freescan_android", featurePage = "pir"), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not reload on first subscription status emission`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test reloads when the user becomes signed in`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE

            assertEquals(Command.ReloadWebView, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `test does not reload when the status changes within signed in`() = runTest {
        advanceUntilIdle()

        testee.commands().test {
            subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE
            assertEquals(Command.ReloadWebView, awaitItem())

            subscriptionStatus.value = SubscriptionStatus.GRACE_PERIOD

            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }
}
