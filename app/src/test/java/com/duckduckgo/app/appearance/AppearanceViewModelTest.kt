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

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.appearance.AppearanceViewModel.Command
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.store.ThemingDataStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
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

    @Mock
    private lateinit var mockTabSwitcherDataStore: TabSwitcherDataStore

    @SuppressLint("DenyListedApi")
    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.SYSTEM_DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockAppSettingsDataStore.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)
        whenever(mockTabSwitcherDataStore.isTrackersAnimationInfoTileHidden()).thenReturn(flowOf(false))

        testee =
            AppearanceViewModel(
                mockThemeSettingsDataStore,
                mockAppSettingsDataStore,
                mockPixel,
                coroutineTestRule.testDispatcherProvider,
                mockTabSwitcherDataStore,
            )
    }

    @Test
    fun whenInitialisedThenViewStateEmittedWithDefaultValues() =
        runTest {
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
    fun whenThemeSettingsClickedThenCommandIsLaunchThemeSettingsIsSent() =
        runTest {
            testee.commands().test {
                testee.userRequestedToChangeTheme()

                assertEquals(Command.LaunchThemeSettings(DuckDuckGoTheme.LIGHT), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIconAndPixelSent() =
        runTest {
            testee.commands().test {
                testee.userRequestedToChangeIcon()

                assertEquals(Command.LaunchAppIcon, awaitItem())
                verify(mockPixel).fire(AppPixelName.SETTINGS_APP_ICON_PRESSED)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenThemeChangedThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() =
        runTest {
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
    fun whenThemeChangedButThemeWasAlreadySetThenDoNothing() =
        runTest {
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
    fun whenForceDarkModeSettingEnabledChangeThenStoreUpdated() =
        runTest {
            testee.onForceDarkModeSettingChanged(true)
            verify(mockAppSettingsDataStore).experimentalWebsiteDarkMode = true
            verify(mockPixel).fire(AppPixelName.FORCE_DARK_MODE_ENABLED)
        }

    @Test
    fun whenForceDarkModeSettingDisabledChangeThenStoreUpdated() =
        runTest {
            testee.onForceDarkModeSettingChanged(false)
            verify(mockAppSettingsDataStore).experimentalWebsiteDarkMode = false
            verify(mockPixel).fire(AppPixelName.FORCE_DARK_MODE_DISABLED)
        }

    @Test
    fun whenOmnibarPositionSettingPressed() =
        runTest {
            testee.commands().test {
                testee.userRequestedToChangeAddressBarPosition()
                assertEquals(Command.LaunchOmnibarTypeSettings(OmnibarType.SINGLE_TOP), awaitItem())
                verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_PRESSED)
            }
        }

    @Test
    fun whenOmnibarPositionUpdatedToBottom() =
        runTest {
            testee.setOmnibarType(OmnibarType.SINGLE_BOTTOM)
            verify(mockAppSettingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
            verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_BOTTOM)
        }

    @Test
    fun whenOmnibarPositionUpdatedToTop() =
        runTest {
            testee.setOmnibarType(OmnibarType.SINGLE_TOP)
            verify(mockAppSettingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
            verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_TOP)
        }

    @Test
    fun whenFullSiteAddressEnabled() =
        runTest {
            val enabled = true
            testee.onFullUrlSettingChanged(enabled)
            verify(mockAppSettingsDataStore).isFullUrlEnabled = enabled
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_FULL_URL_OPTION_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun whenFullSiteAddressDisabled() =
        runTest {
            val enabled = false
            testee.onFullUrlSettingChanged(enabled)
            verify(mockAppSettingsDataStore).isFullUrlEnabled = enabled
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_FULL_URL_OPTION_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun `when tracker count in tab switcher is enabled then setting enabled`() =
        runTest {
            val enabled = true
            testee.onShowTrackersCountInTabSwitcherChanged(enabled)
            verify(mockTabSwitcherDataStore).setTrackersAnimationInfoTileHidden(!enabled)
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_TRACKER_COUNT_IN_TAB_SWITCHER_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun `when tracker count in tab switcher is disabled then setting disabled`() =
        runTest {
            val enabled = false
            testee.onShowTrackersCountInTabSwitcherChanged(enabled)
            verify(mockTabSwitcherDataStore).setTrackersAnimationInfoTileHidden(!enabled)
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_TRACKER_COUNT_IN_TAB_SWITCHER_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun whenInitialisedAndLightThemeThenViewStateEmittedWithProperValues() =
        runTest {
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
