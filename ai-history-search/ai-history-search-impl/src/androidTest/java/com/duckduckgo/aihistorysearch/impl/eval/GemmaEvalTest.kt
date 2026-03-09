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
import com.duckduckgo.aihistorysearch.impl.Bm25Scorer
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
 * Two test variants, both skipped if the model is absent:
 *
 *   [gemmaFullCorpusQualityReport] — Gemma receives the full corpus (up to MAX_ENTRIES).
 *     Baseline: measures what the model can do with no pre-filtering.
 *
 *   [gemmaPreFilteredQualityReport] — Embeddings top-[PRE_FILTER_K] → Gemma.
 *     Production-realistic: mirrors how the feature would work in practice.
 *     Shorter prompt → faster inference; better signal-to-noise.
 *
 * Metrics:
 *   P@5 / MRR  — retrieval quality (URL/title match in response). These are imperfect
 *                for a generative model that may omit URLs; treat as directional only.
 *   SIM        — cosine similarity (USE Lite) between response and reference answer.
 *                The primary quality signal for a generative model.
 *
 * Floor assertion: avg SIM ≥ 0.72 (fires only if the model is truly broken).
 * MRR floors are intentionally omitted — they penalise correct abstention and
 * URL-omission, not actual answer quality.
 *
 * Corpus: eval_corpus.json (src/sharedTest/resources).
 *
 * Run a single variant:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.GemmaEvalTest#gemmaPreFilteredQualityReport
 *
 * Filter logcat:
 *   adb logcat -s GemmaEval      # tables + raw responses
 *   adb logcat -s GemmaEvalCsv   # CSV dump for manual SIM inspection
 */
@RunWith(AndroidJUnit4::class)
class GemmaEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val searcher = GemmaSearcher(context)
    private val embeddingScorer = EmbeddingScorer(context)
    private val corpus: EvalCorpus by lazy {
        EvalCorpusLoader.load(context.assets.open("eval_corpus.json"))
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /** Baseline: Gemma receives the full corpus with no pre-filtering. */
    @Test
    fun gemmaFullCorpusQualityReport() {
        ensureModel()
        val results = corpus.queries.map { q ->
            runQuery(q, corpus.entries)
        }
        printTable(results, label = "Full corpus")
        printResponses(results)
        printCsv(results, label = "full-corpus")
        assertFloors(results)
    }

    /**
     * Production-realistic: embeddings top-[PRE_FILTER_K] feeds into Gemma.
     * Shorter prompt, less hallucination surface, faster inference.
     *
     * Corpus embeddings are pre-computed once (60 embed calls) and reused across all
     * 41 queries — avoids the 41×60 = 2,460 redundant embed calls of naively calling
     * rank() per query.
     */
    @Test
    fun gemmaPreFilteredQualityReport() {
        ensureModel()
        val results = corpus.queries.map { q ->
            val preFiltered = embeddingScorer.rank(q.query, corpus.entries).take(PRE_FILTER_K)
            runQuery(q, preFiltered)
        }
        printTable(results, label = "Pre-filtered (embeddings top-$PRE_FILTER_K)")
        printResponses(results)
        printCsv(results, label = "pre-filtered-emb$PRE_FILTER_K")
        assertFloors(results)
    }

    // ── Model setup ───────────────────────────────────────────────────────────

    private fun ensureModel() {
        val modelFile = File(context.filesDir, "models/${GemmaSearcher.MODEL_FILENAME}")
        if (!modelFile.exists()) {
            val staged = File("/data/local/tmp/${GemmaSearcher.MODEL_FILENAME}")
            if (staged.exists()) {
                modelFile.parentFile?.mkdirs()
                staged.copyTo(modelFile)
            }
        }
        assumeTrue(
            "Gemma model not found at ${modelFile.absolutePath} (also checked /data/local/tmp/) — skipping. " +
                "Push with: adb push gemma3-1b-it-int4.task /data/local/tmp/",
            modelFile.exists() && modelFile.length() > 100 * 1024 * 1024L,
        )
    }

    // ── Core query runner ─────────────────────────────────────────────────────

    private fun runQuery(q: EvalQuery, entries: List<HistoryEntry.VisitedPage>): GemmaResult {
        val response = runBlocking { searcher.searchForEval(q.query, entries) }
        if (response == null) {
            android.util.Log.w("GemmaEval", "No response for '${q.query}' — model load failed")
        }
        val ranked = if (response != null) parseRankedEntries(response, corpus.entries) else emptyList()
        val sim = if (response != null && q.referenceAnswer != null) {
            embeddingScorer.cosineSimilarity(response, q.referenceAnswer)
        } else {
            null
        }
        return GemmaResult(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls), response ?: "<model unavailable>", sim)
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

    // ── Result type ───────────────────────────────────────────────────────────

    private data class GemmaResult(
        val query: EvalQuery,
        val p5: Double,
        val mrr: Double,
        val response: String,
        /** Cosine similarity vs referenceAnswer; null for negative queries. */
        val sim: Double?,
    )

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<GemmaResult>, label: String) {
        val header = "\n=== Gemma Quality Eval — $label (P@5 / MRR / SIM) ==="
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
                    val subSim = sub.mapNotNull { it.sim }.let { if (it.isEmpty()) null else it.average() }
                    val lbl = "${cat.replaceFirstChar { it.uppercase() }} avg"
                    if (subSim != null) {
                        add(rowSim.format(lbl, "", sub.map { it.p5 }.average(), sub.map { it.mrr }.average(), subSim))
                    } else {
                        add(row.format(lbl, "", sub.map { it.p5 }.average(), sub.map { it.mrr }.average()) + "     —")
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
            if (r.sim != null) sb.append("(SIM: ${"%.3f".format(r.sim)})\n")
        }
        sb.append(divider)
        android.util.Log.i("GemmaEval", sb.toString())
        println(sb)
    }

    /**
     * Writes a CSV for manual SIM inspection.
     *
     * Two outputs:
     *  1. File — full response, no truncation.
     *     Written to context.filesDir; pull while the test APK is still installed:
     *       adb shell run-as <package> cat files/gemma_eval_<label>.csv > /tmp/gemma_eval.csv
     *     The pull command is logged to GemmaEval at the end of the test.
     *  2. Logcat (GemmaEvalCsv tag) — response capped at 500 chars to stay under
     *     logcat's 4096-byte line limit. Capture both tags in one shot:
     *       adb logcat -d -s GemmaEval GemmaEvalCsv > eval_out.txt
     *
     * Format: label,query,category,p5,mrr,sim,response
     * Response is double-quoted; internal quotes escaped as "".
     */
    private fun printCsv(results: List<GemmaResult>, label: String) {
        val header = "label,query,category,p5,mrr,sim,response"
        val safeLabel = label.replace(' ', '_')

        // 1. Write full CSV to file
        val csvFile = File(context.filesDir, "gemma_eval_$safeLabel.csv")
        val sb = StringBuilder(header).append("\n")
        results.forEach { r ->
            val sim = r.sim?.let { "%.4f".format(it) } ?: ""
            val responseEscaped = r.response.replace("\"", "\"\"").replace("\n", "\\n")
            sb.append("${csvQuote(label)},${csvQuote(r.query.query)},${r.query.category},")
                .append("${"%.4f".format(r.p5)},${"%.4f".format(r.mrr)},$sim,${csvQuote(responseEscaped)}")
                .append("\n")
        }
        csvFile.writeText(sb.toString())
        android.util.Log.i("GemmaEval", "CSV file: ${csvFile.absolutePath}")
        android.util.Log.i("GemmaEval", "Pull: adb shell run-as ${context.packageName} cat ${csvFile.name.let { "files/$it" }} > /tmp/gemma_eval.csv")

        // 2. Also log to logcat with response capped to avoid line-length truncation
        android.util.Log.i("GemmaEvalCsv", header)
        results.forEach { r ->
            val sim = r.sim?.let { "%.4f".format(it) } ?: ""
            val responseForLog = r.response.replace("\n", "\\n").take(500)
            val line = "${csvQuote(label)},${csvQuote(r.query.query)},${r.query.category}," +
                "${"%.4f".format(r.p5)},${"%.4f".format(r.mrr)},$sim,${csvQuote(responseForLog)}"
            android.util.Log.i("GemmaEvalCsv", line)
        }
    }

    private fun csvQuote(s: String) = "\"$s\""

    // ── Floor thresholds ──────────────────────────────────────────────────────

    /**
     * Only asserts SIM floor. MRR/P@5 floors are omitted intentionally:
     * they measure URL-copying fidelity, not answer quality, and penalise
     * correct abstention ("Nothing relevant found") on negative/semantic queries.
     */
    private fun assertFloors(results: List<GemmaResult>) {
        val avgSim = results.mapNotNull { it.sim }.average()
        assertTrue(
            "Answer similarity floor violated: expected ≥ 0.72 (fires only if model is broken), got %.2f".format(avgSim),
            avgSim >= 0.72,
        )
    }

    companion object {
        /** Number of entries passed to Gemma after embedding pre-filtering. */
        private const val PRE_FILTER_K = 8
    }
}
