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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.usage

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class InputScreenSessionUsageMetricTest {

    private val pixel: Pixel = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val showInputScreenFlow = MutableStateFlow(true)

    private lateinit var testee: InputScreenSessionUsageMetricImpl

    @Before
    fun setup() {
        whenever(duckAiFeatureState.showInputScreen).thenReturn(showInputScreenFlow)
        testee = InputScreenSessionUsageMetricImpl(pixel, duckAiFeatureState)
    }

    @Test
    fun `when onStop called with feature enabled and no searches or prompts then pixel fired with zero counts`() {
        showInputScreenFlow.value = true

        testee.onStop(lifecycleOwner)

        val expectedParams = mapOf(
            "searches_in_session" to "0",
            "prompts_in_session" to "0",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = expectedParams,
        )
    }

    @Test
    fun `when onStop called with feature disabled then no pixel fired`() {
        showInputScreenFlow.value = false

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when onSearchSubmitted called once and feature enabled then counter increments`() {
        showInputScreenFlow.value = true

        testee.onSearchSubmitted()
        testee.onStop(lifecycleOwner)

        val expectedParams = mapOf(
            "searches_in_session" to "1",
            "prompts_in_session" to "0",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = expectedParams,
        )
    }

    @Test
    fun `when onPromptSubmitted called once and feature enabled then counter increments`() {
        showInputScreenFlow.value = true

        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        val expectedParams = mapOf(
            "searches_in_session" to "0",
            "prompts_in_session" to "1",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = expectedParams,
        )
    }

    @Test
    fun `when multiple searches and prompts submitted and feature enabled then counters increment correctly`() {
        showInputScreenFlow.value = true

        testee.onSearchSubmitted()
        testee.onSearchSubmitted()
        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onPromptSubmitted()

        testee.onStop(lifecycleOwner)

        val expectedParams = mapOf(
            "searches_in_session" to "3",
            "prompts_in_session" to "2",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = expectedParams,
        )
    }

    @Test
    fun `when onStop called multiple times with feature enabled then counters reset after each call`() {
        showInputScreenFlow.value = true

        // First session
        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        val firstSessionParams = mapOf(
            "searches_in_session" to "1",
            "prompts_in_session" to "1",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = firstSessionParams,
        )

        // Second session - counters should be reset
        testee.onSearchSubmitted()
        testee.onStop(lifecycleOwner)

        val secondSessionParams = mapOf(
            "searches_in_session" to "1",
            "prompts_in_session" to "0",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = secondSessionParams,
        )
    }

    @Test
    fun `when counters increment but onStop never called then no pixel fired`() {
        showInputScreenFlow.value = true

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when thread safety is required then atomic counters handle concurrent access`() {
        showInputScreenFlow.value = true

        // Simulate concurrent access
        val threads = mutableListOf<Thread>()

        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(5) {
                    if (threadIndex % 2 == 0) {
                        testee.onSearchSubmitted()
                    } else {
                        testee.onPromptSubmitted()
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        testee.onStop(lifecycleOwner)

        // Should have 25 searches (5 even threads * 5 calls each) and 25 prompts (5 odd threads * 5 calls each)
        val expectedParams = mapOf(
            "searches_in_session" to "25",
            "prompts_in_session" to "25",
        )
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = expectedParams,
        )
    }
}
