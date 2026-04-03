/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.notification.NotificationSender
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBrowserObserverTest {

    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val pixel: Pixel = mock()
    private val surveyManager: DefaultBrowserChangedSurveyManager = mock()
    private val notificationSender: NotificationSender = mock()
    private val surveyNotification: DefaultBrowserChangedSurveyNotification = mock()
    private val dispatcherProvider: DispatcherProvider = mock()
    private val workManager: WorkManager = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val lifecycleOwner: LifecycleOwner = mock()
    private lateinit var observer: DefaultBrowserObserver

    @Before
    fun setup() {
        whenever(dispatcherProvider.io()).thenReturn(testDispatcher)
        observer = DefaultBrowserObserver(
            defaultBrowserDetector = defaultBrowserDetector,
            appInstallStore = appInstallStore,
            pixel = pixel,
            defaultBrowserChangedSurveyManager = surveyManager,
            notificationSender = notificationSender,
            defaultBrowserChangedSurveyNotification = surveyNotification,
            appCoroutineScope = testScope,
            dispatcherProvider = dispatcherProvider,
            workManager = workManager,
        )
    }

    @Test
    fun whenNoLongerDefaultBrowserThenMarkSurveyPending() {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(appInstallStore.defaultBrowser).thenReturn(true)
        observer.onResume(lifecycleOwner)
        verify(surveyManager).markSurveyPending()
    }

    @Test
    fun whenBecomeDefaultBrowserThenDoNotMarkSurveyPending() {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(false)
        observer.onResume(lifecycleOwner)
        verify(surveyManager, never()).markSurveyPending()
    }

    @Test
    fun whenNoChangeInDefaultStatusThenDoNotMarkSurveyPending() {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(appInstallStore.defaultBrowser).thenReturn(true)
        observer.onResume(lifecycleOwner)
        verify(surveyManager, never()).markSurveyPending()
    }

    @Test
    fun whenNoLongerDefaultAndShouldTriggerSurveyThenSendNotification() = runTest {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(appInstallStore.defaultBrowser).thenReturn(true)
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(true)
        observer.onResume(lifecycleOwner)
        verify(notificationSender).sendNotification(surveyNotification)
    }

    @Test
    fun whenNoLongerDefaultButSurveySuppressedThenDoNotSendNotification() = runTest {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(appInstallStore.defaultBrowser).thenReturn(true)
        whenever(surveyManager.shouldTriggerSurvey()).thenReturn(false)
        observer.onResume(lifecycleOwner)
        verify(notificationSender, never()).sendNotification(surveyNotification)
    }
}
