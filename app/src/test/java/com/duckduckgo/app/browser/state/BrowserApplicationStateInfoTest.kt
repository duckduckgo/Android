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

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserApplicationStateInfoTest {

    private lateinit var browserApplicationStateInfo: BrowserApplicationStateInfo
    private val observer: BrowserLifecycleObserver = mock()
    private val activity: Activity = mock()

    @Before
    fun setup() {
        browserApplicationStateInfo = BrowserApplicationStateInfo(setOf(observer))
    }

    @Test
    fun whenActivityCreatedThenNoop() {
        browserApplicationStateInfo.onActivityCreated(activity, null)

        verifyZeroInteractions(observer)
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
}
