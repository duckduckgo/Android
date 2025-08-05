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

package com.duckduckgo.duckchat.impl.pixel

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DuckChatDailyPixelSenderTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockDuckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()

    private lateinit var testee: DuckChatDailyPixelSender

    @Before
    fun setup() {
        testee = DuckChatDailyPixelSender(
            pixel = mockPixel,
            duckChatFeatureRepository = mockDuckChatFeatureRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = coroutineRule.testScope,
        )
    }

    @Test
    fun `when onStart then fire daily pixels with correct parameters`() = runTest {
        whenever(mockDuckChatFeatureRepository.isDuckChatUserEnabled()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.shouldShowInBrowserMenu()).thenReturn(false)
        whenever(mockDuckChatFeatureRepository.shouldShowInAddressBar()).thenReturn(true)
        whenever(mockDuckChatFeatureRepository.isInputScreenUserSettingEnabled()).thenReturn(false)

        testee.onStart(mockLifecycleOwner)

        advanceUntilIdle()

        verify(mockPixel).fire(
            pixel = DuckChatPixelName.DUCK_CHAT_IS_ENABLED_DAILY,
            parameters = mapOf(PixelParameter.IS_ENABLED to "true"),
            type = Daily(),
        )
        verify(mockPixel).fire(
            pixel = DuckChatPixelName.DUCK_CHAT_BROWSER_MENU_IS_ENABLED_DAILY,
            parameters = mapOf(PixelParameter.IS_ENABLED to "false"),
            type = Daily(),
        )
        verify(mockPixel).fire(
            pixel = DuckChatPixelName.DUCK_CHAT_ADDRESS_BAR_IS_ENABLED_DAILY,
            parameters = mapOf(PixelParameter.IS_ENABLED to "true"),
            type = Daily(),
        )
        verify(mockPixel).fire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_ADDRESS_BAR_IS_ENABLED_DAILY,
            parameters = mapOf(PixelParameter.IS_ENABLED to "false"),
            type = Daily(),
        )
    }
}
