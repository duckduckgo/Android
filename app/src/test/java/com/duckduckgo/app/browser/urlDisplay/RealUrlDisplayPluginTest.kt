/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.urlDisplay

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealUrlDisplayPluginTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val settingsDataStore = mock<SettingsDataStore>()
    private val browserConfigFeature = mock<AndroidBrowserConfigFeature>()
    private val shorterUrlToggle = mock<Toggle>()
    private val context = mock<Context>()
    private val packageManager = mock<PackageManager>()
    private lateinit var testee: PrivacyConfigCallbackPlugin

    @Before
    fun setup() {
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(browserConfigFeature.shorterUrlDefault()).thenReturn(shorterUrlToggle)

        testee = RealUrlDisplayPlugin(
            settingsDataStore = settingsDataStore,
            browserConfigFeature = browserConfigFeature,
            context = context,
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenUserHasExplicitPreferenceSet_thenNothingChange() = runTest {
        // Given: User has explicitly set preference to true
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(true)

        // When
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        // Then: Settings data store is never called
        verify(settingsDataStore, atLeastOnce()).hasUrlPreferenceSet()
        verify(settingsDataStore, never()).isFullUrlEnabled
    }

    @Test
    fun whenExistingUserAlreadyInstalledApp_thenSetFullUrlToTrue() = runTest {
        // Given: User already migrated (flag is set)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        val packageInfo = PackageInfo().apply {
            firstInstallTime = 1000L
            lastUpdateTime = 2000L
        }
        whenever(packageManager.getPackageInfo(context.packageName, 0)).thenReturn(packageInfo)

        // When
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        // Then: isFullUrlEnabled is changed to true
        verify(settingsDataStore, atLeastOnce()).hasUrlPreferenceSet()
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled = true
    }

    @Test
    fun whenNewUserAndRemoteConfigDisabled_thenSetFullUrlToTrue() = runTest {
        // Given: New user (no preference, fresh install)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        val packageInfo = PackageInfo().apply {
            firstInstallTime = 1000L
            lastUpdateTime = 1000L
        }
        whenever(packageManager.getPackageInfo(context.packageName, 0)).thenReturn(packageInfo)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(false)

        // When
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        // Then
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled = true
    }

    @Test
    fun whenNewUserAndRemoteConfigEnabled_thenSetFullUrlToFalse() = runTest {
        // Given: New user (no preference, fresh install)
        whenever(settingsDataStore.hasUrlPreferenceSet()).thenReturn(false)
        val packageInfo = PackageInfo().apply {
            firstInstallTime = 1000L
            lastUpdateTime = 1000L
        }
        whenever(packageManager.getPackageInfo(context.packageName, 0)).thenReturn(packageInfo)
        whenever(shorterUrlToggle.isEnabled()).thenReturn(true)

        // When
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        // Then
        verify(settingsDataStore, atLeastOnce()).isFullUrlEnabled = false
    }
}
