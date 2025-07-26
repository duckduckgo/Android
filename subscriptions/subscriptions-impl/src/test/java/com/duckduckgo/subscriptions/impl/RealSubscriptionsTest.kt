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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.SubscriptionStatus.AUTO_RENEWABLE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.BUY_URL
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
class RealSubscriptionsTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val mockSubscriptionsManager: SubscriptionsManager = mock()
    private val globalActivityStarter: GlobalActivityStarter = mock()
    private val pixel: SubscriptionPixelSender = mock()
    private lateinit var subscriptions: RealSubscriptions

    private val testSubscriptionOfferList = listOf(
        SubscriptionOffer(
            planId = "test",
            offerId = null,
            pricingPhases = emptyList(),
            features = setOf(SubscriptionsConstants.NETP),
        ),
    )

    @Before
    fun before() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(true)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(emptyList())
        subscriptions = RealSubscriptions(mockSubscriptionsManager, globalActivityStarter, pixel)
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
    fun whenGetEntitlementStatusHasNoEntitlementAndEnabledAndActiveThenReturnEmptyList() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(emptyList()))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenIsEligibleIfOffersReturnedThenReturnTrueRegardlessOfStatus() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnFalseIfNotActiveOrWaiting() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfWaiting() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(WAITING)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotOffersReturnedThenReturnTrueIfActive() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotEncryptionThenReturnTrueIfActive() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotEncryptionAndNotActiveThenReturnFalse() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenShouldLaunchPrivacyProForUrlThenReturnCorrectValue() = runTest {
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertTrue(subscriptions.shouldLaunchPrivacyProForUrl("https://duckduckgo.com/pro"))
        assertTrue(subscriptions.shouldLaunchPrivacyProForUrl("https://duckduckgo.com/pro?test=test"))
        assertTrue(subscriptions.shouldLaunchPrivacyProForUrl("https://test.duckduckgo.com/pro"))
        assertTrue(subscriptions.shouldLaunchPrivacyProForUrl("https://test.duckduckgo.com/pro?test=test"))
        assertFalse(subscriptions.shouldLaunchPrivacyProForUrl("https://test.duckduckgo.com/pro/test"))
        assertFalse(subscriptions.shouldLaunchPrivacyProForUrl("https://duckduckgo.test.com/pro"))
        assertFalse(subscriptions.shouldLaunchPrivacyProForUrl("https://example.com"))
        assertFalse(subscriptions.shouldLaunchPrivacyProForUrl("duckduckgo.com/pro"))
    }

    @Test
    fun whenShouldLaunchPrivacyProForUrlThenReturnTrue() = runTest {
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(testSubscriptionOfferList)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertTrue(subscriptions.shouldLaunchPrivacyProForUrl("https://duckduckgo.com/pro"))
    }

    @Test
    fun whenShouldLaunchPrivacyProForUrlAndNotEligibleThenReturnFalse() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)

        assertFalse(subscriptions.shouldLaunchPrivacyProForUrl("https://duckduckgo.com/pro"))
    }

    @Test
    fun whenLaunchPrivacyProWithOriginThenPassTheOriginToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/pro?origin=test".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertEquals("test", (captor.lastValue as SubscriptionsWebViewActivityWithParams).origin)
    }

    @Test
    fun whenLaunchProUrlWithFeaturePageThenIncludeInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/pro?featurePage=duckai".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertEquals("$BUY_URL?featurePage=duckai", (captor.lastValue as SubscriptionsWebViewActivityWithParams).url)
    }

    @Test
    fun whenLaunchProWithMultipleQueryParametersThenTheyAreIncludedInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/pro?usePaidDuckAi=true&featurePage=duckai".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertEquals("$BUY_URL?usePaidDuckAi=true&featurePage=duckai", (captor.lastValue as SubscriptionsWebViewActivityWithParams).url)
    }

    @Test
    fun whenLaunchSubscriptionUrlWithFeaturePageThenIncludeInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/subscriptions?featurePage=duckai".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertEquals("$BUY_URL?featurePage=duckai", (captor.lastValue as SubscriptionsWebViewActivityWithParams).url)
    }

    @Test
    fun whenLaunchSubscriptionWithMultipleQueryParametersThenTheyAreIncludedInSubscriptionURLToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/subscriptions?usePaidDuckAi=true&featurePage=duckai".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertEquals("$BUY_URL?usePaidDuckAi=true&featurePage=duckai", (captor.lastValue as SubscriptionsWebViewActivityWithParams).url)
    }

    @Test
    fun whenSubscriptionWithMultipleQueryParametersThenIsPrivacyProUrlReturnsTrue() = runTest {
        assertTrue(subscriptions.isPrivacyProUrl("https://duckduckgo.com/subscriptions?usePaidDuckAi=true&featurePage=duckai".toUri()))
    }

    @Test
    fun whenSubscriptionUrlButNotRootPathThenIsPrivacyProUrlReturnsFalse() = runTest {
        assertFalse(subscriptions.isPrivacyProUrl("https://duckduckgo.com/subscriptions/welcome?usePaidDuckAi=true&featurePage=duckai".toUri()))
    }

    @Test
    fun whenLaunchPrivacyProWithNoOriginThenDoNotPassTheOriginToActivity() = runTest {
        whenever(globalActivityStarter.startIntent(any(), any<SettingsScreenNoParams>())).thenReturn(fakeIntent())
        whenever(globalActivityStarter.startIntent(any(), any<SubscriptionsWebViewActivityWithParams>())).thenReturn(fakeIntent())

        val captor = argumentCaptor<ActivityParams>()
        subscriptions.launchPrivacyPro(context, "https://duckduckgo.com/pro".toUri())

        verify(globalActivityStarter, times(2)).startIntent(eq(context), captor.capture())
        assertNull((captor.lastValue as SubscriptionsWebViewActivityWithParams).origin)
    }

    private fun fakeIntent(): Intent {
        return Intent().also { it.addFlags(FLAG_ACTIVITY_NEW_TASK) }
    }
}
