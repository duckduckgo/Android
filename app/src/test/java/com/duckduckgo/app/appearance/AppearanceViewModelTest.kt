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
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.menu.BrowserMenuDisplayRepository
import com.duckduckgo.app.browser.menu.BrowserMenuDisplayState
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.urldisplay.UrlDisplayRepository
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.store.TabSwitcherDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.store.ThemingDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private lateinit var mockUrlDisplayRepository: UrlDisplayRepository

    @Mock
    private lateinit var mockBrowserMenuDisplayRepository: BrowserMenuDisplayRepository

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockAppTheme: AppTheme

    @Mock
    private lateinit var mockTabSwitcherDataStore: TabSwitcherDataStore

    @Mock
    private lateinit var mockOmnibarFeatureRepository: OmnibarRepository

    @Mock
    private lateinit var mockAddressBarTrackersAnimationManager: com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationManager

    @SuppressLint("DenyListedApi")
    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.SYSTEM_DEFAULT)
        whenever(mockAppSettingsDataStore.selectedFireAnimation).thenReturn(FireAnimation.HeroFire)
        whenever(mockAppSettingsDataStore.omnibarType).thenReturn(OmnibarType.SINGLE_TOP)
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(flowOf(true))
        whenever(mockBrowserMenuDisplayRepository.browserMenuState)
            .thenReturn(flowOf(BrowserMenuDisplayState(hasOption = false, isEnabled = false)))
        whenever(mockTabSwitcherDataStore.isTrackersAnimationInfoTileHidden()).thenReturn(flowOf(false))
        whenever(mockOmnibarFeatureRepository.isSplitOmnibarAvailable).thenReturn(false)
        runTest {
            whenever(mockAddressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)
        }

        initializeViewModel()
    }

    private fun initializeViewModel() {
        testee =
            AppearanceViewModel(
                mockThemeSettingsDataStore,
                mockAppSettingsDataStore,
                mockUrlDisplayRepository,
                mockBrowserMenuDisplayRepository,
                mockPixel,
                coroutineTestRule.testDispatcherProvider,
                mockTabSwitcherDataStore,
                mockAddressBarTrackersAnimationManager,
                mockOmnibarFeatureRepository,
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

                assertEquals(Command.LaunchThemeSettings(DuckDuckGoTheme.SYSTEM_DEFAULT), awaitItem())

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
            givenThemeSelected(DuckDuckGoTheme.LIGHT)
            testee.commands().test {
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
            testee.onOmnibarTypeSelected(OmnibarType.SINGLE_BOTTOM)
            verify(mockAppSettingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
            verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_BOTTOM)
        }

    @Test
    fun whenOmnibarPositionUpdatedToTop() =
        runTest {
            testee.onOmnibarTypeSelected(OmnibarType.SINGLE_TOP)
            verify(mockAppSettingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
            verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_TOP)
        }

    @Test
    fun whenOmnibarPositionUpdatedToSplit() =
        runTest {
            testee.onOmnibarTypeSelected(OmnibarType.SPLIT)
            verify(mockAppSettingsDataStore).omnibarType = OmnibarType.SPLIT
            verify(mockPixel).fire(AppPixelName.SETTINGS_ADDRESS_BAR_POSITION_SELECTED_SPLIT_TOP)
        }

    @Test
    fun whenFullSiteAddressEnabled() =
        runTest {
            val enabled = true
            testee.onFullUrlSettingChanged(enabled)
            verify(mockUrlDisplayRepository).setFullUrlEnabled(enabled)
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
            verify(mockUrlDisplayRepository).setFullUrlEnabled(enabled)
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
    fun `when tracker count in address bar is enabled then setting enabled`() =
        runTest {
            val enabled = true
            testee.onShowTrackersCountInAddressBarChanged(enabled)
            verify(mockAppSettingsDataStore).showTrackersCountInAddressBar = enabled
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_TRACKER_COUNT_IN_ADDRESS_BAR_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun `when tracker count in address bar is disabled then setting disabled`() =
        runTest {
            val enabled = false
            testee.onShowTrackersCountInAddressBarChanged(enabled)
            verify(mockAppSettingsDataStore).showTrackersCountInAddressBar = enabled
            val params = mapOf(Pixel.PixelParameter.IS_ENABLED to enabled.toString())
            verify(mockPixel).fire(
                AppPixelName.SETTINGS_APPEARANCE_IS_TRACKER_COUNT_IN_ADDRESS_BAR_TOGGLED,
                params,
                emptyMap(),
                Pixel.PixelType.Count,
            )
        }

    @Test
    fun `when address bar trackers animation feature is disabled then toggle should be hidden`() =
        runTest {
            whenever(mockAddressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(false)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(false, value.shouldShowAddressBarTrackersAnimationItem)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when address bar trackers animation feature is enabled then toggle should be visible`() =
        runTest {
            whenever(mockAddressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(true)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(true, value.shouldShowAddressBarTrackersAnimationItem)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when tracker count in address bar setting is stored then it persists correctly`() =
        runTest {
            whenever(mockAppSettingsDataStore.showTrackersCountInAddressBar).thenReturn(false)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(false, value.isAddressBarTrackersAnimationEnabled)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when tracker count in address bar is enabled by default then viewState reflects it`() =
        runTest {
            whenever(mockAppSettingsDataStore.showTrackersCountInAddressBar).thenReturn(true)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(true, value.isAddressBarTrackersAnimationEnabled)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when both feature flag and user preference are enabled then viewState shows both enabled`() =
        runTest {
            whenever(mockAddressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(true)
            whenever(mockAppSettingsDataStore.showTrackersCountInAddressBar).thenReturn(true)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(true, value.shouldShowAddressBarTrackersAnimationItem)
                assertEquals(true, value.isAddressBarTrackersAnimationEnabled)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when feature flag is enabled but user preference is disabled then toggle is visible but unchecked`() =
        runTest {
            whenever(mockAddressBarTrackersAnimationManager.isFeatureEnabled()).thenReturn(true)
            whenever(mockAppSettingsDataStore.showTrackersCountInAddressBar).thenReturn(false)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()
                assertEquals(true, value.shouldShowAddressBarTrackersAnimationItem) // Toggle visible
                assertEquals(false, value.isAddressBarTrackersAnimationEnabled) // But unchecked
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenInitialisedAndLightThemeThenViewStateEmittedWithProperValues() =
        runTest {
            whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.LIGHT)
            whenever(mockAppTheme.isLightModeEnabled()).thenReturn(true)

            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()

                assertEquals(DuckDuckGoTheme.LIGHT, value.theme)
                assertEquals(AppIcon.DEFAULT, value.appIcon)
                assertEquals(false, value.forceDarkModeEnabled)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenSplitOmnibarAvailableThenViewStateShowsSetting() =
        runTest {
            whenever(mockOmnibarFeatureRepository.isSplitOmnibarAvailable).thenReturn(true)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()

                assertEquals(true, value.shouldShowSplitOmnibarSettings)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenSplitOmnibarNotAvailableThenViewStateHidesSetting() =
        runTest {
            whenever(mockOmnibarFeatureRepository.isSplitOmnibarAvailable).thenReturn(false)
            initializeViewModel()

            testee.viewState().test {
                val value = expectMostRecentItem()

                assertEquals(false, value.shouldShowSplitOmnibarSettings)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `when url display repository emits true then view state reflects full url enabled`() = runTest {
        // Given: Repository flow emits true
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(flowOf(true))
        initializeViewModel()

        // When: Collect view state
        testee.viewState().test {
            val viewState = awaitItem()

            // Then: View state shows full URL enabled
            assertEquals(true, viewState.isFullUrlEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when url display repository emits false then view state reflects full url disabled`() = runTest {
        // Given: Repository flow emits false
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(flowOf(false))
        initializeViewModel()

        // When: Collect view state
        testee.viewState().test {
            val viewState = awaitItem()

            // Then: View state shows full URL disabled
            assertEquals(false, viewState.isFullUrlEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when url display repository flow emits new value then view state updates`() = runTest {
        // Given: Repository flow that emits multiple values
        val urlDisplayFlow = MutableStateFlow(true)
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(urlDisplayFlow)
        initializeViewModel()

        // When: Collect view state and change repository value
        testee.viewState().test {
            // First emission
            assertEquals(true, awaitItem().isFullUrlEnabled)

            // Change the flow value
            urlDisplayFlow.value = false

            // Then: View state updates with new value
            assertEquals(false, awaitItem().isFullUrlEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when url display changes multiple times then view state reflects each change`() = runTest {
        // Given: Repository flow that can emit multiple values
        val urlDisplayFlow = MutableStateFlow(false)
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(urlDisplayFlow)
        initializeViewModel()

        // When: Collect view state and toggle multiple times
        testee.viewState().test {
            assertEquals(false, awaitItem().isFullUrlEnabled)

            urlDisplayFlow.value = true
            assertEquals(true, awaitItem().isFullUrlEnabled)

            urlDisplayFlow.value = false
            assertEquals(false, awaitItem().isFullUrlEnabled)

            urlDisplayFlow.value = true
            assertEquals(true, awaitItem().isFullUrlEnabled)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `when url display repository flow updates then other view state properties remain unchanged`() = runTest {
        // Given: Repository flow changes
        val urlDisplayFlow = MutableStateFlow(true)
        whenever(mockUrlDisplayRepository.isFullUrlEnabled).thenReturn(urlDisplayFlow)
        whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.DARK)
        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.BLUE)
        initializeViewModel()

        // When: URL display changes
        testee.viewState().test {
            val initialState = awaitItem()
            assertEquals(true, initialState.isFullUrlEnabled)
            assertEquals(DuckDuckGoTheme.DARK, initialState.theme)
            assertEquals(AppIcon.BLUE, initialState.appIcon)

            urlDisplayFlow.value = false

            // Then: Only isFullUrlEnabled changes, other properties remain
            val updatedState = awaitItem()
            assertEquals(false, updatedState.isFullUrlEnabled)
            assertEquals(DuckDuckGoTheme.DARK, updatedState.theme)
            assertEquals(AppIcon.BLUE, updatedState.appIcon)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenSystemDefaultThemeIsLightAndUserSelectsLightThenUpdatePreference() =
        runTest {
            // Given: System default is set, but system is in light mode, so the effective theme is LIGHT
            // However, the stored preference is still SYSTEM_DEFAULT
            whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.LIGHT)
            whenever(mockThemeSettingsDataStore.isCurrentlySelected(DuckDuckGoTheme.LIGHT)).thenReturn(false)
            initializeViewModel()

            testee.commands().test {
                // When: User explicitly selects LIGHT theme (wants to lock it to light, not follow system)
                testee.onThemeSelected(DuckDuckGoTheme.LIGHT)

                // Then: Preference is updated to LIGHT and pixel is fired
                verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.LIGHT
                verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_LIGHT)

                assertEquals(Command.UpdateTheme, awaitItem())

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenSystemDefaultThemeIsDarkAndUserSelectsDarkThenUpdatePreference() =
        runTest {
            // Given: System default is set, but system is in dark mode, so the effective theme is DARK
            // However, the stored preference is still SYSTEM_DEFAULT
            whenever(mockThemeSettingsDataStore.theme).thenReturn(DuckDuckGoTheme.DARK)
            whenever(mockThemeSettingsDataStore.isCurrentlySelected(DuckDuckGoTheme.DARK)).thenReturn(false)
            initializeViewModel()

            testee.commands().test {
                // When: User explicitly selects DARK theme (wants to lock it to dark, not follow system)
                testee.onThemeSelected(DuckDuckGoTheme.DARK)

                // Then: Preference is updated to DARK and pixel is fired
                verify(mockThemeSettingsDataStore).theme = DuckDuckGoTheme.DARK
                verify(mockPixel).fire(AppPixelName.SETTINGS_THEME_TOGGLED_DARK)

                assertEquals(Command.UpdateTheme, awaitItem())

                cancelAndConsumeRemainingEvents()
            }
        }

    private fun givenThemeSelected(theme: DuckDuckGoTheme) {
        whenever(mockThemeSettingsDataStore.theme).thenReturn(theme)
        whenever(mockThemeSettingsDataStore.isCurrentlySelected(theme)).thenReturn(true)
        initializeViewModel()
    }

    @Test
    fun whenBrowserMenuOptionDisabledThenViewStateHasOptionFalse() =
        runTest {
            // Given
            whenever(mockBrowserMenuDisplayRepository.browserMenuState)
                .thenReturn(flowOf(BrowserMenuDisplayState(hasOption = false, isEnabled = false)))

            // When
            initializeViewModel()

            // Then
            testee.viewState().test {
                val state = awaitItem()
                assertFalse(state.hasExperimentalBrowserMenuOption)
                assertFalse(state.useBottomSheetMenuEnabled)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenBrowserMenuEnabledThenViewStateReflectsIt() =
        runTest {
            // Given
            whenever(mockBrowserMenuDisplayRepository.browserMenuState)
                .thenReturn(flowOf(BrowserMenuDisplayState(hasOption = true, isEnabled = true)))

            // When
            initializeViewModel()

            // Then
            testee.viewState().test {
                val state = awaitItem()
                assertTrue(state.hasExperimentalBrowserMenuOption)
                assertTrue(state.useBottomSheetMenuEnabled)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenBrowserMenuStateChangesThenViewStateUpdates() =
        runTest {
            // Given
            val stateFlow = MutableStateFlow(BrowserMenuDisplayState(hasOption = true, isEnabled = false))
            whenever(mockBrowserMenuDisplayRepository.browserMenuState).thenReturn(stateFlow)

            initializeViewModel()

            // When/Then
            testee.viewState().test {
                val initialState = awaitItem()
                assertFalse(initialState.useBottomSheetMenuEnabled)

                // When state changes
                stateFlow.value = BrowserMenuDisplayState(hasOption = true, isEnabled = true)

                val updatedState = awaitItem()
                assertTrue(updatedState.useBottomSheetMenuEnabled)

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenOnUseBottomSheetMenuChangedThenRepositoryUpdated() =
        runTest {
            // Given
            initializeViewModel()

            // When
            testee.onUseBottomSheetMenuChanged(true)

            // Then
            verify(mockBrowserMenuDisplayRepository).setExperimentalMenuEnabled(true)
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_ENABLED_DAILY,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Daily(),
            )
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_ENABLED_UNIQUE,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Unique(),
            )
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_ENABLED,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Count,
            )
        }

    @Test
    fun whenOnUseBottomSheetMenuChangedToFalseThenRepositoryUpdated() =
        runTest {
            // Given
            initializeViewModel()

            // When
            testee.onUseBottomSheetMenuChanged(false)

            // Then
            verify(mockBrowserMenuDisplayRepository).setExperimentalMenuEnabled(false)
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_DISABLED_DAILY,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Daily(),
            )
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_DISABLED_UNIQUE,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Unique(),
            )
            verify(mockPixel).fire(
                pixel = AppPixelName.EXPERIMENTAL_MENU_DISABLED,
                parameters = emptyMap(),
                encodedParameters = emptyMap(),
                type = Pixel.PixelType.Count,
            )
        }
}
