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

package com.duckduckgo.duckchat.impl.ui

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.ui.nativeinput.views.chatHintRes
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeInputModeWidgetChatHintTest {

    @Test
    fun `new chat shows the ask anything hint when no chat id`() {
        val state = stateOf(InputContext.DUCK_AI, chatId = null)

        assertEquals(R.string.native_input_chat_hint, state.chatHintRes())
    }

    @Test
    fun `existing chat shows the reply hint when chat id present`() {
        val state = stateOf(InputContext.DUCK_AI, chatId = "chat-123")

        assertEquals(R.string.native_input_chat_duck_mode_hint, state.chatHintRes())
    }

    @Test
    fun `duck ai page context alone does not switch to the reply hint`() {
        assertEquals(R.string.native_input_chat_hint, stateOf(InputContext.DUCK_AI, chatId = null).chatHintRes())
        assertEquals(R.string.native_input_chat_hint, stateOf(InputContext.DUCK_AI_CONTEXTUAL, chatId = null).chatHintRes())
    }

    @Test
    fun `browser context with a chat id still shows the reply hint`() {
        val state = stateOf(InputContext.BROWSER, chatId = "chat-123")

        assertEquals(R.string.native_input_chat_duck_mode_hint, state.chatHintRes())
    }

    private fun stateOf(inputContext: InputContext, chatId: String?): NativeInputState =
        NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = inputContext,
            chatId = chatId,
        )
}
