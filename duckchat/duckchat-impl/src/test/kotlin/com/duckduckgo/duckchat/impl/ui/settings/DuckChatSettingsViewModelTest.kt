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

package com.duckduckgo.duckchat.impl.ui.settings

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatNativeSettingsNoParams
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.duckchat.impl.store.HideAiGeneratedImages
import com.duckduckgo.duckchat.impl.store.SearchAssistVisibility
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenShortcutSettings
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.ShowDefaultTogglePositionDialog
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.ShowHideAiGeneratedImagesDialog
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.ShowSearchAssistDialog
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import com.duckduckgo.settings.api.SettingsPageFeature
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatSettingsViewModelTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckChatSettingsViewModel

    private val duckChat: DuckChatInternal = mock()
    private val mockPixel: Pixel = mock()
    private val mockInputScreenDiscoveryFunnel: InputScreenDiscoveryFunnel = mock()
    private val mockDuckChatPixels: DuckChatPixels = mock()
    private val settingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()

    @Before
    fun setUp() =
        runTest {
            @Suppress("DenyListedApi")
            settingsPageFeature.embeddedSettingsWebView().setRawStoredState(State(enable = false))
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(null))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(false))
            whenever(duckChat.observeDefaultTogglePosition()).thenReturn(flowOf(DefaultTogglePosition.SEARCH))
            // Default both SERP-backed settings (kbe, kbj) to "no value synced"; individual tests override per key.
            whenever(serpSettingsDataProvider.observeSetting(any())).thenReturn(flowOf(null))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )
        }

    @Test
    fun whenDuckChatUserEnabledToggledDisabledThenSetUserSetting() =
        runTest {
            testee.onDuckChatUserEnabledToggled(false)
            verify(duckChat).setEnableDuckChatUserSetting(false)
        }

    @Test
    fun whenDuckChatUserEnabledToggledEnabledThenSetUserSetting() =
        runTest {
            testee.onDuckChatUserEnabledToggled(true)
            verify(duckChat).setEnableDuckChatUserSetting(true)
        }

    @Test
    fun whenShowDuckChatInMenuDisabledThenSetUserSetting() =
        runTest {
            testee.onShowDuckChatInMenuToggled(false)
            verify(duckChat).setShowInBrowserMenuUserSetting(false)
        }

    @Test
    fun whenShowDuckChatInMenuEnabledThenSetUserSetting() =
        runTest {
            testee.onShowDuckChatInMenuToggled(true)
            verify(duckChat).setShowInBrowserMenuUserSetting(true)
        }

    @Test
    fun `when onDuckAiInputScreenWithAiSelected selected then set user setting`() =
        runTest {
            testee.onDuckAiInputScreenWithAiSelected()
            verify(duckChat).setInputScreenUserSetting(true)
        }

    @Test
    fun `when onDuckAiInputScreenWithoutAiSelected selected then set user setting`() =
        runTest {
            testee.onDuckAiInputScreenWithoutAiSelected()
            verify(duckChat).setInputScreenUserSetting(false)
        }

    @Test
    fun `when onDuckAiInputScreenWithAiSelected then discovery funnel onInputScreenEnabled is called`() =
        runTest {
            testee.onDuckAiInputScreenWithAiSelected()
            verify(mockInputScreenDiscoveryFunnel).onInputScreenEnabled()
        }

    @Test
    fun `when onDuckAiInputScreenWithoutAiSelected then discovery funnel onInputScreenDisabled is called`() =
        runTest {
            testee.onDuckAiInputScreenWithoutAiSelected()
            verify(mockInputScreenDiscoveryFunnel).onInputScreenDisabled()
        }

    @Test
    fun `input screen - user preference enabled then set correct state`() =
        runTest {
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(null))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - user preference disabled then set correct state`() =
        runTest {
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(null))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - cosmetic enabled then use cosmetic value`() =
        runTest {
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - cosmetic disabled then use cosmetic value`() =
        runTest {
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - cosmetic null then fallback to actual inputScreen value`() =
        runTest {
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(null))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - when duck chat enabled and flag enabled, then emit enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertTrue(state.shouldShowInputScreenToggle)
            }
        }

    @Test
    fun `input screen - when flag disabled, then emit disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(false)
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertFalse(state.shouldShowInputScreenToggle)
            }
        }

    @Test
    fun whenDuckChatDisabledThenNoSubTogglesShown() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeCosmeticInputScreenUserSettingEnabled()).thenReturn(flowOf(null))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertFalse(state.shouldShowInputScreenToggle)
            }
        }

    @Test
    fun whenDuckChatLearnMoreClickedThenOpenLearnMoreCommandEmitted() =
        runTest {
            testee.duckChatLearnMoreClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    "https://duckduckgo.com/duckduckgo-help-pages/aichat/",
                    command.link,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenDuckChatSearchAISettingsClickedThenOpenSettingsLinkCommandEmitted() =
        runTest {
            testee.duckChatSearchAISettingsClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLinkInNewTab)
                command as OpenLinkInNewTab
                assertEquals(
                    "https://duckduckgo.com/settings?ko=-1#aifeatures",
                    command.link,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenDuckChatSearchAISettingsClickedThenPixelIsSent() =
        runTest {
            testee.duckChatSearchAISettingsClicked()
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED)
        }

    @Test
    fun whenDuckChatSearchAISettingsClickedAndEmbeddedEnabledAndHideAiGeneratedImagesDisabledThenOpenSettingsLinkWithLegacyLink() =
        runTest {
            @Suppress("DenyListedApi")
            settingsPageFeature.embeddedSettingsWebView().setRawStoredState(State(enable = true))
            @Suppress("DenyListedApi")
            duckChatFeature.showHideAiGeneratedImages().setRawStoredState(State(enable = false))

            testee.duckChatSearchAISettingsClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    DuckChatSettingsViewModel.LEGACY_DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED,
                    command.link,
                )
                assertEquals(R.string.duck_chat_assist_settings_title, command.titleRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenDuckChatSearchAISettingsClickedAndEmbeddedEnabledAndHideAiGeneratedImagesEnabledThenOpenSettingsLinkWithNewLink() =
        runTest {
            @Suppress("DenyListedApi")
            settingsPageFeature.embeddedSettingsWebView().setRawStoredState(State(enable = true))
            @Suppress("DenyListedApi")
            duckChatFeature.showHideAiGeneratedImages().setRawStoredState(State(enable = true))

            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.duckChatSearchAISettingsClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    DuckChatSettingsViewModel.DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED,
                    command.link,
                )
                assertEquals(R.string.duckAiSerpSettingsTitle, command.titleRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when onDuckChatUserEnabledToggled true then enabled pixel fired`() =
        runTest {
            testee.onDuckChatUserEnabledToggled(true)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_USER_ENABLED)
        }

    @Test
    fun `when onDuckChatUserEnabledToggled false then disabled pixel fired`() =
        runTest {
            testee.onDuckChatUserEnabledToggled(false)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
        }

    @Test
    fun `when onDuckAiInputScreenWithAiSelected true then on pixel fired`() =
        runTest {
            testee.onDuckAiInputScreenWithAiSelected()
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_ON)
        }

    @Test
    fun `when onDuckAiInputScreenWithoutAiSelected false then off pixel fired`() =
        runTest {
            testee.onDuckAiInputScreenWithoutAiSelected()
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_SETTING_OFF)
        }

    @Test
    fun `when onShowDuckChatInMenuToggled true then on pixel fired`() =
        runTest {
            testee.onShowDuckChatInMenuToggled(true)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
        }

    @Test
    fun `when onShowDuckChatInMenuToggled false then off pixel fired`() =
        runTest {
            testee.onShowDuckChatInMenuToggled(false)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
        }

    @Test
    fun `when Duck ai shortcuts clicked, then dispatch launch command`() =
        runTest {
            testee.onDuckAiShortcutsClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenShortcutSettings)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when input toggle share feedback clicked, then dispatch launch command`() =
        runTest {
            testee.duckAiInputScreenShareFeedbackClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is LaunchFeedback)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when hideAiGeneratedImagesOption is enabled then viewState shows option visible`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.showHideAiGeneratedImages().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertTrue(state.isHideGeneratedImagesOptionVisible)
            }
        }

    @Test
    fun `when hideAiGeneratedImagesOption is disabled then viewState shows option not visible`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.showHideAiGeneratedImages().setRawStoredState(State(enable = false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertFalse(state.isHideGeneratedImagesOptionVisible)
            }
        }

    @Test
    fun `when aiFeaturesNativeControls is enabled then viewState shows native controls enabled`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isNativeControlsEnabled)
            }
        }

    @Test
    fun `when aiFeaturesNativeControls is disabled then viewState shows native controls disabled`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isNativeControlsEnabled)
            }
        }

    @Test
    fun `when DuckChatSettingsNoParams passed then viewState shows search section visible`() =
        runTest {
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertTrue(state.isSearchSectionVisible)
            }
        }

    @Test
    fun `when DuckChatNativeSettingsNoParams passed then viewState shows search section hidden`() =
        runTest {
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertFalse(state.isSearchSectionVisible)
            }
        }

    @Test
    fun `when DuckChatNativeSettingsNoParams passed and aiFeaturesNativeControls enabled then viewState shows search section visible`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                val state = awaitItem()
                assertTrue(state.isSearchSectionVisible)
            }
        }

    @Test
    fun `when onDuckAiHideAiGeneratedImagesClicked and native controls disabled then SERP open pixel is fired`() =
        runTest {
            testee.onDuckAiHideAiGeneratedImagesClicked()
            verify(mockPixel).fire(DuckChatPixelName.SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES)
        }

    @Test
    fun `when onDuckAiHideAiGeneratedImagesClicked then OpenLink command with correct link is emitted`() =
        runTest {
            testee.onDuckAiHideAiGeneratedImagesClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    DuckChatSettingsViewModel.DUCK_CHAT_HIDE_GENERATED_IMAGES_LINK_EMBEDDED,
                    command.link,
                )
                assertEquals(R.string.duckAiSerpSettingsTitle, command.titleRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when onHideAiGeneratedImagesLearnMoreClicked then OpenLink command with learn more link is emitted`() =
        runTest {
            testee.onHideAiGeneratedImagesLearnMoreClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    DuckChatSettingsViewModel.DUCK_CHAT_HIDE_GENERATED_IMAGES_LEARN_MORE_LINK,
                    command.link,
                )
                assertEquals(R.string.duckAiDialogHideAiGeneratedImagesTitle, command.titleRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when onDuckAiHideAiGeneratedImagesClicked and native controls enabled then ShowHideAiGeneratedImagesDialog emitted`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = true))
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.ON.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                awaitItem()
                testee.onDuckAiHideAiGeneratedImagesClicked()

                testee.commands.test {
                    val command = awaitItem()
                    assertTrue(command is ShowHideAiGeneratedImagesDialog)
                    assertEquals(HideAiGeneratedImages.ON, (command as ShowHideAiGeneratedImagesDialog).current)
                    cancelAndIgnoreRemainingEvents()
                }
            }

            // The SERP-open pixel must not fire for the native dialog; it only tracks opening the SERP webview.
            verify(mockPixel, never()).fire(DuckChatPixelName.SERP_SETTINGS_OPEN_HIDE_AI_GENERATED_IMAGES)
        }

    @Test
    fun `when hide ai generated images selected then persisted to SERP settings`() =
        runTest {
            testee.onHideAiGeneratedImagesSelected(HideAiGeneratedImages.ON)
            verify(serpSettingsDataProvider).setSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY, HideAiGeneratedImages.ON.serpCode)
        }

    @Test
    fun `when onUseWithoutAiClicked then ai_features_disabled count and daily fired`() =
        runTest {
            testee.onUseWithoutAiClicked()
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_DISABLED_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_DISABLED_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when search assist set to never then never count and daily fired`() =
        runTest {
            testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.NEVER)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_NEVER_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_NEVER_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when search assist set to on demand then on_demand count and daily fired`() =
        runTest {
            testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.ON_DEMAND)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_ON_DEMAND_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when search assist set to sometimes from a different value then sometimes count and daily fired`() =
        runTest {
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.NEVER.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                var state = awaitItem()
                while (state.searchAssistVisibility != SearchAssistVisibility.NEVER) {
                    state = awaitItem()
                }
                testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.SOMETIMES)
                cancelAndIgnoreRemainingEvents()
            }

            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when search assist re-selected with same value then no count or daily pixel and still persisted`() =
        runTest {
            // Default current value is Sometimes; re-selecting it must not fire telemetry but must still persist.
            testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.SOMETIMES)

            verify(mockPixel, never()).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_COUNT)
            verify(mockPixel, never()).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_SOMETIMES_DAILY, type = Pixel.PixelType.Daily())
            verify(serpSettingsDataProvider).setSetting(SearchAssistVisibility.SERP_SETTINGS_KEY, SearchAssistVisibility.SOMETIMES.serpCode)
        }

    @Test
    fun `when search assist set to often then often count and daily fired`() =
        runTest {
            testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.OFTEN)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_OFTEN_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_SEARCH_ASSIST_OFTEN_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when hide images selected on then hide_images_on count and daily fired`() =
        runTest {
            testee.onHideAiGeneratedImagesSelected(HideAiGeneratedImages.ON)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_ON_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_ON_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when hide images set to off from a different value then hide_images_off count and daily fired`() =
        runTest {
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.ON.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                var state = awaitItem()
                while (state.hideAiGeneratedImages != HideAiGeneratedImages.ON) {
                    state = awaitItem()
                }
                testee.onHideAiGeneratedImagesSelected(HideAiGeneratedImages.OFF)
                cancelAndIgnoreRemainingEvents()
            }

            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_COUNT)
            verify(mockPixel).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_DAILY, type = Pixel.PixelType.Daily())
        }

    @Test
    fun `when hide images re-selected with same value then no count or daily pixel and still persisted`() =
        runTest {
            // Default current value is Off; re-selecting it must not fire telemetry but must still persist.
            testee.onHideAiGeneratedImagesSelected(HideAiGeneratedImages.OFF)

            verify(mockPixel, never()).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_COUNT)
            verify(mockPixel, never()).fire(DuckChatPixelName.AI_FEATURES_HIDE_IMAGES_OFF_DAILY, type = Pixel.PixelType.Daily())
            verify(serpSettingsDataProvider).setSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY, HideAiGeneratedImages.OFF.serpCode)
        }

    @Test
    fun `when onUseWithoutAiClicked and duck chat enabled then duck chat user setting disabled`() =
        runTest {
            // Duck.ai is enabled by default in setUp(); collect viewState so the gate sees isDuckChatUserEnabled = true.
            testee.viewState.test {
                var state = awaitItem()
                while (!state.isDuckChatUserEnabled) {
                    state = awaitItem()
                }
                testee.onUseWithoutAiClicked()
                cancelAndIgnoreRemainingEvents()
            }
            verify(duckChat).setEnableDuckChatUserSetting(false)
        }

    @Test
    fun `when onUseWithoutAiClicked and duck chat enabled then disabled pixel fired`() =
        runTest {
            testee.viewState.test {
                var state = awaitItem()
                while (!state.isDuckChatUserEnabled) {
                    state = awaitItem()
                }
                testee.onUseWithoutAiClicked()
                cancelAndIgnoreRemainingEvents()
            }
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
        }

    @Test
    fun `when onUseWithoutAiClicked and duck chat already off then no disable pixel or write`() =
        runTest {
            // Duck.ai already off, but the action is still reachable because Search Assist is not Never.
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.SOMETIMES.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                // Resolve to the off state before acting.
                var state = awaitItem()
                while (state.isDuckChatUserEnabled) {
                    state = awaitItem()
                }
                testee.onUseWithoutAiClicked()
                cancelAndIgnoreRemainingEvents()
            }

            verify(mockPixel, never()).fire(DuckChatPixelName.DUCK_CHAT_USER_DISABLED)
            verify(duckChat, never()).setEnableDuckChatUserSetting(false)
        }

    @Test
    fun `when onUseWithoutAiClicked then search assist visibility set to never in SERP settings`() =
        runTest {
            testee.onUseWithoutAiClicked()
            verify(serpSettingsDataProvider).setSetting(SearchAssistVisibility.SERP_SETTINGS_KEY, SearchAssistVisibility.NEVER.serpCode)
        }

    @Test
    fun `when onUseWithoutAiClicked then hide ai generated images set to on in SERP settings`() =
        runTest {
            testee.onUseWithoutAiClicked()
            verify(serpSettingsDataProvider).setSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY, HideAiGeneratedImages.ON.serpCode)
        }

    @Test
    fun `when duck chat off and search assist never and images hidden then use without ai action disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.NEVER.serpCode))
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.ON.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isUseWithoutAiActionEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when duck chat on but search assist never and images hidden then use without ai action enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.NEVER.serpCode))
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.ON.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isUseWithoutAiActionEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when duck chat off but search assist not never then use without ai action enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.OFTEN.serpCode))
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.ON.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isUseWithoutAiActionEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when duck chat off and search assist never but images not hidden then use without ai action enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.NEVER.serpCode))
            whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(HideAiGeneratedImages.OFF.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isUseWithoutAiActionEnabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenAutomaticContextAttachmentToggledThenSetUserSetting() =
        runTest {
            testee.onAutomaticContextAttachmentToggled(true)
            verify(duckChat).setAutomaticPageContextUserSetting(true)
        }

    @Test
    fun `when automatic context attachment toggled on then report pixel`() = runTest {
        testee.onAutomaticContextAttachmentToggled(true)

        verify(mockDuckChatPixels).reportContextualSettingAutomaticPageContentToggled(true)
    }

    @Test
    fun `when automatic context attachment toggled off then report pixel`() = runTest {
        testee.onAutomaticContextAttachmentToggled(false)

        verify(mockDuckChatPixels).reportContextualSettingAutomaticPageContentToggled(false)
    }

    @Test
    fun `view state - automatic context enabled then set correct state`() =
        runTest {
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(true))

            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isAutomaticContextEnabled)
            }
        }

    @Test
    fun `view state - automatic context disabled then set correct state`() =
        runTest {
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isAutomaticContextEnabled)
            }
        }

    @Test
    fun `view state - automatic context visible when flag enabled and duck chat enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(false))
            @Suppress("DenyListedApi")
            duckChatFeature.automaticContextAttachment().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isAutomaticContextVisible)
            }
        }

    @Test
    fun `view state - automatic context hidden when flag enabled but duck chat disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(false))
            @Suppress("DenyListedApi")
            duckChatFeature.automaticContextAttachment().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isAutomaticContextVisible)
            }
        }

    @Test
    fun `view state - automatic context hidden when flag disabled`() =
        runTest {
            whenever(duckChat.observeAutomaticContextAttachmentUserSettingEnabled()).thenReturn(flowOf(false))
            @Suppress("DenyListedApi")
            duckChatFeature.automaticContextAttachment().setRawStoredState(State(enable = false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatNativeSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isAutomaticContextVisible)
            }
        }

    @Test
    fun `default toggle position - visible when all conditions met`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            @Suppress("DenyListedApi")
            duckChatFeature.rememberTogglePosition().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertTrue(awaitItem().isDefaultTogglePositionVisible)
            }
        }

    @Test
    fun `default toggle position - hidden when duck chat disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            @Suppress("DenyListedApi")
            duckChatFeature.rememberTogglePosition().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isDefaultTogglePositionVisible)
            }
        }

    @Test
    fun `default toggle position - hidden when input screen disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            @Suppress("DenyListedApi")
            duckChatFeature.rememberTogglePosition().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isDefaultTogglePositionVisible)
            }
        }

    @Test
    fun `default toggle position - hidden when feature flag disabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            @Suppress("DenyListedApi")
            duckChatFeature.rememberTogglePosition().setRawStoredState(State(enable = false))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isDefaultTogglePositionVisible)
            }
        }

    @Test
    fun `default toggle position - hidden when input screen feature not available`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(false)
            @Suppress("DenyListedApi")
            duckChatFeature.rememberTogglePosition().setRawStoredState(State(enable = true))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertFalse(awaitItem().isDefaultTogglePositionVisible)
            }
        }

    @Test
    fun `when default toggle position clicked then show dialog command emitted`() =
        runTest {
            whenever(duckChat.observeDefaultTogglePosition()).thenReturn(flowOf(DefaultTogglePosition.DUCK_AI))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                awaitItem()
                testee.onDefaultTogglePositionClicked()

                testee.commands.test {
                    val command = awaitItem()
                    assertTrue(command is ShowDefaultTogglePositionDialog)
                    assertEquals(DefaultTogglePosition.DUCK_AI, (command as ShowDefaultTogglePositionDialog).currentPosition)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

    @Test
    fun `when default toggle position selected then set user setting`() =
        runTest {
            testee.onDefaultTogglePositionSelected(DefaultTogglePosition.LAST_USED)
            verify(duckChat).setDefaultTogglePosition(DefaultTogglePosition.LAST_USED)
        }

    @Test
    fun `when default toggle position selected then count and daily pixels fired with correct value`() =
        runTest {
            testee.onDefaultTogglePositionSelected(DefaultTogglePosition.DUCK_AI)

            verify(mockPixel).fire(
                pixel = DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_COUNT,
                parameters = mapOf(DuckChatPixelParameters.DEFAULT_TOGGLE_POSITION_VALUE to "duckAI"),
            )
            verify(mockPixel).fire(
                pixel = DuckChatPixelName.DUCK_CHAT_SETTINGS_DEFAULT_TOGGLE_POSITION_CHANGED_DAILY,
                parameters = mapOf(DuckChatPixelParameters.DEFAULT_TOGGLE_POSITION_VALUE to "duckAI"),
                type = Pixel.PixelType.Daily(),
            )
        }

    @Test
    fun `when no search assist visibility synced then viewState defaults to SOMETIMES`() =
        runTest {
            testee.viewState.test {
                assertEquals(SearchAssistVisibility.SOMETIMES, awaitItem().searchAssistVisibility)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when search assist settings clicked and native controls enabled and no value synced then ShowSearchAssistDialog defaults to SOMETIMES`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = true))

            testee.viewState.test {
                awaitItem()
                testee.duckChatSearchAISettingsClicked()

                testee.commands.test {
                    val command = awaitItem()
                    assertTrue(command is ShowSearchAssistDialog)
                    assertEquals(SearchAssistVisibility.SOMETIMES, (command as ShowSearchAssistDialog).currentVisibility)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

    @Test
    fun `when search assist visibility selected then persisted to SERP settings`() =
        runTest {
            testee.onSearchAssistVisibilitySelected(SearchAssistVisibility.OFTEN)
            verify(serpSettingsDataProvider).setSetting(SearchAssistVisibility.SERP_SETTINGS_KEY, SearchAssistVisibility.OFTEN.serpCode)
        }

    @Test
    fun `when search assist visibility already stored then viewState reflects it`() =
        runTest {
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.SOMETIMES.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                assertEquals(SearchAssistVisibility.SOMETIMES, awaitItem().searchAssistVisibility)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `when search assist visibility already stored then ShowSearchAssistDialog carries current visibility`() =
        runTest {
            @Suppress("DenyListedApi")
            duckChatFeature.aiFeaturesNativeControls().setRawStoredState(State(enable = true))
            whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY))
                .thenReturn(flowOf(SearchAssistVisibility.SOMETIMES.serpCode))
            testee = DuckChatSettingsViewModel(
                duckChatActivityParams = DuckChatSettingsNoParams,
                duckChat = duckChat,
                pixel = mockPixel,
                inputScreenDiscoveryFunnel = mockInputScreenDiscoveryFunnel,
                settingsPageFeature = settingsPageFeature,
                duckChatPixels = mockDuckChatPixels,
                dispatcherProvider = coroutineRule.testDispatcherProvider,
                duckChatFeature = duckChatFeature,
                serpSettingsDataProvider = serpSettingsDataProvider,
            )

            testee.viewState.test {
                awaitItem()
                testee.duckChatSearchAISettingsClicked()

                testee.commands.test {
                    val command = awaitItem()
                    assertTrue(command is ShowSearchAssistDialog)
                    assertEquals(SearchAssistVisibility.SOMETIMES, (command as ShowSearchAssistDialog).currentVisibility)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
}
