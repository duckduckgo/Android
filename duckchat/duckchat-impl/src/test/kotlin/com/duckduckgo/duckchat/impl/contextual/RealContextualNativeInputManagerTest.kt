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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStatePublisher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RealContextualNativeInputManagerTest {

    private val duckChat: DuckChat = mock()
    private val publisher: NativeInputStatePublisher = mock()
    private val testee = RealContextualNativeInputManager(duckChat, publisher)

    @Test
    fun `when onContextualClosed called with blank tabId then publisher is not touched`() {
        testee.onContextualClosed("")

        verify(publisher, never()).update(any(), any())
    }

    @Test
    fun `when onContextualClosed called then publisher resets inputContext to browser`() {
        val tabId = "tab-1"
        val previous = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL,
            toggleSelection = NativeInputState.ToggleSelection.DUCK_AI,
        )

        testee.onContextualClosed(tabId)

        val captor = argumentCaptor<(NativeInputState) -> NativeInputState>()
        verify(publisher).update(eq(tabId), captor.capture())
        val updated = captor.firstValue.invoke(previous)

        assertEquals(NativeInputState.InputContext.BROWSER, updated.inputContext)
        assertEquals(NativeInputState.ToggleSelection.SEARCH, updated.toggleSelection)
    }

    @Test
    fun `when onContextualClosed called then inputMode is preserved`() {
        val tabId = "tab-1"
        val previous = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.DUCK_AI_CONTEXTUAL,
            toggleSelection = NativeInputState.ToggleSelection.DUCK_AI,
        )

        testee.onContextualClosed(tabId)

        val captor = argumentCaptor<(NativeInputState) -> NativeInputState>()
        verify(publisher).update(eq(tabId), captor.capture())
        val updated = captor.firstValue.invoke(previous)

        // inputMode is owned by the main widget VM; the reset must leave it alone.
        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, updated.inputMode)
    }
}
