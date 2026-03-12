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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.aihistorysearch.impl.EmbeddingScorer
import com.duckduckgo.history.api.HistoryEntry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Embedding quality evaluation harness.
 *
 * Corpus loaded from eval_corpus.json (src/sharedTest/resources) — same source of truth
 * as [Bm25ScorerEvalTest], so results are directly comparable side-by-side. Requires a
 * real device or emulator because MediaPipe's TextEmbedder uses TFLite native inference.
 *
 * Run:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.EmbeddingScorerEvalTest
 *
 * Filter logcat:
 *   adb logcat -s EmbeddingEval
 *
 * Expected semantic advantage over BM25:
 *   BM25 — semantic MRR ≈ 0.0   (only partial keyword accidents)
 *   USE  — semantic MRR ≥ 0.50  (should resolve conceptual queries)
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingScorerEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scorer = EmbeddingScorer(context)
    private val corpus: EvalCorpus by lazy {
        EvalCorpusLoader.load(context.assets.open("eval_corpus.json"))
    }

    @Test
    fun embeddingQualityReport() {
        val results = corpus.queries.map { q ->
            val ranked = scorer.rank(q.query, corpus.entries)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }
        printTable(results)
        assertFloors(results)
    }

    /** Observation run on the real-sites corpus — no floor assertions. */
    @Test
    fun embeddingQualityReportReal() {
        val realCorpus = EvalCorpusLoader.load(context.assets.open("eval_corpus_real.json"))
        val results = realCorpus.queries.map { q ->
            val ranked = scorer.rank(q.query, realCorpus.entries)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }
        printTable(results)
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<Triple<EvalQuery, Double, Double>>) {
        val header = "\n=== Embedding Quality Eval (Precision@5 / MRR) ==="
        val col = "%-40s %-14s %5s %5s"
        val row = "%-40s %-14s %5.2f %5.2f"
        val divider = "─".repeat(70)

        val lines = buildList {
            add(header)
            add(col.format("Query", "Category", "P@5", "MRR"))
            add(divider)
            results.forEach { (q, p5, mrrVal) ->
                add(row.format(q.query.take(40), q.category.take(14), p5, mrrVal))
            }
            add(divider)
            val avgP5 = results.map { it.second }.average()
            val avgMrr = results.map { it.third }.average()
            add(row.format("Overall avg", "", avgP5, avgMrr))
            for (cat in listOf("keyword", "semantic", "body-only", "negative")) {
                val sub = results.filter { it.first.category == cat }
                if (sub.isNotEmpty()) {
                    add(
                        row.format(
                            "${cat.replaceFirstChar { it.uppercase() }} avg", "",
                            sub.map { it.second }.average(),
                            sub.map { it.third }.average(),
                        ),
                    )
                }
            }
        }
        val table = lines.joinToString("\n")
        android.util.Log.i("EmbeddingEval", table)
        println(table)
    }

    // ── Floor thresholds ──────────────────────────────────────────────────────

    private fun assertFloors(results: List<Triple<EvalQuery, Double, Double>>) {
        val keywordMrr = results.filter { it.first.category == "keyword" }.map { it.third }.average()
        val semanticMrr = results.filter { it.first.category == "semantic" }.map { it.third }.average()
        val bodyOnlyMrr = results.filter { it.first.category == "body-only" }.map { it.third }.average()

        assertTrue(
            "Keyword MRR floor violated: expected ≥ 0.60, got %.2f".format(keywordMrr),
            keywordMrr >= 0.60,
        )
        assertTrue(
            "Semantic MRR floor violated: expected ≥ 0.25 (USE Lite beats BM25's ~0.17 on harder corpus), got %.2f".format(semanticMrr),
            semanticMrr >= 0.25,
        )
        assertTrue(
            "Body-only MRR floor violated: expected ≥ 0.60 (chunkText must be indexed), got %.2f".format(bodyOnlyMrr),
            bodyOnlyMrr >= 0.60,
        )
    }
}
