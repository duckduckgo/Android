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

package com.duckduckgo.duckchat.impl.ui

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.experiments.visual.store.ExperimentalThemingDataStore
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.ui.DuckChatSettingsViewModel.Command.OpenLink
import com.duckduckgo.duckchat.impl.ui.DuckChatSettingsViewModel.Command.OpenLinkInNewTab
import com.duckduckgo.subscriptions.api.SubscriptionRebrandingFeatureToggle
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
    private val mockExperimentalThemingDataStore: ExperimentalThemingDataStore = mock()
    private val mockRebrandingFeatureToggle: SubscriptionRebrandingFeatureToggle = mock()

    @Before
    fun setUp() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)
    }

    @Test
    fun whenDuckChatUserEnabledToggledDisabledThenSetUserSetting() = runTest {
        testee.onDuckChatUserEnabledToggled(false)
        verify(duckChat).setEnableDuckChatUserSetting(false)
    }

    @Test
    fun whenDuckChatUserEnabledToggledEnabledThenSetUserSetting() = runTest {
        testee.onDuckChatUserEnabledToggled(true)
        verify(duckChat).setEnableDuckChatUserSetting(true)
    }

    @Test
    fun whenShowDuckChatInMenuDisabledThenSetUserSetting() = runTest {
        testee.onShowDuckChatInMenuToggled(false)
        verify(duckChat).setShowInBrowserMenuUserSetting(false)
    }

    @Test
    fun whenShowDuckChatInMenuEnabledThenSetUserSetting() = runTest {
        testee.onShowDuckChatInMenuToggled(true)
        verify(duckChat).setShowInBrowserMenuUserSetting(true)
    }

    @Test
    fun whenShowDuckChatInAddressBarDisabledThenSetUserSetting() = runTest {
        testee.onShowDuckChatInAddressBarToggled(false)
        verify(duckChat).setShowInAddressBarUserSetting(false)
    }

    @Test
    fun whenShowDuckChatInAddressBarEnabledThenSetUserSetting() = runTest {
        testee.onShowDuckChatInAddressBarToggled(true)
        verify(duckChat).setShowInAddressBarUserSetting(true)
    }

    @Test
    fun `when onDuckAiInputScreen enabled then set user setting`() = runTest {
        testee.onDuckAiInputScreenToggled(true)
        verify(duckChat).setInputScreenUserSetting(true)
    }

    @Test
    fun `when onDuckAiInputScreen disabled then set user setting`() = runTest {
        testee.onDuckAiInputScreenToggled(false)
        verify(duckChat).setInputScreenUserSetting(false)
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertTrue(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertFalse(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertTrue(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertFalse(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenDuckChatEnabledAndAddressBarEntryPointEnabledThenBothSubTogglesShown() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(true)
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowBrowserMenuToggle)
            assertTrue(state.shouldShowAddressBarToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDuckChatEnabledAndAddressBarEntryPointDisabledThenOnlyBrowserToggleShown() = runTest {
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(false)
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowBrowserMenuToggle)
            assertFalse(state.shouldShowAddressBarToggle)
        }
    }

    @Test
    fun `input screen - user preference enabled then set correct state`() = runTest {
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertTrue(awaitItem().isInputScreenEnabled)
        }
    }

    @Test
    fun `input screen - user preference disabled then set correct state`() = runTest {
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            assertFalse(awaitItem().isInputScreenEnabled)
        }
    }

    @Test
    fun `input screen - when duck chat enabled and flag enabled, then emit enabled`() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(true)
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowInputScreenToggle)
        }
    }

    @Test
    fun `input screen - when flag disabled, then emit disabled`() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isInputScreenFeatureAvailable()).thenReturn(false)
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShowInputScreenToggle)
        }
    }

    @Test
    fun whenDuckChatDisabledThenNoSubTogglesShown() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(true)
        whenever(duckChat.observeInputScreenUserSettingEnabled()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat, mockPixel, mockExperimentalThemingDataStore, mockRebrandingFeatureToggle)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShowBrowserMenuToggle)
            assertFalse(state.shouldShowAddressBarToggle)
            assertFalse(state.shouldShowInputScreenToggle)
        }
    }

    @Test
    fun whenDuckChatLearnMoreClickedThenOpenLearnMoreCommandEmitted() = runTest {
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
    fun whenDuckChatSearchAISettingsClickedThenOpenSettingsLinkCommandEmitted() = runTest {
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
    fun whenDuckChatSearchAISettingsClickedThenPixelIsSent() = runTest {
        testee.duckChatSearchAISettingsClicked()
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_SEARCH_ASSIST_SETTINGS_BUTTON_CLICKED)
    }
}
