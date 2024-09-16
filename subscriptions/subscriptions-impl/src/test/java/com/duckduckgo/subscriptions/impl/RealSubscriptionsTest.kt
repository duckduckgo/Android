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
import com.duckduckgo.subscriptions.api.SubscriptionStatus.INACTIVE
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.SubscriptionStatus.WAITING
import com.duckduckgo.subscriptions.impl.pixels.SubscriptionPixelSender
import com.duckduckgo.subscriptions.impl.ui.SubscriptionsWebViewActivityWithParams
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
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

    @Before
    fun before() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(true)
        subscriptions = RealSubscriptions(mockSubscriptionsManager, globalActivityStarter, pixel)
    }

    @Test
    fun whenGetAccessTokenSucceedsThenReturnAccessToken() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Success("accessToken"))
        val result = subscriptions.getAccessToken()
        assertEquals("accessToken", result)
    }

    @Test
    fun whenGetAccessTokenFailsThenReturnNull() = runTest {
        whenever(mockSubscriptionsManager.getAccessToken()).thenReturn(AccessToken.Failure("error"))
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
    fun whenGetEntitlementStatusHasEntitlementAndEnabledAndInactiveThenReturnEmptyList() = runTest {
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(INACTIVE)
        whenever(mockSubscriptionsManager.entitlements).thenReturn(flowOf(listOf(NetP)))

        subscriptions.getEntitlementStatus().test {
            assertTrue(awaitItem().isEmpty())
            verify(mockSubscriptionsManager).removeEntitlements()
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

    @Test
    fun whenIsEligibleIfNotEncryptionThenReturnTrueIfActive() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(AUTO_RENEWABLE)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(monthlyPlanId = "test", yearlyFormattedPrice = "test", yearlyPlanId = "test", monthlyFormattedPrice = "test"),
        )
        assertTrue(subscriptions.isEligible())
    }

    @Test
    fun whenIsEligibleIfNotEncryptionAndNotActiveThenReturnFalse() = runTest {
        whenever(mockSubscriptionsManager.canSupportEncryption()).thenReturn(false)
        whenever(mockSubscriptionsManager.subscriptionStatus()).thenReturn(UNKNOWN)
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(monthlyPlanId = "test", yearlyFormattedPrice = "test", yearlyPlanId = "test", monthlyFormattedPrice = "test"),
        )
        assertFalse(subscriptions.isEligible())
    }

    @Test
    fun whenShouldLaunchPrivacyProForUrlThenReturnCorrectValue() = runTest {
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(monthlyPlanId = "test", yearlyFormattedPrice = "test", yearlyPlanId = "test", monthlyFormattedPrice = "test"),
        )
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
        whenever(mockSubscriptionsManager.getSubscriptionOffer()).thenReturn(
            SubscriptionOffer(monthlyPlanId = "test", yearlyFormattedPrice = "test", yearlyPlanId = "test", monthlyFormattedPrice = "test"),
        )
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
