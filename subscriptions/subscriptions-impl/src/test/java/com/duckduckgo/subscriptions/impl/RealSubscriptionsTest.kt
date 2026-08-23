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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.subscriptions.api.Product.DuckAiPlus
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.api.model.Entitlement
import com.duckduckgo.subscriptions.impl.internal.DefaultSubscriptionsBaseUrl
import com.duckduckgo.subscriptions.impl.internal.RealSubscriptionsUrlProvider
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealSubscriptionsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val mockSubscriptionsManager: SubscriptionsManager = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val pixel: SubscriptionPixelSender = mock()
    private val subscriptionsUrlProvider = RealSubscriptionsUrlProvider(DefaultSubscriptionsBaseUrl())
    private lateinit var subscriptions: RealSubscriptions
    private val subscriptionFeature: SubscriptionsFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)

    private val testSubscriptionOfferList = listOf(
        SubscriptionOffer(
            planId = "test",
            offerId = null,
            tier = "plus",
            pricingPhases = emptyList(),
            entitlements = setOf(Entitlement("plus", SubscriptionsConstants.NETP)),
        ),
    )

    @Before
    fun before() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(true)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(emptyList())
        subscriptions = RealSubscriptions(
            mockSubscriptionsManager,
            globalActivityStarter,
            pixel,
            { subscriptionFeature },
            coroutineRule.testDispatcherProvider,
            subscriptionsUrlProvider,
        )
    }

    @Test
    fun whenGetAccessTokenSucceedsThenReturnAccessToken() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Success("accessToken"))
        val result = subscriptions.getAccessToken()
        assertEquals("accessToken", result)
    }

    @Test
    fun whenGetAccessTokenFailsThenReturnNull() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessTokenResult.Failure("error"))
        assertNull(subscriptions.getAccessToken())
    }

    @Test
    fun whenGetEntitlementStatusHasEntitlementAndEnabledAndActiveThenReturnList() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(listOf(NetP)))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isNotEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetEntitlementStatusHasEntitlementDuckaiButFFDisabledThenReturnRemoveFromList() = runTest {
        subscriptionFeature.duckAiPlus().setRawStoredState(State(false))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(listOf(NetP, DuckAiPlus)))

        subscriptions.getEntitlementStatus().test {
            val entitlements = awaitItem()
            assertFalse(entitlements.contains(DuckAiPlus))
            assertTrue(entitlements.size == 1)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetEntitlementStatusHasEntitlementDuckaiAndFFEnabledThenReturnList() = runTest {
        subscriptionFeature.duckAiPlus().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(listOf(NetP, DuckAiPlus)))

        subscriptions.getEntitlementStatus().test {
            val entitlements = awaitItem()
            assertTrue(entitlements.contains(DuckAiPlus))
            assertTrue(entitlements.size == 2)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetEntitlementStatusHasNoEntitlementAndEnabledAndActiveThenReturnEmptyList() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(emptyList()))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetAvailableProductsHasDuckaiButFFDisabledThenReturnRemoveFromList() = runTest {
        subscriptionFeature.duckAiPlus().setRawStoredState(State(false))
        val subsProducts = setOf(NetP, DuckAiPlus).map { it.value }.toSet()
        whenever(mockSubscriptionsManager.getFeatures()).thenReturn(subsProducts)

        val products = subscriptions.getAvailableProducts()
        assertFalse(products.contains(DuckAiPlus))
        assertTrue(products.size == 1)
    }

    @Test
    fun whenGetAvailableProductsHasDuckaiAndFFEnabledThenReturnList() = runTest {
        subscriptionFeature.duckAiPlus().setRawStoredState(State(true))
        val subsProducts = setOf(NetP, DuckAiPlus).map { it.value }.toSet()
        whenever(mockSubscriptionsManager.getFeatures()).thenReturn(subsProducts)

        val products = subscriptions.getAvailableProducts()
        assertTrue(products.contains(DuckAiPlus))
        assertTrue(products.size == 2)
    }

    @Test
    fun whenIsEligibleIfAllowPurchaseDisabledAndNoActiveSubscriptionThenReturnFalse() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(false))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfAllowPurchaseDisabledButHasActiveSubscriptionThenReturnTrue() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(false))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfOffersReturnedThenReturnTrueRegardlessOfStatus() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnFalseIfNotActiveOrWaiting() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfWaiting() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(WAITING)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfActive() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotEncryptionThenReturnTrueIfActive() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotEncryptionAndNotActiveThenReturnFalse() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenShouldLaunchSubscriptionForUrlThenReturnCorrectValue() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertTrue(subscriptions.shouldLaunchSubscriptionForUrl("https://duckduckgo.com/pro"))
        assertTrue(subscriptions.shouldLaunchSubscriptionForUrl("https://duckduckgo.com/pro?test=test"))
        assertTrue(subscriptions.shouldLaunchSubscriptionForUrl("https://test.duckduckgo.com/pro"))
        assertTrue(subscriptions.shouldLaunchSubscriptionForUrl("https://test.duckduckgo.com/pro?test=test"))
        assertFalse(subscriptions.shouldLaunchSubscriptionForUrl("https://test.duckduckgo.com/pro/test"))
        assertFalse(subscriptions.shouldLaunchSubscriptionForUrl("https://duckduckgo.test.com/pro"))
        assertFalse(subscriptions.shouldLaunchSubscriptionForUrl("https://example.com"))
        assertFalse(subscriptions.shouldLaunchSubscriptionForUrl("duckduckgo.com/pro"))
    }

    @Test
    fun whenShouldLaunchSubscriptionForUrlThenReturnTrue() = runTest {
        subscriptionFeature.allowPurchase().setRawStoredState(State(true))
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertTrue(subscriptions.shouldLaunchSubscriptionForUrl("https://duckduckgo.com/pro"))
    }

    @Test
    fun whenShouldLaunchSubscriptionForUrlAndNotEligibleThenReturnFalse() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertFalse(subscriptions.shouldLaunchSubscriptionForUrl("https://duckduckgo.com/pro"))
    }

    @Test
    fun whenLaunchSubscriptionWithOriginThenPassTheOriginToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/pro?origin=test".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertEquals("test", (captor.lastValue as SubscriptionsWebViewActivityWithParams).origin)
    }

    // region First paywall, performance-optimized (not implemented)

    // The `performanceOptimizedPaywalls` toggle is wired but unread. When it is on, the two
    // first-paywall entry points this app opens itself have to be opened at a URL that names the page
    // and states what the page would otherwise resolve after mount, instead of `/subscriptions` plus
    // a `featurePage` item.
    //
    //     entry point                     URL
    //     ---------------------------------------------------------------------------------
    //     VPN      (no featurePage)       /subscriptions/new/mobile/vpn
    //     Duck.ai  (featurePage=duckai)   /subscriptions/new/mobile/duckai
    //
    //     query item   values                when
    //     ---------------------------------------------------------------------------------
    //     trial        true | false          always, whichever it is
    //     pir          false                 only when the offering excludes Personal Information
    //                                        Removal; the page shows PIR unless told otherwise
    //     origin       unchanged             carried as it is today
    //
    // `trial` is whether the offering includes a free trial; `pir` is whether it includes Personal
    // Information Removal, which is sold in the USA storefront and not in the rest of the world. Both
    // have to be settled before the URL is opened — that is the whole point, since the page ships both
    // CTA labels and both feature lists and reveals one from the URL. Where they are read from,
    // whether the store may be waited on first, and what happens if it never answers are open
    // questions, deliberately not answered here.
    //
    // What must not move: `pir`, `stripe` and `winback` featurePages, and intercepted `/pro` links
    // like the ones the tests below cover, stay on the URL they use today. The first two create or
    // refresh a cart account on mount, which would make a load-time comparison measure the network.

    /**
     * Pending until something produces the URLs above. Remove the `@Ignore` to see it fail, then
     * replace the `fail` with assertions against whatever ends up building them.
     */
    @Test
    @Ignore("Pending: the server-rendered first paywall is not implemented")
    fun whenPerformanceOptimizedPaywallsIsOnThenFirstPaywallOpensTheServerRenderedUrl() = runTest {
        val required = listOf(
            "https://duckduckgo.com/subscriptions/new/mobile/vpn?trial=false",
            "https://duckduckgo.com/subscriptions/new/mobile/vpn?trial=true",
            "https://duckduckgo.com/subscriptions/new/mobile/vpn?trial=false&pir=false",
            "https://duckduckgo.com/subscriptions/new/mobile/vpn?trial=true&pir=false",
            "https://duckduckgo.com/subscriptions/new/mobile/duckai?trial=false",
            "https://duckduckgo.com/subscriptions/new/mobile/duckai?trial=true",
            "https://duckduckgo.com/subscriptions/new/mobile/duckai?trial=false&pir=false",
            "https://duckduckgo.com/subscriptions/new/mobile/duckai?trial=true&pir=false",
        )

        fail("Nothing produces the server-rendered first paywall URLs yet: ${required.joinToString()}")
    }

    /**
     * Fails until the offer-screen impression fires for the URLs above. It is the denominator of the
     * whole comparison, and today it is gated on [Subscriptions.isSubscriptionUrl], which matches only
     * a single-segment `/subscriptions` — so as things stand the treatment arm would report no
     * impressions at all. Whether that gate widens or the impression is decided somewhere else is
     * open; `trial` and `pir` must not affect it either way, since they choose what the page reveals
     * rather than which page it is.
     */
    @Test
    @Ignore("Pending: nothing reports an impression for the server-rendered first paywall")
    fun whenPerformanceOptimizedPaywallIsShownThenOfferScreenImpressionStillFires() = runTest {
        assertFalse(
            "isSubscriptionUrl now matches the server-rendered paywall, so this test needs replacing " +
                "with one that asserts the impression fires",
            subscriptions.isSubscriptionUrl("https://duckduckgo.com/subscriptions/new/mobile/vpn?trial=true".toUri()),
        )

        fail("Nothing fires m_privacy-pro_offer_screen_impression for the server-rendered first paywall yet")
    }

    // endregion

    @Test
    fun whenLaunchProUrlWithFeaturePageThenIncludeInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/pro?featurePage=duckai".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertEquals(
            subscriptionsUrlProvider.buyUrl.appendQueryParams("featurePage=duckai"),
            (captor.lastValue as SubscriptionsWebViewActivityWithParams).url,
        )
    }

    @Test
    fun whenLaunchProWithMultipleQueryParametersThenTheyAreIncludedInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/pro?usePaidDuckAi=true&featurePage=duckai".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertEquals(
            subscriptionsUrlProvider.buyUrl.appendQueryParams("usePaidDuckAi=true&featurePage=duckai"),
            (captor.lastValue as SubscriptionsWebViewActivityWithParams).url,
        )
    }

    @Test
    fun whenLaunchSubscriptionUrlWithFeaturePageThenIncludeInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/subscriptions?featurePage=duckai".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertEquals(
            subscriptionsUrlProvider.buyUrl.appendQueryParams("featurePage=duckai"),
            (captor.lastValue as SubscriptionsWebViewActivityWithParams).url,
        )
    }

    @Test
    fun whenLaunchSubscriptionWithMultipleQueryParametersThenTheyAreIncludedInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/subscriptions?usePaidDuckAi=true&featurePage=duckai".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertEquals(
            subscriptionsUrlProvider.buyUrl.appendQueryParams("usePaidDuckAi=true&featurePage=duckai"),
            (captor.lastValue as SubscriptionsWebViewActivityWithParams).url,
        )
    }

    @Test
    fun whenSubscriptionWithMultipleQueryParametersThenIsSubscriptionUrlReturnsTrue() = runTest {
        assertTrue(subscriptions.isSubscriptionUrl("https://duckduckgo.com/subscriptions?usePaidDuckAi=true&featurePage=duckai".toUri()))
    }

    @Test
    fun whenSubscriptionUrlButNotRootPathThenIsSubscriptionUrlReturnsFalse() = runTest {
        assertFalse(subscriptions.isSubscriptionUrl("https://duckduckgo.com/subscriptions/welcome?usePaidDuckAi=true&featurePage=duckai".toUri()))
    }

    @Test
    fun whenLaunchSubscriptionWithNoOriginThenDoNotPassTheOriginToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchSubscription(context, "https://duckduckgo.com/pro".toUri())

        verify(globalActivityStarter, times(1)).startIntent(eq(context), captor.capture())
        assertNull((captor.lastValue as SubscriptionsWebViewActivityWithParams).origin)
    }

    private fun fakeIntent(): Intent {
        return Intent().also { it.addFlags(FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun String.appendQueryParams(queryParams: String): String {
        val separator = if (this.contains("?")) "&" else "?"
        return this + separator + queryParams
    }
}
