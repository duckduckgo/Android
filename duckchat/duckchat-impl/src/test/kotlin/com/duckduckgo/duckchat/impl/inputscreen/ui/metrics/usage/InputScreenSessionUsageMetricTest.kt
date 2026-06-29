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
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class InputScreenSessionUsageMetricTest {

    private val pixel: Pixel = mock()
    private val duckChatPixels: DuckChatPixels = mock()
    private val duckAiFeatureState: DuckAiFeatureState = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val nativeInputFieldEnabledFlow = MutableStateFlow(true)

    private lateinit var testee: InputScreenSessionUsageMetricImpl

    @Before
    fun setup() {
        whenever(duckAiFeatureState.nativeInputFieldEnabled).thenReturn(nativeInputFieldEnabledFlow)
        testee = InputScreenSessionUsageMetricImpl(pixel, duckAiFeatureState, duckChatPixels)
    }

    @Test
    fun `when onStop called with feature enabled and no searches or prompts then pixel fired with zero counts`() {
        nativeInputFieldEnabledFlow.value = true

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
        nativeInputFieldEnabledFlow.value = false

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when onSearchSubmitted called once and feature enabled then counter increments`() {
        nativeInputFieldEnabledFlow.value = true

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
        nativeInputFieldEnabledFlow.value = true

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
        nativeInputFieldEnabledFlow.value = true

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
        nativeInputFieldEnabledFlow.value = true

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
        nativeInputFieldEnabledFlow.value = true

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when thread safety is required then atomic counters handle concurrent access`() {
        nativeInputFieldEnabledFlow.value = true

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

    @Test
    fun `when native input enabled and both modes used then both-modes pixel fired once and summary on stop`() = runTest {
        nativeInputFieldEnabledFlow.value = true

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()

        verify(duckChatPixels).fireOmnibarSessionBothModes()

        testee.onStop(lifecycleOwner)
        verify(pixel).enqueueFire(
            pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_SUMMARY,
            parameters = mapOf("searches_in_session" to "1", "prompts_in_session" to "1"),
        )
    }

    @Test
    fun `when both modes used in one session then both-modes fires once only`() = runTest {
        nativeInputFieldEnabledFlow.value = true

        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onSearchSubmitted()
        testee.onPromptSubmitted()

        verify(duckChatPixels).fireOmnibarSessionBothModes()
    }

    @Test
    fun `when only search submitted then both-modes pixel not fired`() = runTest {
        nativeInputFieldEnabledFlow.value = true

        testee.onSearchSubmitted()
        testee.onStop(lifecycleOwner)

        verifyNoInteractions(duckChatPixels)
    }

    @Test
    fun `when only prompt submitted then both-modes pixel not fired`() = runTest {
        nativeInputFieldEnabledFlow.value = true

        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        verifyNoInteractions(duckChatPixels)
    }

    @Test
    fun `when both modes used across two sessions then both-modes fires once per session`() = runTest {
        nativeInputFieldEnabledFlow.value = true

        // First session
        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        // Second session - bothModesFired should be reset
        testee.onSearchSubmitted()
        testee.onPromptSubmitted()
        testee.onStop(lifecycleOwner)

        // Should be called exactly twice (once per session)
        verify(duckChatPixels, times(2)).fireOmnibarSessionBothModes()
    }
}
