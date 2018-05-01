/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultBrowsing

import com.duckduckgo.app.global.install.AppInstallStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DefaultBrowserTimeBasedNotificationTest {

    private lateinit var testee: DefaultBrowserTimeBasedNotification

    private val mockDetector: DefaultBrowserDetector = mock()
    private val appInstallStore: AppInstallStore = mock()

    @Before
    fun setup() {
        testee = DefaultBrowserTimeBasedNotification(mockDetector, appInstallStore)
    }

    @Test
    fun whenDeviceNotSupportingDefaultBrowserThenNotificationNotShown() {
        configureEnvironment(false, true, false)
        assertFalse(testee.shouldShowNotification(browserShowing = true))
    }

    @Test
    fun whenNoAppInstallTimeRecordedThenNotificationNotShown() {
        configureEnvironment(true, false, false)
        assertFalse(testee.shouldShowNotification(browserShowing = true))
    }

    @Test
    fun whenUserDeclinedPreviouslyThenNotificationNotShown() {
        configureEnvironment(true, true, true)
        assertFalse(testee.shouldShowNotification(browserShowing = true))
    }

    @Test
    fun whenNotEnoughTimeHasPassedSinceInstallThenNotificationNotShown() {
        configureEnvironment(true, true, false)
        whenever(appInstallStore.installTimestamp).thenReturn(0)
        assertFalse(testee.shouldShowNotification(browserShowing = true, timeNow = TimeUnit.SECONDS.toMillis(10)))
    }

    @Test
    fun whenEnoughTimeHasPassedSinceInstallThenNotificationShown() {
        configureEnvironment(true, true, false)
        whenever(appInstallStore.installTimestamp).thenReturn(0)
        assertTrue(testee.shouldShowNotification(browserShowing = true, timeNow = TimeUnit.DAYS.toMillis(100)))
    }

    private fun configureEnvironment(supported: Boolean, timestampRecorded: Boolean, previousDecline: Boolean) {
        whenever(mockDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(supported)
        whenever(appInstallStore.hasInstallTimestampRecorded()).thenReturn(timestampRecorded)
        whenever(appInstallStore.hasUserDeclinedDefaultBrowserPreviously()).thenReturn(previousDecline)
    }
}