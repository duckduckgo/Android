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

package com.duckduckgo.app.browser.newtab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabNextStepsStateTest {

    private val testee = TabNextStepsState()

    @Test
    fun whenNothingHappenedYetThenNextStepsAreNotAllowed() {
        assertFalse(testee.nextStepsAllowed.value)
    }

    @Test
    fun whenNewTabPageIsShownAfterOnboardingThenNextStepsAreAllowed() {
        testee.onNewTabPageShownAfterOnboarding()

        assertTrue(testee.nextStepsAllowed.value)
    }

    @Test
    fun whenOnboardingBubbleWasShownThenNextStepsStayBlockedAfterOnboarding() {
        testee.onOnboardingBubbleShown()
        testee.onNewTabPageShownAfterOnboarding()

        assertFalse(testee.nextStepsAllowed.value)
    }

    @Test
    fun whenOnboardingBubbleIsShownAfterNextStepsWereAllowedThenNextStepsAreBlocked() {
        testee.onNewTabPageShownAfterOnboarding()
        testee.onOnboardingBubbleShown()

        assertFalse(testee.nextStepsAllowed.value)
    }
}
