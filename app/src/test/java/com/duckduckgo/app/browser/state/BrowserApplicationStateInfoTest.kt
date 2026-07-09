/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.state

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class BrowserApplicationStateInfoTest {

    private lateinit var browserApplicationStateInfo: BrowserApplicationStateInfo
    private val observer: BrowserLifecycleObserver = mock()
    private val activity = FakeBrowserActivity()
    private val recreateAwareToggle: Toggle = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()

    class FakeBrowserActivity : BrowserActivity() {
        var isConfigChange = false

        override fun isChangingConfigurations(): Boolean {
            return isConfigChange
        }
    }

    @Before
    fun setup() {
        activity.destroyedByBackPress = false
        whenever(recreateAwareToggle.isEnabled()).thenReturn(true)
        whenever(androidBrowserConfigFeature.recreateAwareLifecycle()).thenReturn(recreateAwareToggle)
        browserApplicationStateInfo = BrowserApplicationStateInfo(setOf(observer), androidBrowserConfigFeature)
    }

    @Test
    fun whenActivityCreatedThenNoop() {
        browserApplicationStateInfo.onActivityCreated(activity, null)

        verifyNoInteractions(observer)
    }

    @Test
    fun whenFirstActivityCreatedAndStartedThenNotifyFreshAppLaunch() {
        browserApplicationStateInfo.onActivityCreated(activity, null)

        browserApplicationStateInfo.onActivityStarted(activity)

        verify(observer).onOpen(true)
    }

    @Test
    fun whenAllActivitiesStopAndRestartThenNotifyAppOpen() {
        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityCreated(activity, null)

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)

        verify(observer).onOpen(true)

        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()
        verify(observer, never()).onExit()

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(false)
    }

    @Test
    fun whenAllActivitiesAreDestroyedAndRecreatedThenNotifyFreshAppLaunch() {
        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityCreated(activity, null)

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)

        verify(observer).onOpen(true)

        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()

        browserApplicationStateInfo.onActivityDestroyed(activity)
        browserApplicationStateInfo.onActivityDestroyed(activity)
        verify(observer).onClose()
        verify(observer).onExit()

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(true)
    }

    @Test
    fun whenAllActivitiesAreDestroyedByBackPressAndRecreatedThenDoNotNotifyFreshAppLaunch() {
        activity.destroyedByBackPress = true

        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityCreated(activity, null)

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)

        verify(observer).onOpen(true)

        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()

        browserApplicationStateInfo.onActivityDestroyed(activity)
        browserApplicationStateInfo.onActivityDestroyed(activity)
        verify(observer).onClose()
        verify(observer, never()).onExit()

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(false)
    }

    @Test
    fun whenActivitiesStopAndRestartForConfigChangeThenDoNotNotifyCloseOrOpen() {
        activity.isConfigChange = true

        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityCreated(activity, null)

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)

        verify(observer).onOpen(true)

        // A config-change recreate stops then restarts the activities, but it is not a real
        // background->foreground, so neither onClose() nor onOpen() should fire again.
        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer, never()).onClose()

        browserApplicationStateInfo.onActivityDestroyed(activity)
        browserApplicationStateInfo.onActivityDestroyed(activity)
        verify(observer, never()).onExit()

        browserApplicationStateInfo.onActivityStarted(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        // only the initial onOpen(true); the recreate restart must not fire a second onOpen
        verify(observer, times(1)).onOpen(any())
    }

    @Test
    fun whenConfigChangeRecreateThenSubsequentRealBackgroundThenNotifyClose() {
        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(true)

        // Config-change recreate: stop + restart must not fire onClose()/onOpen().
        activity.isConfigChange = true
        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer, never()).onClose()
        verify(observer, times(1)).onOpen(any())

        // The recreate flag must be consumed, not stuck, so a genuine background still fires onClose().
        activity.isConfigChange = false
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()
    }

    @Test
    fun whenConfigChangeRecreateThenRealBackgroundAndForegroundThenNotifyOpen() {
        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(true)

        // Config-change recreate (no onClose()/onOpen()), then a genuine background.
        activity.isConfigChange = true
        browserApplicationStateInfo.onActivityStopped(activity)
        browserApplicationStateInfo.onActivityStarted(activity)
        activity.isConfigChange = false
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()

        // A genuine foreground afterwards must fire onOpen() again (recreate flag not stuck).
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(false)
    }

    @Test
    fun whenConfigChangeButRecreateAwareLifecycleDisabledThenNotifyCloseAndOpen() {
        // Kill switch off => behave exactly as before the fix: a recreate fires onClose()/onOpen().
        whenever(recreateAwareToggle.isEnabled()).thenReturn(false)

        browserApplicationStateInfo.onActivityCreated(activity, null)
        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(true)

        activity.isConfigChange = true
        browserApplicationStateInfo.onActivityStopped(activity)
        verify(observer).onClose()

        browserApplicationStateInfo.onActivityStarted(activity)
        verify(observer).onOpen(false)
    }
}
