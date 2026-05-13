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

package com.duckduckgo.duckchat.api.nativeinput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputStateTest {

    @Test
    fun whenZeroThenInputModeIsSearchOnly() {
        assertEquals(NativeInputState.InputMode.SEARCH_ONLY, NativeInputState.zero().inputMode)
    }

    @Test
    fun whenZeroThenInputContextIsBrowser() {
        assertEquals(NativeInputState.InputContext.BROWSER, NativeInputState.zero().inputContext)
    }

    @Test
    fun whenZeroThenInputPositionIsTop() {
        assertEquals(NativeInputState.InputPosition.TOP, NativeInputState.zero().inputPosition)
    }

    @Test
    fun whenZeroThenSelectedModelIdIsNull() {
        assertNull(NativeInputState.zero().selectedModelId)
    }

    @Test
    fun whenZeroThenChatIdIsNull() {
        assertNull(NativeInputState.zero().chatId)
    }

    @Test
    fun whenCopyingWithChatIdThenValueIsPreserved() {
        val state = NativeInputState.zero().copy(chatId = "chat-42")
        assertEquals("chat-42", state.chatId)
    }

    @Test
    fun whenZeroThenFileRefsIsEmpty() {
        assertTrue(NativeInputState.zero().fileRefs.isEmpty())
    }

    @Test
    fun whenCopyingWithSelectedModelIdThenValueIsPreserved() {
        val state = NativeInputState.zero().copy(selectedModelId = "claude-3")
        assertEquals("claude-3", state.selectedModelId)
    }

    @Test
    fun whenCopyingWithFileRefsThenValueIsPreserved() {
        val state = NativeInputState.zero().copy(fileRefs = listOf("uuid-1", "uuid-2"))
        assertEquals(listOf("uuid-1", "uuid-2"), state.fileRefs)
    }

    @Test
    fun whenToggleVisibleAndSearchOnlyModeThenToggleNotVisible() {
        val state = NativeInputState.zero() // SEARCH_ONLY
        assertTrue(!state.toggleVisible)
    }
}
