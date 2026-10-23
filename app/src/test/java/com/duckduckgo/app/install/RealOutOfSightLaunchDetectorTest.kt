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

package com.duckduckgo.app.install

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealOutOfSightLaunchDetectorTest {

    private val detector = RealOutOfSightLaunchDetector()

    @Test
    fun whenLaunchedOnTheDefaultDisplayThenLaunchIsNotOutOfSight() {
        assertFalse(detector.onLaunchDisplay(display(id = Display.DEFAULT_DISPLAY, flags = 0)))
        assertFalse(detector.isOutOfSightLaunch)
    }

    @Test
    fun whenLaunchedOnAPrivateSecondaryDisplayThenLaunchIsOutOfSight() {
        assertTrue(detector.onLaunchDisplay(display(id = 7, flags = Display.FLAG_PRIVATE, name = "SpegVirtualDisplay")))
        assertTrue(detector.isOutOfSightLaunch)
    }

    @Test
    fun whenLaunchedOnAPublicSecondaryDisplayThenLaunchIsNotOutOfSight() {
        assertFalse(detector.onLaunchDisplay(display(id = 2, flags = Display.FLAG_PRESENTATION)))
        assertFalse(detector.isOutOfSightLaunch)
    }

    @Test
    fun whenDisplayIsUnknownThenLaunchIsNotOutOfSight() {
        assertFalse(detector.onLaunchDisplay(null))
        assertFalse(detector.isOutOfSightLaunch)
    }

    @Test
    fun whenAnOutOfSightLaunchWasSeenThenItIsRememberedForTheProcess() {
        detector.onLaunchDisplay(display(id = 7, flags = Display.FLAG_PRIVATE))
        detector.onLaunchDisplay(display(id = Display.DEFAULT_DISPLAY, flags = 0))

        assertTrue(detector.isOutOfSightLaunch)
    }

    private fun display(id: Int, flags: Int, name: String = "Display $id"): Display {
        val display: Display = mock()
        whenever(display.displayId).thenReturn(id)
        whenever(display.flags).thenReturn(flags)
        whenever(display.name).thenReturn(name)
        return display
    }
}
