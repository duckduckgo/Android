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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.ui.DuckChatSettingsViewModel.Command.OpenLearnMore
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

    @Before
    fun setUp() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat)
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
    fun whenViewModelIsCreatedAndShowInBrowserIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            assertTrue(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            assertFalse(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(true))
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            assertTrue(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            assertFalse(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenDuckChatEnabledAndAddressBarEntryPointEnabledThenBothSubTogglesShown() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(true)
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowBrowserMenuToggle)
            assertTrue(state.shouldShowAddressBarToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDuckChatEnabledAndAddressBarEntryPointDisabledThenOnlyBrowserToggleShown() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(true))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(false)
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowBrowserMenuToggle)
            assertFalse(state.shouldShowAddressBarToggle)
        }
    }

    @Test
    fun whenDuckChatDisabledThenNoSubTogglesShown() = runTest {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(true)
        testee = DuckChatSettingsViewModel(duckChat)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShowBrowserMenuToggle)
            assertFalse(state.shouldShowAddressBarToggle)
        }
    }

    @Test
    fun whenDuckChatLearnMoreClickedThenOpenLearnMoreCommandEmitted() = runTest {
        testee.duckChatLearnMoreClicked()

        testee.commands.test {
            val command = awaitItem()
            assertTrue(command is OpenLearnMore)
            command as OpenLearnMore
            assertEquals(
                "https://duckduckgo.com/duckduckgo-help-pages/aichat/",
                command.learnMoreLink,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
