/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.impl.pixel.DuckAiFeatureStateReporterPlugin.Companion.DUCK_AI_CONTEXTUAL
import com.duckduckgo.duckchat.impl.pixel.DuckAiFeatureStateReporterPlugin.Companion.DUCK_AI_INPUT_MODE
import com.duckduckgo.duckchat.impl.pixel.DuckAiFeatureStateReporterPlugin.Companion.DUCK_AI_NATIVE_INPUT
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuckAiFeatureStateReporterPluginTest {

    private val inputModeCapability = MutableStateFlow(NativeInputState.InputMode.SEARCH_ONLY)
    private val nativeInputFieldEnabled = MutableStateFlow(false)
    private val showContextualMode = MutableStateFlow(false)

    private val duckAiFeatureState: DuckAiFeatureState = mock<DuckAiFeatureState>().also {
        whenever(it.nativeInputFieldEnabled).thenReturn(nativeInputFieldEnabled)
        whenever(it.showContextualMode).thenReturn(showContextualMode)
    }

    private val duckChatInputModeState: DuckChatInputModeState = mock<DuckChatInputModeState>().also {
        whenever(it.inputModeCapability).thenReturn(inputModeCapability)
    }

    private val testee = DuckAiFeatureStateReporterPlugin(duckAiFeatureState, duckChatInputModeState)

    @Test
    fun `when search only and nothing enabled then params report the off state`() {
        assertEquals(
            mapOf(
                DUCK_AI_INPUT_MODE to "search_only",
                DUCK_AI_NATIVE_INPUT to "0",
                DUCK_AI_CONTEXTUAL to "0",
            ),
            testee.featureStateParams(),
        )
    }

    @Test
    fun `when everything enabled then params report the on state`() {
        inputModeCapability.value = NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        nativeInputFieldEnabled.value = true
        showContextualMode.value = true

        assertEquals(
            mapOf(
                DUCK_AI_INPUT_MODE to "search_and_duck_ai",
                DUCK_AI_NATIVE_INPUT to "1",
                DUCK_AI_CONTEXTUAL to "1",
            ),
            testee.featureStateParams(),
        )
    }

    @Test
    fun `when mode is search and duck ai then the mode param says so`() {
        inputModeCapability.value = NativeInputState.InputMode.SEARCH_AND_DUCK_AI

        val params = testee.featureStateParams()

        assertEquals("search_and_duck_ai", params[DUCK_AI_INPUT_MODE])
        assertEquals("0", params[DUCK_AI_NATIVE_INPUT])
        assertEquals("0", params[DUCK_AI_CONTEXTUAL])
    }

    @Test
    fun `when state changes then params reflect the latest values`() {
        inputModeCapability.value = NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        assertEquals("search_and_duck_ai", testee.featureStateParams()[DUCK_AI_INPUT_MODE])

        inputModeCapability.value = NativeInputState.InputMode.SEARCH_ONLY
        assertEquals("search_only", testee.featureStateParams()[DUCK_AI_INPUT_MODE])
    }

    @Test
    fun `when the input surface changes the mode is unaffected`() {
        inputModeCapability.value = NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        nativeInputFieldEnabled.value = true

        val params = testee.featureStateParams()

        assertEquals("search_and_duck_ai", params[DUCK_AI_INPUT_MODE])
        assertEquals("1", params[DUCK_AI_NATIVE_INPUT])
    }

    @Test
    fun `params contain exactly the three Duck ai keys`() {
        assertEquals(
            setOf(DUCK_AI_INPUT_MODE, DUCK_AI_NATIVE_INPUT, DUCK_AI_CONTEXTUAL),
            testee.featureStateParams().keys,
        )
    }
}
