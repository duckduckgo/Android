/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.appearance

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.appearance.AppearanceViewModel.Command
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.store.ThemingDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class AppearanceViewModelTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: AppearanceViewModel

    @Mock
    private lateinit var mockThemeSettingsDataStore: ThemingDataStore

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockAppTheme: AppTheme

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.SYSTEM_DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)

        testee = AppearanceViewModel(
            mockThemeSettingsDataStore,
            mockAppTheme,
            mockAppSettingsDataStore,
            mockPixel,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenInitialisedThenViewStateEmittedWithDefaultValues() = runTest {
        testee.viewState().test {
            val value = awaitItem()

            assertEquals(DuckDuckGoTheme.SYSTEM_DEFAULT, value.theme)
            assertEquals(AppIcon.DEFAULT, value.appIcon)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeSettingsClickedThenPixelSent() {
        testee.userRequestedToChangeTheme()
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_OPENED)
    }

    @Test
    fun whenThemeSettingsClickedThenCommandIsLaunchThemeSettingsIsSent() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeTheme()

            assertEquals(Command.LaunchThemeSettings(DuckDuckGoTheme.LIGHT), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIconAndPixelSent() = runTest {
        testee.commands().test {
            testee.userRequestedToChangeIcon()

            assertEquals(Command.LaunchAppIcon, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_APP_ICON_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeChangedThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.DARK)

            verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.DARK

            assertEquals(Command.UpdateTheme, awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenThemeChangedToLightThenLightThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.DARK)
        testee.onThemeSelected(DuckDuckGoTheme.LIGHT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenThemeChangedToDarkThenDarkThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.DARK)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_DARK)
    }

    @Test
    fun whenThemeChangedToSystemDefaultThenSystemDefaultThemePixelIsSent() {
        givenThemeSelected(DuckDuckGoTheme.LIGHT)
        testee.onThemeSelected(DuckDuckGoTheme.SYSTEM_DEFAULT)
        verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_SYSTEM_DEFAULT)
    }

    @Test
    fun whenThemeChangedButThemeWasAlreadySetThenDoNothing() = runTest {
        testee.commands().test {
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.onThemeSelected(DuckDuckGoTheme.LIGHT)

            verify(mockPixel, never()).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)
            verify(mockThemeSettingsDataStore, never()).theme = DuckDuckGoTheme.LIGHT

            expectNoEvents()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenForceDarkModeSettingEnabledChangeThenStoreUpdated() = runTest {
        testee.onForceDarkModeSettingChanged(true)
        verify(mockAppSettingsDataStore).experimentalWebsiteDarkMode = true
        verify(mockPixel).fire(AppPixelName.FORCE_DARK_MODE_ENABLED)
    }

    @Test
    fun whenForceDarkModeSettingDisabledChangeThenStoreUpdated() = runTest {
        testee.onForceDarkModeSettingChanged(false)
        verify(mockAppSettingsDataStore).experimentalWebsiteDarkMode = false
        verify(mockPixel).fire(AppPixelName.FORCE_DARK_MODE_DISABLED)
    }

    @Test
    fun whenInitialisedAndLightThemeThenViewStateEmittedWithProperValues() = runTest {
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.LIGHT)
        whenever(mockAppTheme.isLightModeEnabled()).thenReturn(true)

        testee.viewState().test {
            val value = expectMostRecentItem()

            assertEquals(DuckDuckGoTheme.LIGHT, value.theme)
            assertEquals(AppIcon.DEFAULT, value.appIcon)
            assertEquals(false, value.forceDarkModeEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    private fun givenThemeSelected(theme: DuckDuckGoTheme) {
        whenever(mockThemeSettingsDataStore.theme).thenReturn(theme)
        whenever(mockThemeSettingsDataStore.isCurrentlySelected(theme)).thenReturn(true)
    }
}
