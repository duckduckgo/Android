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

package com.duckduckgo.app.notification

import androidx.core.app.NotificationManagerCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.notification.model.NotificationPlugin
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentManager
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.experiments.api.VariantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NotificationRegistrarTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private val mockPixel: Pixel = mock()
    private val mockSchedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin> = mock()
    private val mockNotificationPluginPoint: PluginPoint<NotificationPlugin> = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val mockNotificationPromptExperimentManager: NotificationPromptExperimentManager = mock()

    private lateinit var testee: NotificationRegistrar

    @Before
    fun before() {
        whenever(mockVariantManager.getVariantKey()).thenReturn("DEFAULT_VARIANT")
        whenever(appBuildConfig.sdkInt).thenReturn(30)
        testee = NotificationRegistrar(
            TestScope(),
            context,
            notificationManagerCompat,
            mockSettingsDataStore,
            mockPixel,
            mockSchedulableNotificationPluginPoint,
            mockNotificationPluginPoint,
            appBuildConfig,
            coroutineRule.testDispatcherProvider,
            mockNotificationPromptExperimentManager,
        )
    }

    @Test
    fun whenNotificationsPreviouslyOffAndNowOnThenPixelIsFiredAndSettingsUpdated() = runTest {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateStatus(true)
        advanceUntilIdle()
        verify(mockPixel).fire(eq(AppPixelName.NOTIFICATIONS_ENABLED), any(), any(), eq(Count))
        verify(mockSettingsDataStore).appNotificationsEnabled = true
        verify(mockNotificationPromptExperimentManager).fireNotificationsEnabledLater()
    }

    @Test
    fun whenNotificationsPreviouslyOffAndStillOffThenNoPixelIsFiredAndSettingsUnchanged() = runTest {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateStatus(false)
        advanceUntilIdle()
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any(), any(), eq(Count))
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = true
        verify(mockNotificationPromptExperimentManager, never()).fireNotificationsEnabledLater()
    }

    @Test
    fun whenNotificationsPreviouslyOnAndStillOnThenNoPixelIsFiredAndSettingsUnchanged() = runTest {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateStatus(true)
        advanceUntilIdle()
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any(), any(), eq(Count))
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = false
        verify(mockNotificationPromptExperimentManager, never()).fireNotificationsEnabledLater()
    }

    @Test
    fun whenNotificationsPreviouslyOnAndNowOffPixelIsFiredAndSettingsUpdated() = runTest {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateStatus(false)
        advanceUntilIdle()
        verify(mockPixel).fire(eq(AppPixelName.NOTIFICATIONS_DISABLED), any(), any(), eq(Count))
        verify(mockSettingsDataStore).appNotificationsEnabled = false
        verify(mockNotificationPromptExperimentManager, never()).fireNotificationsEnabledLater()
    }
}
