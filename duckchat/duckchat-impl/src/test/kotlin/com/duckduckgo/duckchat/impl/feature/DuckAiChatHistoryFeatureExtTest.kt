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

package com.duckduckgo.duckchat.impl.feature

import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DuckAiChatHistoryFeatureExtTest {

    private val toggle: Toggle = mock()
    private val feature: DuckAiChatHistoryFeature = mock<DuckAiChatHistoryFeature>().also {
        whenever(it.self()).thenReturn(toggle)
    }

    @Test
    fun `maxUrlSuggestions returns parsed value when settings present`() {
        whenever(toggle.getSettings()).thenReturn("""{"maxUrlSuggestions": 7}""")
        assertEquals(7, feature.maxUrlSuggestions())
    }

    @Test
    fun `maxUrlSuggestions returns default when key missing`() {
        whenever(toggle.getSettings()).thenReturn("""{"other":1}""")
        assertEquals(3, feature.maxUrlSuggestions())
    }

    @Test
    fun `maxUrlSuggestions returns default when settings null`() {
        whenever(toggle.getSettings()).thenReturn(null)
        assertEquals(3, feature.maxUrlSuggestions())
    }

    @Test
    fun `maxUrlSuggestions returns default when settings malformed`() {
        whenever(toggle.getSettings()).thenReturn("not-json")
        assertEquals(3, feature.maxUrlSuggestions())
    }

    @Test
    fun `maxHistoryCount returns parsed value when settings present`() {
        whenever(toggle.getSettings()).thenReturn("""{"maxHistoryCount": 25}""")
        assertEquals(25, feature.maxHistoryCount())
    }

    @Test
    fun `maxHistoryCount returns default when key missing`() {
        whenever(toggle.getSettings()).thenReturn("""{"other":1}""")
        assertEquals(10, feature.maxHistoryCount())
    }

    @Test
    fun `maxHistoryCount returns default when settings null`() {
        whenever(toggle.getSettings()).thenReturn(null)
        assertEquals(10, feature.maxHistoryCount())
    }

    @Test
    fun `maxHistoryCount returns default when settings malformed`() {
        whenever(toggle.getSettings()).thenReturn("{nope")
        assertEquals(10, feature.maxHistoryCount())
    }
}
