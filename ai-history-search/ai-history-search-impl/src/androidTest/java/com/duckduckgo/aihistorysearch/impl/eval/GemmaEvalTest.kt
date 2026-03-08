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
import com.duckduckgo.aihistorysearch.impl.GemmaSearcher
import com.duckduckgo.history.api.HistoryEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gemma 3 1B IT answer-quality evaluation harness.
 *
 * Corpus loaded from eval_corpus.json (src/sharedTest/resources) — same source of truth
 * as [Bm25ScorerEvalTest] and [EmbeddingScorerEvalTest], so all three ranking paths are
 * directly comparable side-by-side.
 *
 * Requires the Gemma model in the app's private files dir:
 *   adb shell run-as com.duckduckgo.mobile.android.debug ls files/models/
 *   → expected: gemma3-1b-it-int4.task
 * If absent, all tests are SKIPPED (not failed).
 *
 * Run:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.GemmaEvalTest
 *
 * Filter logcat:
 *   adb logcat -s GemmaEval
 *
 * Ranking is derived by scanning the raw response text for the first mention of each entry's
 * URL or title prefix. Entries mentioned earlier rank higher; unreferenced entries are excluded.
 *
 * Answer quality (SIM) is measured as cosine similarity between the Gemma response and a
 * reference answer written per query. Both texts are embedded with USE Lite. A score near 1.0
 * means the response covers the same ground as the reference; near 0.7 means near-random for
 * English (USE's baseline). Negative queries have no reference answer and are excluded from SIM.
 */
@RunWith(AndroidJUnit4::class)
class GemmaEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val searcher = GemmaSearcher(context)
    private val embeddingScorer = EmbeddingScorer(context)
    private val corpus: EvalCorpus by lazy {
        EvalCorpusLoader.load(context.assets.open("eval_corpus.json"))
    }

    @Test
    fun gemmaQualityReport() {
        val modelFile = File(context.filesDir, "models/${GemmaSearcher.MODEL_FILENAME}")
        // Allow pre-staging the model at /data/local/tmp/ (no run-as required) for library
        // module androidTests where the test APK's filesDir can't be pre-populated via adb.
        if (!modelFile.exists()) {
            val staged = File("/data/local/tmp/${GemmaSearcher.MODEL_FILENAME}")
            if (staged.exists()) {
                modelFile.parentFile?.mkdirs()
                staged.copyTo(modelFile)
            }
        }
        assumeTrue(
            "Gemma model not found at ${modelFile.absolutePath} (also checked /data/local/tmp/) — skipping eval. " +
                "Push with: adb push gemma3-1b-it-int4.task /data/local/tmp/",
            modelFile.exists() && modelFile.length() > 100 * 1024 * 1024L,
        )

        val results = corpus.queries.map { q ->
            val response = runBlocking { searcher.searchForEval(q.query, corpus.entries) }
            if (response == null) {
                android.util.Log.w("GemmaEval", "No response for query '${q.query}' — model load failed")
            }
            val ranked = if (response != null) parseRankedEntries(response, corpus.entries) else emptyList()
            val sim = if (response != null && q.referenceAnswer != null) {
                embeddingScorer.cosineSimilarity(response, q.referenceAnswer)
            } else {
                null
            }
            GemmaResult(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls), response ?: "<model unavailable>", sim)
        }

        printTable(results)
        printResponses(results)
        assertFloors(results)
    }

    // ── Ranking ───────────────────────────────────────────────────────────────

    private fun parseRankedEntries(
        response: String,
        entries: List<HistoryEntry.VisitedPage>,
    ): List<HistoryEntry.VisitedPage> {
        val lower = response.lowercase()
        return entries
            .map { entry ->
                val urlPos = lower.indexOf(entry.url.toString().lowercase())
                val titlePos = lower.indexOf(entry.title.lowercase().take(20))
                val firstPos = listOf(urlPos, titlePos).filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE
                entry to firstPos
            }
            .filter { (_, pos) -> pos < Int.MAX_VALUE }
            .sortedBy { (_, pos) -> pos }
            .map { (entry, _) -> entry }
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private data class GemmaResult(
        val query: EvalQuery,
        val p5: Double,
        val mrr: Double,
        val response: String,
        /** Cosine similarity between response and referenceAnswer; null for negative queries. */
        val sim: Double?,
    )

    private fun printTable(results: List<GemmaResult>) {
        val header = "\n=== Gemma Quality Eval (Precision@5 / MRR / Answer Similarity) ==="
        val col = "%-40s %-14s %5s %5s %5s"
        val row = "%-40s %-14s %5.2f %5.2f"
        val rowSim = "%-40s %-14s %5.2f %5.2f %5.2f"
        val divider = "─".repeat(76)

        val lines = buildList {
            add(header)
            add(col.format("Query", "Category", "P@5", "MRR", "SIM"))
            add(divider)
            results.forEach { r ->
                if (r.sim != null) {
                    add(rowSim.format(r.query.query.take(40), r.query.category.take(14), r.p5, r.mrr, r.sim))
                } else {
                    add(row.format(r.query.query.take(40), r.query.category.take(14), r.p5, r.mrr) + "     —")
                }
            }
            add(divider)
            val avgP5 = results.map { it.p5 }.average()
            val avgMrr = results.map { it.mrr }.average()
            val avgSim = results.mapNotNull { it.sim }.average()
            add(rowSim.format("Overall avg", "", avgP5, avgMrr, avgSim))
            for (cat in listOf("keyword", "semantic", "body-only", "negative")) {
                val sub = results.filter { it.query.category == cat }
                if (sub.isNotEmpty()) {
                    val subAvgP5 = sub.map { it.p5 }.average()
                    val subAvgMrr = sub.map { it.mrr }.average()
                    val subAvgSim = sub.mapNotNull { it.sim }.let { if (it.isEmpty()) null else it.average() }
                    val label = "${cat.replaceFirstChar { it.uppercase() }} avg"
                    if (subAvgSim != null) {
                        add(rowSim.format(label, "", subAvgP5, subAvgMrr, subAvgSim))
                    } else {
                        add(row.format(label, "", subAvgP5, subAvgMrr) + "     —")
                    }
                }
            }
        }
        val table = lines.joinToString("\n")
        android.util.Log.i("GemmaEval", table)
        println(table)
    }

    private fun printResponses(results: List<GemmaResult>) {
        val divider = "═".repeat(70)
        val sb = StringBuilder("\n$divider\nGemma raw responses\n$divider\n")
        results.forEach { r ->
            sb.append("\nQuery: ${r.query.query}\n")
            sb.append("─".repeat(40)).append("\n")
            sb.append(r.response).append("\n")
            if (r.sim != null) {
                sb.append("(answer similarity: ${"%.3f".format(r.sim)})\n")
            }
        }
        sb.append(divider)
        android.util.Log.i("GemmaEval", sb.toString())
        println(sb)
    }

    // ── Floor thresholds ──────────────────────────────────────────────────────

    private fun assertFloors(results: List<GemmaResult>) {
        val keywordMrr = results.filter { it.query.category == "keyword" }.map { it.mrr }.average()
        val semanticMrr = results.filter { it.query.category == "semantic" }.map { it.mrr }.average()
        val avgSim = results.mapNotNull { it.sim }.average()

        assertTrue(
            "Keyword MRR floor violated: expected ≥ 0.60, got %.2f".format(keywordMrr),
            keywordMrr >= 0.60,
        )
        assertTrue(
            "Semantic MRR floor violated: expected ≥ 0.50 (Gemma should understand conceptual queries), got %.2f".format(semanticMrr),
            semanticMrr >= 0.50,
        )
        assertTrue(
            "Answer similarity floor violated: expected ≥ 0.72 (response should match reference), got %.2f".format(avgSim),
            avgSim >= 0.72,
        )
    }
}
