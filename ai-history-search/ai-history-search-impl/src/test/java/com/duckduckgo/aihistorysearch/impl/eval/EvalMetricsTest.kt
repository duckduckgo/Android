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

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.history.api.HistoryEntry
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EvalMetricsTest {

    // ── precisionAtK ──────────────────────────────────────────────────────────

    @Test
    fun `precisionAtK is 1_0 when first result is the only relevant one`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com", "https://d.com", "https://e.com")
        val result = precisionAtK(ranked, setOf("https://a.com"), k = 5)
        assertEquals(0.2, result, 0.001)
    }

    @Test
    fun `precisionAtK is 1_0 when only result is relevant and k equals 1`() {
        val ranked = pages("https://a.com")
        val result = precisionAtK(ranked, setOf("https://a.com"), k = 1)
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `precisionAtK is 0_0 when no relevant results in top k`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com", "https://d.com", "https://e.com")
        val result = precisionAtK(ranked, setOf("https://z.com"), k = 5)
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `precisionAtK is 0_4 when 2 of 5 are relevant`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com", "https://d.com", "https://e.com")
        val result = precisionAtK(ranked, setOf("https://a.com", "https://c.com"), k = 5)
        assertEquals(0.4, result, 0.001)
    }

    @Test
    fun `precisionAtK considers only top k even if more results exist`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com", "https://d.com", "https://e.com", "https://rel.com")
        val result = precisionAtK(ranked, setOf("https://rel.com"), k = 5)
        assertEquals(0.0, result, 0.001)
    }

    // ── precisionAtK — negative queries ───────────────────────────────────────

    @Test
    fun `precisionAtK is 1_0 for negative query when ranked list is empty`() {
        val result = precisionAtK(emptyList(), emptySet(), k = 5)
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `precisionAtK is 0_0 for negative query when irrelevant entry surfaces`() {
        val ranked = pages("https://false-positive.com")
        val result = precisionAtK(ranked, emptySet(), k = 5)
        assertEquals(0.0, result, 0.001)
    }

    // ── mrr ───────────────────────────────────────────────────────────────────

    @Test
    fun `mrr is 1_0 when match is at rank 1`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com")
        val result = mrr(ranked, setOf("https://a.com"))
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `mrr is 0_5 when match is at rank 2`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com")
        val result = mrr(ranked, setOf("https://b.com"))
        assertEquals(0.5, result, 0.001)
    }

    @Test
    fun `mrr is 0_0 when relevant not found`() {
        val ranked = pages("https://a.com", "https://b.com", "https://c.com")
        val result = mrr(ranked, setOf("https://z.com"))
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `mrr is 1_0 for negative query regardless of ranked content`() {
        val result = mrr(emptyList(), emptySet())
        assertEquals(1.0, result, 0.001)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun pages(vararg urls: String): List<HistoryEntry.VisitedPage> =
        urls.map { url ->
            HistoryEntry.VisitedPage(
                url = Uri.parse(url),
                title = url,
                visits = listOf(LocalDateTime.now()),
            )
        }
}
