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

package com.duckduckgo.duckchat.impl.ui.inputscreen.tabattachments

import com.duckduckgo.duckchat.impl.inputscreen.ui.tabattachments.TagDetector
import com.duckduckgo.duckchat.impl.inputscreen.ui.tabattachments.TagQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagDetectorTest {

    @Test
    fun `when text has @ at start then returns tag query`() {
        val result = TagDetector.detect("@duck", 5)
        assertEquals(TagQuery(atIndex = 0, query = "duck"), result)
    }

    @Test
    fun `when text has @ after whitespace then returns tag query`() {
        val result = TagDetector.detect("hello @duck", 11)
        assertEquals(TagQuery(atIndex = 6, query = "duck"), result)
    }

    @Test
    fun `when cursor is right after @ then returns empty query`() {
        val result = TagDetector.detect("@", 1)
        assertEquals(TagQuery(atIndex = 0, query = ""), result)
    }

    @Test
    fun `when @ is inside a word then returns null`() {
        val result = TagDetector.detect("email@example", 13)
        assertNull(result)
    }

    @Test
    fun `when query starts with space then returns null`() {
        val result = TagDetector.detect("@ duck", 6)
        assertNull(result)
    }

    @Test
    fun `when no @ in text then returns null`() {
        val result = TagDetector.detect("hello duck", 10)
        assertNull(result)
    }

    @Test
    fun `when cursor is at 0 then returns null`() {
        val result = TagDetector.detect("@duck", 0)
        assertNull(result)
    }

    @Test
    fun `when cursor position exceeds text length then returns null`() {
        val result = TagDetector.detect("@duck", 10)
        assertNull(result)
    }

    @Test
    fun `when multiple @ signs then uses last one before cursor`() {
        val result = TagDetector.detect("@first @second", 14)
        assertEquals(TagQuery(atIndex = 7, query = "second"), result)
    }

    @Test
    fun `when cursor is between two @ signs then uses the one before cursor`() {
        val result = TagDetector.detect("@first @second", 6)
        assertEquals(TagQuery(atIndex = 0, query = "first"), result)
    }

    @Test
    fun `when @ preceded by newline then returns tag query`() {
        val result = TagDetector.detect("hello\n@duck", 11)
        assertEquals(TagQuery(atIndex = 6, query = "duck"), result)
    }

    @Test
    fun `when @ preceded by tab then returns tag query`() {
        val result = TagDetector.detect("hello\t@duck", 11)
        assertEquals(TagQuery(atIndex = 6, query = "duck"), result)
    }

    @Test
    fun `when empty text then returns null`() {
        val result = TagDetector.detect("", 0)
        assertNull(result)
    }
}
