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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DuckChatInputScreenDauPluginTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val duckChatFeatureRepository: DuckChatFeatureRepository = mock()
    private val pixel: Pixel = mock()

    private val testee = DuckChatInputScreenDauPlugin(
        duckChatFeatureRepository = duckChatFeatureRepository,
        pixel = pixel,
        coroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when new DAU day and toggle never enabled then fires pixel`() = runTest {
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(false)

        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v100-1",
            newAtb = "v100-2",
            metadata = emptyMap(),
        )
        advanceUntilIdle()

        verify(pixel).fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED)
    }

    @Test
    fun `when new DAU day and toggle was ever enabled then does not fire pixel`() = runTest {
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(true)

        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v100-1",
            newAtb = "v100-2",
            metadata = emptyMap(),
        )
        advanceUntilIdle()

        verify(pixel, never()).fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED)
    }

    @Test
    fun `when same DAU day (oldAtb equals newAtb) then does not fire pixel`() = runTest {
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(false)

        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v100-1",
            newAtb = "v100-1",
            metadata = emptyMap(),
        )
        advanceUntilIdle()

        verify(pixel, never()).fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED)
    }

    @Test
    fun `when same DAU day but different cohort suffix then does not fire pixel`() = runTest {
        whenever(duckChatFeatureRepository.isInputScreenEverEnabled()).thenReturn(false)

        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v100-1ma",
            newAtb = "v100-1",
            metadata = emptyMap(),
        )
        advanceUntilIdle()

        verify(pixel, never()).fire(DUCK_CHAT_DUCKAI_DAU_TOGGLE_NEVER_ENABLED)
    }
}
