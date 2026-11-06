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

package com.duckduckgo.subscriptions.impl.stripe

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.EXPIRED
import com.duckduckgo.subscriptions.impl.AccessTokenResult
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.Subscription
import com.duckduckgo.subscriptions.impl.services.TierFeatureResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealStripePlansHandlerTest {

    private val subscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java, FakeToggleStore())
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val stripeTierOptionsProvider: StripeTierOptionsProvider = mock()

    private val handler = RealStripePlansHandler(subscriptionsFeature, subscriptionsManager, stripeTierOptionsProvider)

    @Test
    fun whenFlagEnabledAndActiveStripeSubscriptionThenShouldHandle() {
        givenStripeTierOptionsEnabled(true)

        assertTrue(handler.shouldHandle(subscription(platform = "stripe")))
    }

    @Test
    fun whenFlagDisabledThenShouldNotHandle() {
        givenStripeTierOptionsEnabled(false)

        assertFalse(handler.shouldHandle(subscription(platform = "stripe")))
    }

    @Test
    fun whenGoogleSubscriptionThenShouldNotHandle() {
        givenStripeTierOptionsEnabled(true)

        assertFalse(handler.shouldHandle(subscription(platform = "google")))
    }

    @Test
    fun whenStripeSubscriptionExpiredThenShouldNotHandle() {
        givenStripeTierOptionsEnabled(true)

        assertFalse(handler.shouldHandle(subscription(platform = "stripe", status = EXPIRED)))
    }

    @Test
    fun whenNoSubscriptionThenShouldNotHandle() {
        givenStripeTierOptionsEnabled(true)

        assertFalse(handler.shouldHandle(null))
    }

    @Test
    fun whenGetTierOptionsThenMapStripeProductsToJson() = runTest {
        whenever(stripeTierOptionsProvider.getTierProducts(any())).thenReturn(
            listOf(
                StripeTierProduct(
                    tier = "plus",
                    features = listOf(TierFeatureResponse(product = "NetP", name = "plus")),
                    options = listOf(
                        StripeTierOption(id = "stripe-monthly", displayPrice = "$9.99", recurrence = "monthly"),
                        StripeTierOption(id = "stripe-yearly", displayPrice = "$99.99", recurrence = "yearly"),
                    ),
                ),
            ),
        )

        val options = handler.getTierOptions()

        assertEquals("stripe", options.platform)
        val product = options.products.single()
        assertEquals("plus", product.tier)
        assertEquals("NetP", product.features.single().product)
        assertEquals(listOf("stripe-monthly", "stripe-yearly"), product.options.map { it.id })
        assertEquals("$9.99", product.options.first().cost.displayPrice)
        assertEquals("monthly", product.options.first().cost.recurrence)
        assertNull(product.options.first().offer)
    }

    @Test
    fun whenGetTierOptionsThenProTierFollowsProFlag() = runTest {
        subscriptionsFeature.allowProTierPurchase().setRawStoredState(Toggle.State(enable = false))
        whenever(stripeTierOptionsProvider.getTierProducts(any())).thenReturn(emptyList())

        handler.getTierOptions()

        verify(stripeTierOptionsProvider).getTierProducts(includeProTier = false)
    }

    @Test
    fun whenAccessTokenAvailableThenRedirectEventCarriesToken() = runTest {
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Success("accessToken"))

        val event = handler.getRedirectEvent()!!

        assertEquals("useSubscription", event.featureName)
        assertEquals("onPurchaseUpdate", event.subscriptionName)
        assertEquals("redirect", event.params.getString("type"))
        assertEquals("accessToken", event.params.getString("token"))
    }

    @Test
    fun whenNoAccessTokenThenNoRedirectEvent() = runTest {
        whenever(subscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Failure("error"))

        assertNull(handler.getRedirectEvent())
    }

    @Test
    fun whenPlanChangeCompletedThenRefreshTokensAndSubscription() = runTest {
        handler.onPlanChangeCompleted()

        verify(subscriptionsManager).refreshAccessToken(force = true)
        verify(subscriptionsManager).refreshSubscriptionData()
    }

    @Test
    fun whenTokenRefreshFailsThenSubscriptionStillRefreshed() = runTest {
        whenever(subscriptionsManager.refreshAccessToken(force = true)).thenThrow(RuntimeException())

        handler.onPlanChangeCompleted()

        verify(subscriptionsManager).refreshSubscriptionData()
    }

    private fun givenStripeTierOptionsEnabled(enabled: Boolean) {
        subscriptionsFeature.stripeTierOptions().setRawStoredState(Toggle.State(enable = enabled))
    }

    private fun subscription(platform: String, status: SubscriptionStatus = AUTO_RENEWABLE) = Subscription(
        productId = "productId",
        billingPeriod = "Monthly",
        startedAt = 1234,
        expiresOrRenewsAt = 1701694623000,
        status = status,
        platform = platform,
        activeOffers = listOf(),
    )
}
