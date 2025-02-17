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
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChatSettingsLaunchSource.Other
import com.duckduckgo.duckchat.api.DuckChatSettingsLaunchSource.Settings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckChatSettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: DuckChatSettingsViewModel

    @Mock
    private lateinit var duckChat: DuckChatInternal

    @Mock
    private lateinit var pixel: Pixel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = DuckChatSettingsViewModel(duckChat, pixel)
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
        testee = DuckChatSettingsViewModel(duckChat, pixel)

        testee.viewState.test {
            assertTrue(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun whenViewModelIsCreatedAndShowInBrowserIsDisabledThenEmitDisabled() = runTest {
        whenever(duckChat.observeShowInBrowserMenuUserSetting()).thenReturn(flowOf(false))
        testee = DuckChatSettingsViewModel(duckChat, pixel)

        testee.viewState.test {
            assertFalse(awaitItem().showInBrowserMenu)
        }
    }

    @Test
    fun `when screen opened, source settings and wasn't used before, then send count pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)

        testee.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED, mapOf("source" to "settings", "was_used_before" to "0"))
    }

    @Test
    fun `when screen opened, source settings and was used before, then send count pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(true)

        testee.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED, mapOf("source" to "settings", "was_used_before" to "1"))
    }

    @Test
    fun `when screen opened, source other and wasn't used before, then send count pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)

        testee.onScreenOpened(launchSource = Other)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED, mapOf("source" to "other", "was_used_before" to "0"))
    }

    @Test
    fun `when screen opened, source other and was used before, then send count pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(true)

        testee.onScreenOpened(launchSource = Other)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED, mapOf("source" to "other", "was_used_before" to "1"))
    }

    @Test
    fun `when screen opened, source settings, then send unique pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        testee.onScreenOpened(launchSource = Settings)

        verify(pixel).fire(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED_FROM_SETTINGS_UNIQUE, type = Pixel.PixelType.Unique())
    }

    @Test
    fun `when screen opened, source other, then don't send unique pixel`() = runTest {
        whenever(duckChat.wasOpenedBefore()).thenReturn(false)
        testee.onScreenOpened(launchSource = Other)

        verify(pixel, never()).fire(eq(DuckChatPixelName.DUCK_CHAT_SETTINGS_OPENED_FROM_SETTINGS_UNIQUE), any(), any(), any())
    }
}
