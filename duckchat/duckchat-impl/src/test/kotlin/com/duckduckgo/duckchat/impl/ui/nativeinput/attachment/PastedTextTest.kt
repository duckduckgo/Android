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

package com.duckduckgo.duckchat.impl.ui.nativeinput.attachment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedTextTest {

    @Test
    fun `insertion below the threshold stays inline`() {
        assertFalse(PromptLimits.convertsToPastedText("a".repeat(3_999)))
    }

    @Test
    fun `insertion at the threshold converts`() {
        assertTrue(PromptLimits.convertsToPastedText("a".repeat(4_000)))
    }

    @Test
    fun `blank insertion never converts`() {
        assertFalse(PromptLimits.convertsToPastedText(" \n\t".repeat(2_000)))
    }

    @Test
    fun `threshold counts UTF-16 code units`() {
        assertTrue(PromptLimits.convertsToPastedText("😀".repeat(2_000)))
    }

    @Test
    fun `count is typed length plus every paste`() {
        val length = PromptLimits.evaluate(
            typedLength = 10,
            pastedTexts = listOf(PastedText("1", "a".repeat(4_000)), PastedText("2", "b".repeat(5_000))),
            hasImage = false,
            maxCharactersWithImage = 4_500,
        )

        assertEquals(PromptLength(count = 9_010, limit = 16_000), length)
    }

    @Test
    fun `an image lowers the limit to the model limit`() {
        val length = PromptLimits.evaluate(typedLength = 0, pastedTexts = emptyList(), hasImage = true, maxCharactersWithImage = 4_500)

        assertEquals(4_500, length.limit)
    }

    @Test
    fun `an image never raises the limit`() {
        val length = PromptLimits.evaluate(typedLength = 0, pastedTexts = emptyList(), hasImage = true, maxCharactersWithImage = 50_000)

        assertEquals(16_000, length.limit)
    }

    @Test
    fun `at the limit is allowed and one over is not`() {
        assertFalse(PromptLength(count = 16_000, limit = 16_000).isOverLimit)
        assertTrue(PromptLength(count = 16_001, limit = 16_000).isOverLimit)
    }

    @Test
    fun `title is the first non-blank line trimmed`() {
        assertEquals("Hello world", PastedText("1", "\n  \r\n   Hello world  \nsecond").title)
    }

    @Test
    fun `title is empty when every line is blank`() {
        assertEquals("", PastedText("1", " \n\t\n").title)
    }

    @Test
    fun `length is UTF-16 code units of the untrimmed content`() {
        assertEquals(4, PastedText("1", " 😀 ").length)
    }
}
