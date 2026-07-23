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

package com.duckduckgo.app.cta.ui

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class SubscriptionPromoModalDeciderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockToggles: ExtendedOnboardingFeatureToggles = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockDismissedCtaDao: DismissedCtaDao = mock()
    private val mockSubscriptions: Subscriptions = mock()

    private val enabledToggle: Toggle = mock { on { isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { isEnabled() } doReturn false }

    private lateinit var testee: RealSubscriptionPromoModalDecider

    @Before
    fun before() = runTest {
        // Defaults: subscription is available but neither promo toggle is enabled -> not eligible.
        whenever(mockToggles.subscriptionPromoModalCta()).thenReturn(disabledToggle)
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(disabledToggle)
        whenever(mockToggles.privacyProCta()).thenReturn(enabledToggle)
        whenever(mockToggles.freeTrialCopy()).thenReturn(disabledToggle)
        whenever(mockSettingsDataStore.hideTips).thenReturn(false)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8))
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_PRIVACY_PRO)).thenReturn(false)
        whenever(mockSubscriptions.isEligible()).thenReturn(true)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(false)

        testee = RealSubscriptionPromoModalDecider(
            extendedOnboardingFeatureToggles = mockToggles,
            appInstallStore = mockAppInstallStore,
            settingsDataStore = mockSettingsDataStore,
            dismissedCtaDao = mockDismissedCtaDao,
            subscriptions = mockSubscriptions,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenNeitherToggleEnabledThenNull() = runTest {
        assertNull(testee.decide())
    }

    @Test
    fun whenSkippedOnboardingEligibleThenSkippedOnboardingFlow() = runTest {
        whenever(mockToggles.subscriptionPromoModalCta()).thenReturn(enabledToggle)
        whenever(mockSettingsDataStore.hideTips).thenReturn(true)

        assertEquals(SubscriptionPromoFlow.SKIPPED_ONBOARDING, testee.decide()?.flow)
    }

    @Test
    fun whenSkippedOnboardingToggleEnabledButHideTipsFalseThenNull() = runTest {
        whenever(mockToggles.subscriptionPromoModalCta()).thenReturn(enabledToggle)
        whenever(mockSettingsDataStore.hideTips).thenReturn(false)

        assertNull(testee.decide())
    }

    @Test
    fun whenNudgeEligibleThenNudgeFlow() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)

        assertEquals(SubscriptionPromoFlow.NUDGE, testee.decide()?.flow)
    }

    @Test
    fun whenAlreadyShownThenNull() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)
        whenever(mockDismissedCtaDao.exists(CtaId.DAX_INTRO_PRIVACY_PRO)).thenReturn(true)

        assertNull(testee.decide())
    }

    @Test
    fun whenInstalledLessThanMinDaysThenNull() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

        assertNull(testee.decide())
    }

    @Test
    fun whenNotSubscriptionEligibleThenNull() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)
        whenever(mockSubscriptions.isEligible()).thenReturn(false)

        assertNull(testee.decide())
    }

    @Test
    fun whenAlreadySubscribedThenNull() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)
        whenever(mockSubscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        assertNull(testee.decide())
    }

    @Test
    fun whenFreeTrialCopyEnabledAndEligibleThenFreeTrialCopyTrue() = runTest {
        whenever(mockToggles.subscriptionPromoModalCtaExistingUsers()).thenReturn(enabledToggle)
        whenever(mockToggles.freeTrialCopy()).thenReturn(enabledToggle)
        whenever(mockSubscriptions.isFreeTrialEligible()).thenReturn(true)

        assertTrue(testee.decide()?.isFreeTrialCopy == true)
    }
}
