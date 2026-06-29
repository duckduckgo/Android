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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireModeAvailability
import com.duckduckgo.browsermode.impl.FireModeFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealFireTabsPromosTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireModeAvailability: FireModeAvailability = mock()
    private val fireModeFeature: FireModeFeature = mock()
    private val promoToggle: Toggle = mock()
    private val fireDataStore: FireDataStore = mock()
    private val onboardingFlowChecker: OnboardingFlowChecker = mock()
    private val browserModeStateHolder: BrowserModeStateHolder = mock()

    private val testee by lazy {
        RealFireTabsPromos(
            fireModeAvailability = fireModeAvailability,
            fireModeFeature = fireModeFeature,
            fireDataStore = fireDataStore,
            onboardingFlowChecker = onboardingFlowChecker,
            browserModeStateHolder = browserModeStateHolder,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Before
    fun setup() {
        whenever(fireModeFeature.fireTabsPromo()).thenReturn(promoToggle)
        whenever(browserModeStateHolder.currentMode).thenReturn(MutableStateFlow(BrowserMode.REGULAR))
    }

    private suspend fun allNtpConditionsMet() {
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        whenever(promoToggle.isEnabled()).thenReturn(true)
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(fireDataStore.isNtpPromoDismissed()).thenReturn(false)
        whenever(fireDataStore.hasUserBurnedWhileBrowsing()).thenReturn(true)
    }

    @Test
    fun whenAllNtpConditionsMetThenCanShowNtpPromoTrue() = runTest {
        allNtpConditionsMet()
        assertTrue(testee.canShowNtpPromo())
    }

    @Test
    fun whenNotAvailableThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenPromoFlagDisabledThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(promoToggle.isEnabled()).thenReturn(false)
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenInFireModeThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(browserModeStateHolder.currentMode).thenReturn(MutableStateFlow(BrowserMode.FIRE))
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenNtpDismissedThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(fireDataStore.isNtpPromoDismissed()).thenReturn(true)
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenUserNotBurnedThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(fireDataStore.hasUserBurnedWhileBrowsing()).thenReturn(false)
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenOnboardingIncompleteThenCanShowNtpPromoFalse() = runTest {
        allNtpConditionsMet()
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(false)
        assertFalse(testee.canShowNtpPromo())
    }

    @Test
    fun whenCommonGateMetAndNotDismissedThenCanShowTabSwitcherPromoTrue() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        whenever(promoToggle.isEnabled()).thenReturn(true)
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(fireDataStore.isTabSwitcherPromoDismissed()).thenReturn(false)
        assertTrue(testee.canShowTabSwitcherPromo())
    }

    @Test
    fun whenTabSwitcherDismissedThenCanShowTabSwitcherPromoFalse() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        whenever(promoToggle.isEnabled()).thenReturn(true)
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(fireDataStore.isTabSwitcherPromoDismissed()).thenReturn(true)
        assertFalse(testee.canShowTabSwitcherPromo())
    }

    @Test
    fun whenInFireModeThenCanShowTabSwitcherPromoFalse() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(true)
        whenever(promoToggle.isEnabled()).thenReturn(true)
        whenever(onboardingFlowChecker.isOnboardingComplete()).thenReturn(true)
        whenever(fireDataStore.isTabSwitcherPromoDismissed()).thenReturn(false)
        whenever(browserModeStateHolder.currentMode).thenReturn(MutableStateFlow(BrowserMode.FIRE))
        assertFalse(testee.canShowTabSwitcherPromo())
    }

    @Test
    fun whenOnFireModeEnteredThenBothPromosDismissed() = runTest {
        testee.onFireModeEntered()
        verify(fireDataStore).setNtpPromoDismissed(true)
        verify(fireDataStore).setTabSwitcherPromoDismissed(true)
    }

    @Test
    fun whenOnUserBurnedThenBurnedRecorded() = runTest {
        testee.onUserBurned()
        verify(fireDataStore).setUserBurnedWhileBrowsing(true)
    }

    @Test
    fun whenOnNtpPromoInteractedThenNtpDismissed() = runTest {
        testee.onNtpPromoInteracted()
        verify(fireDataStore).setNtpPromoDismissed(true)
    }

    @Test
    fun whenOnTabSwitcherPromoShownThenTabSwitcherDismissed() = runTest {
        testee.onTabSwitcherPromoShown()
        verify(fireDataStore).setTabSwitcherPromoDismissed(true)
    }
}
