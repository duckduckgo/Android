/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.impl.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealSubscriptionsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockSubscriptionsManager: SubscriptionsManager = mock()
    private val pixelSender: SubscriptionPixelSender = mock()
    private lateinit var subscriptions: RealSubscriptions
    private val privacyProFeature = FakeFeatureToggleFactory.create(PrivacyProFeature::class.java, FakeToggleStore())

    @Before
    fun before() {
        subscriptions = RealSubscriptions(mockSubscriptionsManager, privacyProFeature, pixelSender)
    }

    @Test
    fun whenSubscriptionDataSucceedsThenReturnAccessToken() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Success("accessToken"))
        privacyProFeature.isLaunched().setEnabled(State(enable = true))
        val result = subscriptions.getAccessToken()
        assertEquals("accessToken", result)
    }

    @Test
    fun whenSubscriptionDataFailsThenReturnNull() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Failure("error"))
        assertNull(subscriptions.getAccessToken())
    }

    @Test
    fun whenSubscriptionDataHasEntitlementThenReturnList() = runTest {
        privacyProFeature.isLaunched().setEnabled(State(enable = true))
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(listOf(NetP)))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isNotEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSubscriptionDataHasNoEntitlementThenReturnEmptyList() = runTest {
        privacyProFeature.isLaunched().setEnabled(State(enable = true))
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(emptyList()))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenIsEligibleIfOffersReturnedThenReturnTrueRegardlessOfStatus() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(monthlyPlanId = "test", yearlyFormattedPrice = "test", yearlyPlanId = "test", monthlyFormattedPrice = "test"),
        )
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnFalseIfNotActiveOrWaiting() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(null)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfWaiting() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(WAITING)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(null)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfActive() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(null)
        assertTrue(subscriptions.isEligible())
    }
}
