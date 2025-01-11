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

package com.duckduckgo.duckchat.impl

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
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
    fun setUp() {
        runTest {
            testee = DuckChatSettingsViewModel(duckChat)
        }
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
}
