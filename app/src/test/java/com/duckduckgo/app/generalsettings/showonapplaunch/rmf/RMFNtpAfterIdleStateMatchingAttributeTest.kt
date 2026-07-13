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

package com.duckduckgo.app.generalsettings.showonapplaunch.rmf

import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RMFNtpAfterIdleStateMatchingAttributeTest {

    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore = mock()
    private val ntpAfterIdleManager: NtpAfterIdleManager = mock()
    private val feature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val testee = RMFNtpAfterIdleStateMatchingAttribute(showOnAppLaunchOptionDataStore, ntpAfterIdleManager, feature)

    // --- map (this is where the rollout is gated: flags off => attribute is left unmapped) ---

    @Test
    fun whenKeyIsNotNtpAfterIdleStateThenMapReturnsNull() {
        assertNull(testee.map("someOtherKey", JsonMatchingAttribute(value = listOf("eligibleCardShown"))))
    }

    @Test
    fun whenRolloutDisabledThenMapReturnsNull() {
        // both flags default off → attribute not mapped (RMF treats it as Unknown(fallback))
        assertNull(testee.map("ntpAfterIdleState", JsonMatchingAttribute(value = listOf("eligibleCardShown"))))
    }

    @Test
    fun whenRolloutEnabledAndValueHasKnownStatesThenMapReturnsAttribute() {
        enableRollout()

        val result = testee.map("ntpAfterIdleState", JsonMatchingAttribute(value = listOf("eligibleCardShown")))

        assertEquals(NtpAfterIdleStateMatchingAttribute(listOf(NtpAfterIdleState.ELIGIBLE_CARD_SHOWN)), result)
    }

    @Test
    fun whenRolloutEnabledAndValueHasNoKnownStatesThenMapReturnsNull() {
        enableRollout()

        assertNull(testee.map("ntpAfterIdleState", JsonMatchingAttribute(value = listOf("futureState"))))
    }

    // --- evaluate (only reached once mapped, i.e. rollout on; it computes state from the settings) ---

    @Test
    fun whenOptionNotNtpThenNotEligible() = runTest {
        whenever(showOnAppLaunchOptionDataStore.optionFlow).thenReturn(flowOf(LastOpenedTab))

        assertEquals(true, testee.evaluate(attr(NtpAfterIdleState.NOT_ELIGIBLE)))
        assertEquals(false, testee.evaluate(attr(NtpAfterIdleState.ELIGIBLE_CARD_SHOWN)))
    }

    @Test
    fun whenNtpAndCardShownThenEligibleCardShown() = runTest {
        whenever(showOnAppLaunchOptionDataStore.optionFlow).thenReturn(flowOf(NewTabPage))
        whenever(ntpAfterIdleManager.returnToLastTabEnabled).thenReturn(flowOf(true))

        assertEquals(true, testee.evaluate(attr(NtpAfterIdleState.ELIGIBLE_CARD_SHOWN)))
        assertEquals(false, testee.evaluate(attr(NtpAfterIdleState.ELIGIBLE_CARD_HIDDEN)))
    }

    @Test
    fun whenNtpAndCardHiddenThenEligibleCardHidden() = runTest {
        whenever(showOnAppLaunchOptionDataStore.optionFlow).thenReturn(flowOf(NewTabPage))
        whenever(ntpAfterIdleManager.returnToLastTabEnabled).thenReturn(flowOf(false))

        assertEquals(true, testee.evaluate(attr(NtpAfterIdleState.ELIGIBLE_CARD_HIDDEN)))
        assertEquals(false, testee.evaluate(attr(NtpAfterIdleState.ELIGIBLE_CARD_SHOWN)))
    }

    @Test
    fun whenAttributeIsNotNtpAfterIdleStateThenEvaluateReturnsNull() = runTest {
        assertNull(testee.evaluate(object : MatchingAttribute {}))
    }

    private fun attr(vararg states: NtpAfterIdleState) = NtpAfterIdleStateMatchingAttribute(states.toList())

    private fun enableRollout() {
        feature.showNTPAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
        feature.ntpAsDefaultAfterIdleReturn().setRawStoredState(Toggle.State(enable = true))
    }
}
