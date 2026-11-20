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

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.async
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RealVisibleLaunchDetectorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val reinstallMarkerFeature = FakeFeatureToggleFactory.create(ReinstallMarkerFeature::class.java)
    private val detector = RealVisibleLaunchDetector(reinstallMarkerFeature)

    @Test
    @Config(sdk = [34])
    fun whenNoActivityWasResumedThenLaunchIsNotVisible() {
        assertFalse(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenActivityIsResumedOnTheDefaultDisplayThenLaunchIsVisible() {
        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenActivityIsResumedOnAPublicSecondaryDisplayThenLaunchIsVisible() {
        detector.onActivityResumed(activityOn(displayId = 2, flags = Display.FLAG_PRESENTATION))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenActivityIsResumedOnAPrivateSecondaryDisplayThenLaunchIsNotVisible() {
        detector.onActivityResumed(activityOn(displayId = 7, flags = Display.FLAG_PRIVATE))

        assertFalse(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenTheDefaultDisplayIsPrivateThenLaunchIsVisible() {
        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY, flags = Display.FLAG_PRIVATE))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenTheDisplayIsUnknownThenLaunchIsVisible() {
        detector.onActivityResumed(activityWithoutDisplay())

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenReadingTheDisplayFailsThenLaunchIsVisible() {
        detector.onActivityResumed(activityFailingToReadDisplay())

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeAndActivityIsResumedOnTheDefaultDisplayThenLaunchIsVisible() {
        givenStrictMode()

        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeAndActivityIsResumedOnAPublicSecondaryDisplayThenLaunchIsVisible() {
        givenStrictMode()

        detector.onActivityResumed(activityOn(displayId = 2, flags = Display.FLAG_PRESENTATION))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeAndActivityIsResumedOnAPrivateSecondaryDisplayThenLaunchIsNotVisible() {
        givenStrictMode()

        detector.onActivityResumed(activityOn(displayId = 7, flags = Display.FLAG_PRIVATE))

        assertFalse(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeAndTheDisplayIsUnknownThenLaunchIsNotVisible() {
        givenStrictMode()

        detector.onActivityResumed(activityWithoutDisplay())

        assertFalse(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeAndReadingTheDisplayFailsThenLaunchIsNotVisible() {
        givenStrictMode()

        detector.onActivityResumed(activityFailingToReadDisplay())

        assertFalse(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenStrictModeIsTurnedOffAfterAnUnknownDisplayLaunchThenTheLaunchSeenEarlierCounts() {
        givenStrictMode()
        detector.onActivityResumed(activityWithoutDisplay())
        assertFalse(visibleLaunchSeen())

        reinstallMarkerFeature.strictMode().setRawStoredState(State(enable = false))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenTheCheckIsOffThenLaunchCountsAsVisibleWithoutAnyActivity() {
        reinstallMarkerFeature.self().setRawStoredState(State(enable = false))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenTheCheckIsOffThenStrictModeIsIgnored() {
        reinstallMarkerFeature.self().setRawStoredState(State(enable = false))
        givenStrictMode()

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [34])
    fun whenAVisibleActivityFollowsAHiddenOneThenWaitingCallersAreReleased() {
        val waiting = coroutineTestRule.testScope.async { detector.awaitVisibleLaunch() }

        detector.onActivityResumed(activityOn(displayId = 7, flags = Display.FLAG_PRIVATE))
        assertFalse(waiting.isCompleted)

        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY))
        assertTrue(waiting.isCompleted)
    }

    @Test
    @Config(sdk = [34])
    fun whenAHiddenActivityFollowsAVisibleOneThenLaunchStaysVisible() {
        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY))
        detector.onActivityResumed(activityOn(displayId = 7, flags = Display.FLAG_PRIVATE))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndActivityIsResumedOnTheDefaultDisplayThenLaunchIsVisible() {
        detector.onActivityResumed(activityOn(displayId = Display.DEFAULT_DISPLAY))

        assertTrue(visibleLaunchSeen())
    }

    @Test
    @Config(sdk = [29])
    fun whenBelowAndroid11AndActivityIsResumedOnAPrivateSecondaryDisplayThenLaunchIsNotVisible() {
        detector.onActivityResumed(activityOn(displayId = 7, flags = Display.FLAG_PRIVATE))

        assertFalse(visibleLaunchSeen())
    }

    private fun givenStrictMode() {
        reinstallMarkerFeature.strictMode().setRawStoredState(State(enable = true))
    }

    private fun activityWithoutDisplay(): Activity {
        val activity: Activity = mock()
        whenever(activity.display).thenReturn(null)
        return activity
    }

    private fun activityFailingToReadDisplay(): Activity {
        val activity: Activity = mock()
        whenever(activity.display).thenThrow(UnsupportedOperationException("no display"))
        return activity
    }

    private fun visibleLaunchSeen(): Boolean = coroutineTestRule.testScope.async { detector.awaitVisibleLaunch() }.isCompleted
}

/**
 * An activity whose display is read the way the running Android version allows: Activity.getDisplay() only exists from Android 11.
 */
internal fun activityOn(displayId: Int, flags: Int = 0): Activity {
    val display: Display = mock()
    whenever(display.displayId).thenReturn(displayId)
    whenever(display.flags).thenReturn(flags)
    val activity: Activity = mock()
    if (Build.VERSION.SDK_INT >= 30) {
        whenever(activity.display).thenReturn(display)
    } else {
        val windowManager: WindowManager = mock()
        @Suppress("DEPRECATION")
        whenever(windowManager.defaultDisplay).thenReturn(display)
        whenever(activity.windowManager).thenReturn(windowManager)
    }
    return activity
}
