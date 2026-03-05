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

package com.duckduckgo.aihistorysearch.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMetadataJsPluginTest {

    // --- happy path ---

    @Test
    fun `returns description and h1 when both are present`() {
        val result = parseMetadata("""{"description":"A page about ducks","h1":"Ducks"}""")
        assertEquals(Pair("A page about ducks", "Ducks"), result)
    }

    @Test
    fun `returns description only when h1 is null`() {
        val result = parseMetadata("""{"description":"Only a description","h1":null}""")
        assertEquals(Pair("Only a description", null), result)
    }

    @Test
    fun `returns h1 only when description is null`() {
        val result = parseMetadata("""{"description":null,"h1":"Only an H1"}""")
        assertEquals(Pair(null, "Only an H1"), result)
    }

    @Test
    fun `handles values with special characters`() {
        val result = parseMetadata("""{"description":"Line1\nLine2","h1":"Title & More"}""")
        assertEquals(Pair("Line1\nLine2", "Title & More"), result)
    }

    @Test
    fun `handles unicode values`() {
        val result = parseMetadata("""{"description":"\u00e9l\u00e8ve","h1":"\u4e2d\u6587"}""")
        assertEquals(Pair("élève", "中文"), result)
    }

    // --- null / empty returns ---

    @Test
    fun `returns null when both fields are null`() {
        val result = parseMetadata("""{"description":null,"h1":null}""")
        assertNull(result)
    }

    @Test
    fun `returns null when both fields are blank strings`() {
        val result = parseMetadata("""{"description":"   ","h1":""}""")
        assertNull(result)
    }

    @Test
    fun `returns null for literal null JSON`() {
        val result = parseMetadata("null")
        assertNull(result)
    }

    // --- error handling ---

    @Test
    fun `returns null for malformed JSON`() {
        val result = parseMetadata("not-json")
        assertNull(result)
    }

    @Test
    fun `returns null for empty string`() {
        val result = parseMetadata("")
        assertNull(result)
    }
}
