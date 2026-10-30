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

package com.duckduckgo.app.devicemigration

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserChangedSurveyFeature
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserObserver
import com.duckduckgo.app.browser.defaultbrowsing.RealDefaultBrowserChangedSurveyManager
import com.duckduckgo.app.global.install.AppInstallSharedPreferences
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.devicemigration.api.DeviceMigration
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MigratedInstallDefaultBrowserTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val deviceMigration: DeviceMigration = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val pixel: Pixel = mock()
    private val surveyToggle: Toggle = mock { on { isEnabled() } doReturn true }
    private val surveyFeature: DefaultBrowserChangedSurveyFeature = mock { on { self() } doReturn surveyToggle }
    private val appBuildConfig: AppBuildConfig = mock { on { deviceLocale } doReturn Locale.US }

    private val preferenceFiles = mutableMapOf<String, InMemorySharedPreferences>()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doAnswer { invocation ->
            preferenceFiles.getOrPut(invocation.getArgument(0)) { InMemorySharedPreferences() }
        }
    }

    private lateinit var appInstallStore: AppInstallSharedPreferences

    @Before
    fun setup() {
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        // The previous phone's history, written by an instance that does not see a migrated install.
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(null)
        newStore().defaultBrowser = true
        whenever(deviceMigration.sourceDeviceIsNewInstall()).thenReturn(false)
        appInstallStore = newStore()
    }

    private fun newStore() = AppInstallSharedPreferences(
        context = context,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        sharedPreferencesProvider = sharedPreferencesProvider,
        deviceMigration = lazyOf(deviceMigration),
        defaultBrowserDetector = lazyOf(defaultBrowserDetector),
    )

    @Test
    fun whenMigratedInstallFirstResumesThenDefaultBrowserUnsetPixelNotFired() {
        DefaultBrowserObserver(defaultBrowserDetector, appInstallStore, pixel).onResume(mock())

        verify(pixel, never()).fire(eq(AppPixelName.DEFAULT_BROWSER_UNSET), any(), any(), any())
    }

    @Test
    fun whenMigratedInstallThenDefaultBrowserChangedSurveyNotTriggeredByThePhoneSwitch() {
        val surveyManager = RealDefaultBrowserChangedSurveyManager(
            appInstallStore,
            surveyFeature,
            appBuildConfig,
            defaultBrowserDetector,
            NotificationManagerCompat.from(context),
        )

        assertFalse(surveyManager.shouldTriggerSurvey())
    }
}
