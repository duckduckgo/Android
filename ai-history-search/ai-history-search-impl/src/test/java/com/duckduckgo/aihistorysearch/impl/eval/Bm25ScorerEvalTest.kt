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
 * Runs all corpus queries, prints a human-readable table (visible with --info),
 * and asserts floor thresholds that prevent silent regressions.
 *
 * Semantic queries are expected to score 0.0 — this is not a failure; it is the point
 * of the comparison and documents the known BM25 limitation vs. embeddings.
 *
 * Corpus lives in src/sharedTest/resources/eval_corpus.json — single source of truth
 * shared with the androidTest embedding and Gemma evals.
 *
 * Run:
 *   ./gradlew :ai-history-search-impl:testDebugUnitTest --info 2>&1 | grep -A 50 "BM25 Quality Eval"
 */
@RunWith(AndroidJUnit4::class)
class Bm25ScorerEvalTest {

    private val corpus: EvalCorpus = EvalCorpusLoader.load(
        checkNotNull(javaClass.getResourceAsStream("/eval_corpus.json")) {
            "eval_corpus.json not found — check sharedTest resources configuration"
        },
    )

    @Test
    fun `bm25 quality report`() {
        val results: List<Triple<EvalQuery, Double, Double>> = corpus.queries.map { q ->
            val ranked: List<HistoryEntry.VisitedPage> = Bm25Scorer.rank(q.query, corpus.entries)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }

        printTable(results)
        assertFloors(results)
    }

    /** Observation run on the real-sites corpus — no floor assertions. */
    @Test
    fun `bm25 quality report real corpus`() {
        val realCorpus = EvalCorpusLoader.load(
            checkNotNull(javaClass.getResourceAsStream("/eval_corpus_real.json")) {
                "eval_corpus_real.json not found"
            },
        )
        val results: List<Triple<EvalQuery, Double, Double>> = realCorpus.queries.map { q ->
            val ranked: List<HistoryEntry.VisitedPage> = Bm25Scorer.rank(q.query, realCorpus.entries)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }
        printTable(results)
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<Triple<EvalQuery, Double, Double>>) {
        val header = "\n=== BM25 Quality Eval (Precision@5 / MRR) ==="
        val col = "%-40s %-14s %5s %5s"
        val row = "%-40s %-14s %5.2f %5.2f"
        val divider = "─".repeat(70)

        println(header)
        println(col.format("Query", "Category", "P@5", "MRR"))
        println(divider)

        results.forEach { (q, p5, mrrVal) ->
            val note = when {
                q.category == "semantic" -> "  ← expected BM25 blind spot"
                q.category == "negative" -> "  (negative query)"
                else -> ""
            }
            println(row.format(q.query.take(40), q.category.take(14), p5, mrrVal) + note)
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

    private fun assertFloors(results: List<Triple<EvalQuery, Double, Double>>) {
        val keywordMrr = results.filter { it.first.category == "keyword" }.map { it.third }.average()
        val bodyOnlyMrr = results.filter { it.first.category == "body-only" }.map { it.third }.average()
        val negativeP5 = results.filter { it.first.category == "negative" }.map { it.second }.average()

        assertTrue(
            "Keyword MRR floor violated: expected ≥ 0.60 (BM25 must rank keyword matches at top), got %.2f".format(keywordMrr),
            keywordMrr >= 0.60,
        )
        assertTrue(
            "Body-only MRR floor violated: expected ≥ 0.60 (chunkText must be indexed), got %.2f".format(bodyOnlyMrr),
            bodyOnlyMrr >= 0.60,
        )
        assertEquals(
            "Negative P@5 must be 1.00 (no false positives for unrelated query), got %.2f".format(negativeP5),
            1.00,
            negativeP5,
            0.001,
        )
    }
}
