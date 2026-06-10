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

import com.duckduckgo.duckchat.impl.ui.nativeinput.views.shouldShowChatSuggestionsCoverOnSelect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputModeWidgetChatSuggestionsCoverTest {

    @Test
    fun `cover is shown when the input has text, regardless of chat history`() {
        // With text the fetch always yields content (at minimum the "Search for [query]" row), so the
        // cover is filled and stays - no flash.
        assertTrue(shouldShowChatSuggestionsCoverOnSelect(inputText = "weather", recentChatsExpected = false))
        assertTrue(shouldShowChatSuggestionsCoverOnSelect(inputText = "a", recentChatsExpected = false))
    }

    @Test
    fun `cover is shown when empty input is expected to yield recent chats`() {
        // Empty input but recent chats will load, so covering avoids briefly exposing the logo.
        assertTrue(shouldShowChatSuggestionsCoverOnSelect(inputText = "", recentChatsExpected = true))
    }

    @Test
    fun `cover is not shown when empty input yields no content`() {
        // Regression: covering an empty input with no chats meant the overlay was shown then cleared
        // ~200ms later, flashing the list and the NTP logo behind it on search -> Duck.ai.
        assertFalse(shouldShowChatSuggestionsCoverOnSelect(inputText = "", recentChatsExpected = false))
    }
}
