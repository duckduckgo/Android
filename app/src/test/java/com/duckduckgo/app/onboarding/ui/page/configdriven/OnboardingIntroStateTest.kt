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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.ui.page.configdriven.OnboardingIntroState.Handover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingIntroStateTest {

    private val testee = OnboardingIntroState()

    @Test
    fun `a view that is playing the intro has nothing to restore`() {
        testee.play()

        assertFalse(testee.restore())
    }

    @Test
    fun `a view that never played the intro restores it once`() {
        assertTrue(testee.restore())
        assertFalse(testee.restore())
    }

    @Test
    fun `a dialog arriving over a playing intro fades it out and can cross-fade the background`() {
        testee.play()

        val handover = testee.handOverToDialog()

        assertEquals(Handover.FadeOut, handover)
        assertTrue(handover.canCrossFadeBackground)
    }

    @Test
    fun `a dialog arriving over a restored intro fades it out`() {
        testee.restore()

        assertEquals(Handover.FadeOut, testee.handOverToDialog())
    }

    @Test
    fun `a dialog arriving with no intro on screen snaps it away and snaps the background`() {
        val handover = testee.handOverToDialog()

        assertEquals(Handover.SnapAway, handover)
        assertFalse(handover.canCrossFadeBackground)
    }

    @Test
    fun `later dialogs leave the intro alone and keep animating the background`() {
        testee.play()
        testee.handOverToDialog()

        val handover = testee.handOverToDialog()

        assertEquals(Handover.AlreadyHandedOver, handover)
        assertTrue(handover.canCrossFadeBackground)
    }

    @Test
    fun `a dialog arriving after the intro was dismissed unplayed snaps the background`() {
        assertTrue(testee.dismissUnplayed())

        val handover = testee.handOverToDialog()

        assertEquals(Handover.AlreadyDismissed, handover)
        assertFalse(handover.canCrossFadeBackground)
    }

    @Test
    fun `dismissing after a dialog took over is a no-op`() {
        testee.play()
        testee.handOverToDialog()

        assertFalse(testee.dismissUnplayed())
    }

    @Test
    fun `dismissing an intro that is on screen is a no-op`() {
        testee.play()

        assertFalse(testee.dismissUnplayed())
    }

    @Test
    fun `dismissing twice snaps the intro away once`() {
        assertTrue(testee.dismissUnplayed())
        assertFalse(testee.dismissUnplayed())
    }

    @Test
    fun `a deferred intro start runs while nothing has overtaken it`() {
        testee.play()

        assertTrue(testee.canStart())
    }

    @Test
    fun `releasing mid-play stops a deferred intro start`() {
        testee.play()
        testee.release()

        assertFalse(testee.canStart())
    }

    @Test
    fun `a dialog taking over stops a deferred intro start`() {
        testee.handOverToDialog()

        assertFalse(testee.canStart())
    }

    @Test
    fun `dismissing unplayed stops a deferred intro start`() {
        testee.dismissUnplayed()

        assertFalse(testee.canStart())
    }
}
