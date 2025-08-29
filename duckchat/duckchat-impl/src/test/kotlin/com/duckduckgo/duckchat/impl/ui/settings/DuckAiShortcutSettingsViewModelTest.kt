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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuckAiShortcutSettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckAiShortcutSettingsViewModel

    private val duckChat: DuckChatInternal = mock()

    @Before
    fun setUp() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        testee = DuckAiShortcutSettingsViewModel(duckChat)
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(true))
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            assertTrue(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            assertFalse(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsEnabledThenEmitEnabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(true))
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            assertTrue(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInAddressBarIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInAddressBarUserSetting()).thenReturn(flowOf(false))
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            assertFalse(awaitItem().showInAddressBar)
        }
    }

    @Test
    fun whenAddressBarEntryPointEnabledTogglesShown() = runTest {
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(true)
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShowAddressBarToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAddressBarEntryPointDisabledThenToggleHidden() = runTest {
        whenever(duckChat.isAddressBarEntryPointEnabled()).thenReturn(false)
        testee = DuckAiShortcutSettingsViewModel(duckChat)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShowAddressBarToggle)
        }
    }
}
