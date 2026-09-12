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

package com.duckduckgo.subscriptions.impl.internal

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.model.Entitlement
import com.duckduckgo.subscriptions.impl.SubscriptionOffer
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.DUCK_AI_FEATURE_PAGE
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.VPN_FEATURE_PAGE
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealPaywallUrlResolverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val subscriptionsFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)
    private val subscriptionsUrlProvider = RealSubscriptionsUrlProvider(DefaultSubscriptionsBaseUrl())
    private val paywallPathProvider: PaywallPathProvider = mock()

    private val testee = RealPaywallUrlResolver(
        subscriptionsUrlProvider = subscriptionsUrlProvider,
        subscriptionsManager = subscriptionsManager,
        subscriptionsFeature = subscriptionsFeature,
        paywallPathProvider = paywallPathProvider,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Before
    fun before() = runTest {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(State(remoteEnableState = true))
        givenEntryPointPath(featurePage = VPN_FEATURE_PAGE, path = "/subscriptions/new/mobile/vpn")
        givenEntryPointPath(featurePage = DUCK_AI_FEATURE_PAGE, path = "/subscriptions/new/mobile/duckai")
        givenOfferedProducts(Product.NetP.value)
        givenFreeTrialEligible(false)
    }

    @Test
    fun whenFeatureDisabledThenUrlIsUnchanged() = runTest {
        subscriptionsFeature.performanceOptimizedPaywalls().setRawStoredState(State(remoteEnableState = false))

        assertEquals(BUY_URL, testee.resolve(BUY_URL))
        assertEquals("$BUY_URL?featurePage=duckai", testee.resolve("$BUY_URL?featurePage=duckai"))
    }

    @Test
    fun whenVpnPaywallThenServerRenderedVpnUrl() = runTest {
        assertEquals("$BUY_URL/new/mobile/vpn?trial=false&pir=false", testee.resolve(BUY_URL))
    }

    @Test
    fun whenDuckAiPaywallThenServerRenderedDuckAiUrl() = runTest {
        assertEquals("$BUY_URL/new/mobile/duckai?trial=false&pir=false", testee.resolve("$BUY_URL?featurePage=duckai"))
    }

    @Test
    fun whenFreeTrialEligibleThenTrialIsTrue() = runTest {
        givenFreeTrialEligible(true)

        assertEquals("$BUY_URL/new/mobile/vpn?trial=true&pir=false", testee.resolve(BUY_URL))
    }

    @Test
    fun whenPirIsOnOfferThenPirParamIsOmitted() = runTest {
        givenOfferedProducts(Product.NetP.value, Product.PIR.value)

        assertEquals("$BUY_URL/new/mobile/vpn?trial=false", testee.resolve(BUY_URL))
        assertEquals("$BUY_URL/new/mobile/duckai?trial=false", testee.resolve("$BUY_URL?featurePage=duckai"))
    }

    @Test
    fun whenVpnNamedExplicitlyAsFeaturePageThenServerRenderedVpnUrl() = runTest {
        assertEquals("$BUY_URL/new/mobile/vpn?trial=false&pir=false", testee.resolve("$BUY_URL?featurePage=vpn"))
    }

    @Test
    fun whenUnknownFeaturePageThenUrlIsUnchanged() = runTest {
        val url = "$BUY_URL?featurePage=itr"

        assertEquals(url, testee.resolve(url))
    }

    @Test
    fun whenUrlCarriesOtherParamsThenTheyAreCarriedOntoTheNewPath() = runTest {
        assertEquals(
            "$BUY_URL/new/mobile/vpn?origin=funnel_appsettings_android&trial=false&pir=false",
            testee.resolve("$BUY_URL?origin=funnel_appsettings_android"),
        )
        assertEquals(
            "$BUY_URL/new/mobile/duckai?origin=funnel_appsettings_android&trial=false&pir=false",
            testee.resolve("$BUY_URL?featurePage=duckai&origin=funnel_appsettings_android"),
        )
    }

    @Test
    fun whenFeaturePageIsBlankThenServerRenderedVpnUrl() = runTest {
        assertEquals("$BUY_URL/new/mobile/vpn?trial=false&pir=false", testee.resolve("$BUY_URL?featurePage="))
    }

    @Test
    fun whenAParamRepeatsThenEveryValueIsCarriedOntoTheNewPath() = runTest {
        assertEquals(
            "$BUY_URL/new/mobile/vpn?origin=first&origin=second&trial=false&pir=false",
            testee.resolve("$BUY_URL?origin=first&origin=second"),
        )
    }

    @Test
    fun whenAParamIsEncodedThenItStaysEncodedOnTheNewPath() = runTest {
        assertEquals(
            "$BUY_URL/new/mobile/vpn?origin=funnel%20appsettings%26x%3D1&trial=false&pir=false",
            testee.resolve("$BUY_URL?origin=funnel%20appsettings%26x%3D1"),
        )
    }

    @Test
    fun whenUrlAlreadyStatesTrialOrPirThenOurAnswerReplacesIt() = runTest {
        assertEquals(
            "$BUY_URL/new/mobile/vpn?trial=false&pir=false",
            testee.resolve("$BUY_URL?trial=true&pir=true"),
        )
    }

    @Test
    fun whenNotAPaywallUrlThenUrlIsUnchanged() = runTest {
        assertEquals(subscriptionsUrlProvider.welcomeUrl, testee.resolve(subscriptionsUrlProvider.welcomeUrl))
        assertEquals(subscriptionsUrlProvider.activateUrl, testee.resolve(subscriptionsUrlProvider.activateUrl))
        assertEquals(subscriptionsUrlProvider.manageUrl, testee.resolve(subscriptionsUrlProvider.manageUrl))
        assertEquals(subscriptionsUrlProvider.plansUrl, testee.resolve(subscriptionsUrlProvider.plansUrl))
        assertEquals(subscriptionsUrlProvider.upgradeToProUrl, testee.resolve(subscriptionsUrlProvider.upgradeToProUrl))
    }

    @Test
    fun whenNoOffersAreAvailableThenUrlIsUnchanged() = runTest {
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(emptyList())

        assertEquals(BUY_URL, testee.resolve(BUY_URL))
        assertEquals("$BUY_URL?featurePage=duckai", testee.resolve("$BUY_URL?featurePage=duckai"))
    }

    @Test
    fun whenResolvingFailsThenUrlIsUnchanged() = runTest {
        whenever(subscriptionsManager.getSubscriptionOffer()).thenThrow(RuntimeException())

        assertEquals(BUY_URL, testee.resolve(BUY_URL))
    }

    @Test
    fun whenBaseUrlIsOverriddenThenServerRenderedUrlIsBuiltOnIt() = runTest {
        val overriddenBaseUrl = "https://example.devtunnels.ms/subscriptions"
        val testeeWithOverriddenBaseUrl = RealPaywallUrlResolver(
            subscriptionsUrlProvider = RealSubscriptionsUrlProvider(
                object : SubscriptionsBaseUrl {
                    override val subscriptionsBaseUrl = overriddenBaseUrl
                },
            ),
            subscriptionsManager = subscriptionsManager,
            subscriptionsFeature = subscriptionsFeature,
            paywallPathProvider = paywallPathProvider,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )

        assertEquals(
            "$overriddenBaseUrl/new/mobile/duckai?trial=false&pir=false",
            testeeWithOverriddenBaseUrl.resolve("$overriddenBaseUrl?featurePage=duckai"),
        )
    }

    @Test
    fun whenUrlIsNotASubscriptionsUrlThenUrlIsUnchanged() = runTest {
        val url = "https://example.com/subscriptions"

        assertEquals(url, testee.resolve(url))
    }

    @Test
    fun whenNoEntryPointForTheFeaturePageThenOnlyThatPageIsUnchanged() = runTest {
        givenEntryPointPath(featurePage = DUCK_AI_FEATURE_PAGE, path = null)

        assertEquals("$BUY_URL/new/mobile/vpn?trial=false&pir=false", testee.resolve(BUY_URL))
        assertEquals("$BUY_URL?featurePage=duckai", testee.resolve("$BUY_URL?featurePage=duckai"))
    }

    @Test
    fun whenEntryPointPathChangesThenServerRenderedUrlFollowsIt() = runTest {
        givenEntryPointPath(featurePage = VPN_FEATURE_PAGE, path = "/subscriptions/faster/vpn")

        assertEquals("$BUY_URL/faster/vpn?trial=false&pir=false", testee.resolve(BUY_URL))
    }

    private fun givenEntryPointPath(featurePage: String, path: String?) {
        whenever(paywallPathProvider.getPath(featurePage)).thenReturn(path)
    }

    private suspend fun givenFreeTrialEligible(eligible: Boolean) {
        whenever(subscriptionsManager.isFreeTrialEligible()).thenReturn(eligible)
    }

    private suspend fun givenOfferedProducts(vararg products: String) {
        whenever(subscriptionsManager.getSubscriptionOffer()).thenReturn(
            listOf(
                SubscriptionOffer(
                    planId = "test",
                    offerId = null,
                    tier = "plus",
                    pricingPhases = emptyList(),
                    entitlements = products.map { Entitlement(name = "plus", product = it) }.toSet(),
                ),
            ),
        )
    }

    private companion object {
        const val BUY_URL = "https://duckduckgo.com/subscriptions"
    }
}
