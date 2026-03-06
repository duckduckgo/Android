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

package com.duckduckgo.aihistorysearch.impl.eval

import com.duckduckgo.aihistorysearch.impl.Bm25Scorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Unit tests for [Bm25Scorer.tokenize]. No Android context required. */
@RunWith(JUnit4::class)
class Bm25ScorerTokenizeTest {

    @Test
    fun `stop words are removed`() {
        val tokens = Bm25Scorer.tokenize("the quick brown fox and the lazy dog")
        assertTrue("quick" in tokens)
        assertTrue("brown" in tokens)
        assertTrue("fox" in tokens)
        assertTrue("lazy" in tokens)
        assertTrue("dog" in tokens)
        assertTrue("the" !in tokens)
        assertTrue("and" !in tokens)
    }

    @Test
    fun `tokens shorter than 2 chars are removed`() {
        val tokens = Bm25Scorer.tokenize("a b c kotlin")
        assertTrue("kotlin" in tokens)
        assertTrue("a" !in tokens)
        assertTrue("b" !in tokens)
        assertTrue("c" !in tokens)
    }

    @Test
    fun `non-alphanumeric characters are stripped`() {
        val tokens = Bm25Scorer.tokenize("hello, world! it's kotlin.")
        assertTrue("hello" in tokens)
        assertTrue("world" in tokens)
        assertTrue("kotlin" in tokens)
        // punctuation-only fragments should not appear
        assertTrue("," !in tokens)
        assertTrue("." !in tokens)
        assertTrue("!" !in tokens)
    }

    @Test
    fun `url path is split into individual tokens`() {
        val tokens = Bm25Scorer.tokenize("kotlinlang.org/docs/coroutines")
        assertEquals(listOf("kotlinlang", "org", "docs", "coroutines"), tokens)
    }

    @Test
    fun `full url with scheme is split correctly`() {
        val tokens = Bm25Scorer.tokenize("https://kotlinlang.org/docs/coroutines")
        assertTrue("kotlinlang" in tokens)
        assertTrue("coroutines" in tokens)
        assertTrue("docs" in tokens)
        // "https" is not a stop word but that is fine — it carries no query signal
    }

    @Test
    fun `output is lowercased`() {
        val tokens = Bm25Scorer.tokenize("Kotlin COROUTINES Android")
        assertEquals(listOf("kotlin", "coroutines", "android"), tokens)
    }

    @Test
    fun `empty string returns empty list`() {
        val tokens = Bm25Scorer.tokenize("")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `query with only stop words returns empty list`() {
        // Exercises the queryTerms.isEmpty() guard in Bm25Scorer.rank()
        val tokens = Bm25Scorer.tokenize("the and or but")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `whitespace-only string returns empty list`() {
        val tokens = Bm25Scorer.tokenize("   ")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `hyphenated terms are split into separate tokens`() {
        val tokens = Bm25Scorer.tokenize("ball-python")
        assertTrue("ball" in tokens)
        assertTrue("python" in tokens)
    }
}
