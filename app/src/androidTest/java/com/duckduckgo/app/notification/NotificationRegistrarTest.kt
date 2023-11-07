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
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.notification.model.NotificationPlugin
import com.duckduckgo.app.notification.model.SchedulableNotificationPlugin
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.experiments.api.VariantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NotificationRegistrarTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockVariantManager: VariantManager = mock()
    private val mockPixel: Pixel = mock()
    private val mockSchedulableNotificationPluginPoint: PluginPoint<SchedulableNotificationPlugin> = mock()
    private val mockNotificationPluginPoint: PluginPoint<NotificationPlugin> = mock()
    private val appBuildConfig: AppBuildConfig = mock()

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
        )
    }

    @Test
    fun whenNotificationsPreviouslyOffAndNowOnThenPixelIsFiredAndSettingsUpdated() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateStatus(true)
        verify(mockPixel).fire(eq(AppPixelName.NOTIFICATIONS_ENABLED), any(), any())
        verify(mockSettingsDataStore).appNotificationsEnabled = true
    }

    @Test
    fun whenNotificationsPreviouslyOffAndStillOffThenNoPixelIsFiredAndSettingsUnchanged() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(false)
        testee.updateStatus(false)
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any(), any())
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = true
    }

    @Test
    fun whenNotificationsPreviouslyOnAndStillOnThenNoPixelIsFiredAndSettingsUnchanged() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateStatus(true)
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any(), any())
        verify(mockSettingsDataStore, never()).appNotificationsEnabled = false
    }

    @Test
    fun whenNotificationsPreviouslyOnAndNowOffPixelIsFiredAndSettingsUpdated() {
        whenever(mockSettingsDataStore.appNotificationsEnabled).thenReturn(true)
        testee.updateStatus(false)
        verify(mockPixel).fire(eq(AppPixelName.NOTIFICATIONS_DISABLED), any(), any())
        verify(mockSettingsDataStore).appNotificationsEnabled = false
    }
}
