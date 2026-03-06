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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.aihistorysearch.impl.Bm25Scorer
import com.duckduckgo.history.api.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BM25 quality evaluation harness.
 *
 * Runs all 10 corpus queries, prints a human-readable table (visible with --info),
 * and asserts floor thresholds that prevent silent regressions.
 *
 * Semantic queries are expected to score 0.0 — this is not a failure; it is the point
 * of the comparison and documents the known BM25 limitation vs. embeddings.
 *
 * Run:
 *   ./gradlew :ai-history-search-impl:testDebugUnitTest --info 2>&1 | grep -A 25 "BM25 Quality Eval"
 */
@RunWith(AndroidJUnit4::class)
class Bm25ScorerEvalTest {

    @Test
    fun `bm25 quality report`() {
        val results: List<Triple<EvalQuery, Double, Double>> = SearchEvalCorpus.QUERIES.map { q ->
            val ranked: List<HistoryEntry.VisitedPage> = Bm25Scorer.rank(q.query, SearchEvalCorpus.ENTRIES)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }

        printTable(results)
        assertFloors(results)
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<Triple<EvalQuery, Double, Double>>) {
        val header = "\n=== BM25 Quality Eval (Precision@5 / MRR) ==="
        val col = "%-34s %-14s %5s %5s"
        val row = "%-34s %-14s %5.2f %5.2f"
        val divider = "─".repeat(64)

        println(header)
        println(col.format("Query", "Category", "P@5", "MRR"))
        println(divider)

        results.forEach { (q, p5, mrrVal) ->
            val note = when {
                q.category == "semantic" -> "  ← expected BM25 blind spot"
                q.category == "negative" -> "  (negative query)"
                else -> ""
            }
            println(row.format(q.query.take(34), q.category.take(14), p5, mrrVal) + note)
        }

        println(divider)

        val avgP5 = results.map { it.second }.average()
        val avgMrr = results.map { it.third }.average()
        println(row.format("Overall avg", "", avgP5, avgMrr))

        for (cat in listOf("keyword", "semantic", "body-only", "negative")) {
            val sub = results.filter { it.first.category == cat }
            if (sub.isNotEmpty()) {
                val label = "${cat.replaceFirstChar { it.uppercase() }} avg"
                println(row.format(label, "", sub.map { it.second }.average(), sub.map { it.third }.average()))
            }
        }
    }

    // ── Floor thresholds ──────────────────────────────────────────────────────
    //
    // P@5 is a weak signal here because each keyword query has only 1 relevant URL — so the
    // maximum achievable P@5 is 1/5 = 0.20 even when the relevant item ranks first. MRR is the
    // right primary signal: it measures whether BM25 can surface the relevant item at the top.
    //
    // Thresholds guard three distinct regressions:
    //   keyword MRR  — BM25 stops finding exact keyword matches in title/URL
    //   body-only MRR — chunkText is no longer indexed (body-only queries stop working)
    //   negative P@5  — BM25 starts surfacing irrelevant results for unrelated queries

    private fun assertFloors(results: List<Triple<EvalQuery, Double, Double>>) {
        val keywordMrr = results.filter { it.first.category == "keyword" }.map { it.third }.average()
        val bodyOnlyMrr = results.filter { it.first.category == "body-only" }.map { it.third }.average()
        val negativeP5 = results.filter { it.first.category == "negative" }.map { it.second }.average()

        assertTrue(
            "Keyword MRR floor violated: expected ≥ 0.60 (BM25 must rank keyword matches at top), got %.2f".format(keywordMrr),
            keywordMrr >= 0.60,
        )
        assertTrue(
            "Body-only MRR floor violated: expected ≥ 0.80 (chunkText must be indexed), got %.2f".format(bodyOnlyMrr),
            bodyOnlyMrr >= 0.80,
        )
        assertEquals(
            "Negative P@5 must be 1.00 (no false positives for unrelated query), got %.2f".format(negativeP5),
            1.00,
            negativeP5,
            0.001,
        )
    }
}
