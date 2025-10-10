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
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.LaunchFeedback
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.duckchat.impl.ui.settings.DuckChatSettingsViewModel.Command.OpenShortcutSettings
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SettingsPageFeature
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
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
    private val settingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    @Before
    fun setUp() =
        runTest {
            @Suppress("DenyListedApi")
            settingsPageFeature.embeddedSettingsWebView().setRawStoredState(State(enable = false))
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)
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
    fun whenShowDuckChatInAddressBarDisabledThenSetUserSetting() =
        runTest {
            testee.onShowDuckChatInAddressBarToggled(false)
            verify(duckChat).setShowInAddressBarUserSetting(false)
        }

    @Test
    fun whenShowDuckChatInAddressBarEnabledThenSetUserSetting() =
        runTest {
            testee.onShowDuckChatInAddressBarToggled(true)
            verify(duckChat).setShowInAddressBarUserSetting(true)
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
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)

            testee.viewState.test {
                assertTrue(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - user preference disabled then set correct state`() =
        runTest {
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)

            testee.viewState.test {
                assertFalse(awaitItem().isInputScreenEnabled)
            }
        }

    @Test
    fun `input screen - when duck chat enabled and flag enabled, then emit enabled`() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
            whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)

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
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)

            testee.viewState.test {
                val state = awaitItem()
                assertFalse(state.shouldShowInputScreenToggle)
            }
        }

    @Test
    fun whenDuckChatDisabledThenNoSubTogglesShown() =
        runTest {
            whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
            whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
            testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockInputScreenDiscoveryFunnel, settingsPageFeature)

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
    fun whenDuckChatSearchAISettingsClickedAndSaveAndExitEnabledThenOpenSettingsLinkWithReturnParamEmitted() =
        runTest {
            @Suppress("DenyListedApi")
            settingsPageFeature.embeddedSettingsWebView().setRawStoredState(State(enable = true))

            testee.duckChatSearchAISettingsClicked()

            testee.commands.test {
                val command = awaitItem()
                assertTrue(command is OpenLink)
                command as OpenLink
                assertEquals(
                    DuckChatSettingsViewModel.DUCK_CHAT_SEARCH_AI_SETTINGS_LINK_EMBEDDED,
                    command.link,
                )
                assertEquals(R.string.duck_chat_assist_settings_title_rebranding, command.titleRes)
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
    fun `when onShowDuckChatInAddressBarToggled true then on pixel fired`() =
        runTest {
            testee.onShowDuckChatInAddressBarToggled(true)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_ON)
        }

    @Test
    fun `when onShowDuckChatInAddressBarToggled false then off pixel fired`() =
        runTest {
            testee.onShowDuckChatInAddressBarToggled(false)
            verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCHBAR_SETTING_OFF)
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
}
