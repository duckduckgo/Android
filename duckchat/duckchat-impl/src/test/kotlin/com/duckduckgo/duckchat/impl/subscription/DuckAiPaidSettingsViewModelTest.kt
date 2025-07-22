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

package com.duckduckgo.duckchat.impl.subscription

import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_SETTINGS_OPENED
import com.duckduckgo.duckchat.impl.subscription.DuckAiPaidSettingsViewModel.Command
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DuckAiPaidSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()

    private lateinit var testee: DuckAiPaidSettingsViewModel

    @Before
    fun setUp() {
        testee = DuckAiPaidSettingsViewModel(
            pixel = mockPixel,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when viewModel is initialized then settings opened pixel is fired`() {
        verify(mockPixel).fire(DUCK_CHAT_PAID_SETTINGS_OPENED)
    }

    @Test
    fun `when onLearnMoreSelected is called then LaunchLearnMoreWebPage command is emitted`() = runTest {
        testee.commands.test {
            testee.onLearnMoreSelected()
            assertEquals(Command.LaunchLearnMoreWebPage(), awaitItem())
        }
    }

    @Test
    fun `when onOpenDuckAiSelected is called then OpenDuckAi command is emitted`() = runTest {
        testee.commands.test {
            testee.onOpenDuckAiSelected()
            assertEquals(Command.OpenDuckAi, awaitItem())
        }
    }

    @Test
    fun `when onOpenDuckAiSelected is called then pixel is fired`() = runTest {
        testee.onOpenDuckAiSelected()
        verify(mockPixel).fire(DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED)
    }

    @Test
    fun `when LaunchLearnMoreWebPage command is created then it has correct default values`() {
        val command = Command.LaunchLearnMoreWebPage()
        assertEquals("https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/", command.url)
        assertEquals(com.duckduckgo.duckchat.impl.R.string.duck_ai_paid_settings_learn_more_title, command.titleId)
    }

    @Test
    fun `when LaunchLearnMoreWebPage command is created with custom values then it uses those values`() {
        val customUrl = "https://example.com/custom"
        val customTitleId = 123
        val command = Command.LaunchLearnMoreWebPage(url = customUrl, titleId = customTitleId)
        assertEquals(customUrl, command.url)
        assertEquals(customTitleId, command.titleId)
    }
}
