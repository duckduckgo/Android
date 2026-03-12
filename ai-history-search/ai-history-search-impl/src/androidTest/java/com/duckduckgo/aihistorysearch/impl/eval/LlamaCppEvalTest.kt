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
import com.duckduckgo.aihistorysearch.impl.LlamaCppDebloater
import com.duckduckgo.aihistorysearch.impl.LlamaCppInference
import com.duckduckgo.aihistorysearch.impl.LlamaCppSearcher
import com.duckduckgo.history.api.HistoryEntry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * llama.cpp latency + quality eval harness.
 *
 * Tests (all skipped if model absent):
 *
 *   [llamaCppFullCorpusLatencyReport]      — same task as GemmaEvalTest. Direct latency comparison.
 *   [llamaCppPreFilteredLatencyReport]     — embeddings top-8 → llama.cpp. Matches Gemma pre-filtered test.
 *   [llamaCppDebloatLatencyReport]         — debloat each query, then re-run BM25+embeddings.
 *                                            Key question: does debloating improve semantic MRR? At what latency?
 *   [llamaCppDebloatRealCorpusLatencyReport] — same debloat test on the real-sites corpus.
 *
 * Model: google_gemma-3-1b-it-Q4_K_M.gguf (~700 MB)
 * Push:
 *   adb push google_gemma-3-1b-it-Q4_K_M.gguf /data/local/tmp/
 *
 * Run all:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.LlamaCppEvalTest
 *
 * Run one:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.LlamaCppEvalTest#llamaCppDebloatLatencyReport
 *
 * Filter logcat:
 *   adb logcat -d -s LlamaCppEval
 */
@RunWith(AndroidJUnit4::class)
class LlamaCppEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inference = LlamaCppInference(
        modelPath = File(context.filesDir, "models/${LlamaCppSearcher.MODEL_FILENAME}").absolutePath,
        nCtx = 4096,
    )
    private val searcher = LlamaCppSearcher(context)
    private val debloater = LlamaCppDebloater(inference) // share the loaded model
    private val embeddingScorer = EmbeddingScorer(context)
    private val corpus: EvalCorpus by lazy {
        EvalCorpusLoader.load(context.assets.open("eval_corpus.json"))
    }
    private val realCorpus: EvalCorpus by lazy {
        EvalCorpusLoader.load(context.assets.open("eval_corpus_real.json"))
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Full corpus → llama.cpp. Direct latency comparison with GemmaEvalTest.gemmaFullCorpusQualityReport.
     * Reports: latency per query, MRR, SIM.
     */
    @Test
    fun llamaCppFullCorpusLatencyReport() {
        ensureModel()
        val latencies = mutableListOf<Long>()
        val results = corpus.queries.map { q ->
            val (result, ms) = timed { runQuery(q, corpus.entries) }
            latencies.add(ms)
            result
        }
        printTable(results, latencies, label = "llama.cpp full corpus")
    }

    /**
     * Embeddings top-8 → llama.cpp. Direct comparison with GemmaEvalTest.gemmaPreFilteredQualityReport.
     */
    @Test
    fun llamaCppPreFilteredLatencyReport() {
        ensureModel()
        val latencies = mutableListOf<Long>()
        val results = corpus.queries.map { q ->
            val preFiltered = embeddingScorer.rank(q.query, corpus.entries).take(PRE_FILTER_K)
            val (result, ms) = timed { runQuery(q, preFiltered) }
            latencies.add(ms)
            result
        }
        printTable(results, latencies, label = "llama.cpp pre-filtered (emb top-$PRE_FILTER_K)")
    }

    /**
     * KEY TEST: debloat each query → BM25 + embeddings on debloated query.
     * Measures:
     *   - Debloat latency (short inference)
     *   - Whether debloating improves MRR on semantic/body-only categories vs raw BM25+embeddings
     */
    @Test
    fun llamaCppDebloatLatencyReport() {
        ensureModel()
        val debloatLatencies = mutableListOf<Long>()
        val results = corpus.queries.map { q ->
            // Step 1: debloat
            val (debloated, debloatMs) = timed { runBlocking { debloater.debloat(q.query) } }
            debloatLatencies.add(debloatMs)
            android.util.Log.i("LlamaCppEval", "Debloat: '${q.query}' → '$debloated' (${debloatMs}ms)")

            // Step 2: retrieve using debloated query (BM25 + embeddings RRF)
            val bm25Ranked = Bm25Scorer.rank(debloated, corpus.entries)
            val embRanked = embeddingScorer.rank(debloated, corpus.entries)
            val rrfRanked = reciprocalRankFusion(bm25Ranked, embRanked).take(10)

            // Step 3: also retrieve using original query for comparison
            val bm25Original = Bm25Scorer.rank(q.query, corpus.entries)
            val embOriginal = embeddingScorer.rank(q.query, corpus.entries)
            val rrfOriginal = reciprocalRankFusion(bm25Original, embOriginal).take(10)

            DebloatResult(
                query = q,
                debloatedQuery = debloated,
                debloatMs = debloatMs,
                mrrDebloated = mrr(rrfRanked, q.relevantUrls),
                mrrOriginal = mrr(rrfOriginal, q.relevantUrls),
            )
        }
        printDebloatTable(results, debloatLatencies)
    }

    /**
     * Same as [llamaCppDebloatLatencyReport] but on the real-sites corpus.
     * No floor assertions — observation only.
     */
    @Test
    fun llamaCppDebloatRealCorpusLatencyReport() {
        ensureModel()
        val debloatLatencies = mutableListOf<Long>()
        val results = realCorpus.queries.map { q ->
            val (debloated, debloatMs) = timed { runBlocking { debloater.debloat(q.query) } }
            debloatLatencies.add(debloatMs)
            android.util.Log.i("LlamaCppEval", "Debloat: '${q.query}' → '$debloated' (${debloatMs}ms)")

            val bm25Ranked = Bm25Scorer.rank(debloated, realCorpus.entries)
            val embRanked = embeddingScorer.rank(debloated, realCorpus.entries)
            val rrfRanked = reciprocalRankFusion(bm25Ranked, embRanked).take(10)

            val bm25Original = Bm25Scorer.rank(q.query, realCorpus.entries)
            val embOriginal = embeddingScorer.rank(q.query, realCorpus.entries)
            val rrfOriginal = reciprocalRankFusion(bm25Original, embOriginal).take(10)

            DebloatResult(
                query = q,
                debloatedQuery = debloated,
                debloatMs = debloatMs,
                mrrDebloated = mrr(rrfRanked, q.relevantUrls),
                mrrOriginal = mrr(rrfOriginal, q.relevantUrls),
            )
        }
        printDebloatTable(results, debloatLatencies, label = "real corpus")
    }

    // ── Model setup ───────────────────────────────────────────────────────────

    private fun ensureModel() {
        val modelFile = File(context.filesDir, "models/${LlamaCppSearcher.MODEL_FILENAME}")
        if (!modelFile.exists()) {
            val staged = File("/data/local/tmp/${LlamaCppSearcher.MODEL_FILENAME}")
            if (staged.exists()) {
                modelFile.parentFile?.mkdirs()
                staged.copyTo(modelFile)
            }
        }
        assumeTrue(
            "llama.cpp model not found at ${modelFile.absolutePath} — skipping.\n" +
                "Push with:\n" +
                "  huggingface-cli download bartowski/google_gemma-3-1b-it-GGUF " +
                "${LlamaCppSearcher.MODEL_FILENAME} --local-dir .\n" +
                "  adb push ${LlamaCppSearcher.MODEL_FILENAME} /data/local/tmp/",
            modelFile.exists() && modelFile.length() > 100 * 1024 * 1024L,
        )
    }

    // ── Core helpers ──────────────────────────────────────────────────────────

    private fun runQuery(q: EvalQuery, entries: List<HistoryEntry.VisitedPage>): EvalResult {
        val response = runBlocking { searcher.searchForEval(q.query, entries) }
        val ranked = if (response != null) parseRankedEntries(response, entries) else emptyList()
        val sim = if (response != null && q.referenceAnswer != null) {
            embeddingScorer.cosineSimilarity(response, q.referenceAnswer)
        } else null
        return EvalResult(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls), response ?: "<null>", sim)
    }

    private fun <T> timed(block: () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        return result to (System.currentTimeMillis() - start)
    }

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

    private fun reciprocalRankFusion(
        list1: List<HistoryEntry.VisitedPage>,
        list2: List<HistoryEntry.VisitedPage>,
        k: Int = 60,
    ): List<HistoryEntry.VisitedPage> {
        val scores = mutableMapOf<String, Double>()
        list1.forEachIndexed { rank, entry ->
            scores[entry.url.toString()] = (scores[entry.url.toString()] ?: 0.0) + 1.0 / (k + rank + 1)
        }
        list2.forEachIndexed { rank, entry ->
            scores[entry.url.toString()] = (scores[entry.url.toString()] ?: 0.0) + 1.0 / (k + rank + 1)
        }
        return (list1 + list2)
            .distinctBy { it.url.toString() }
            .sortedByDescending { scores[it.url.toString()] ?: 0.0 }
    }

    // ── Result types ──────────────────────────────────────────────────────────

    private data class EvalResult(
        val query: EvalQuery,
        val p5: Double,
        val mrr: Double,
        val response: String,
        val sim: Double?,
    )

    private data class DebloatResult(
        val query: EvalQuery,
        val debloatedQuery: String,
        val debloatMs: Long,
        val mrrDebloated: Double,
        val mrrOriginal: Double,
    )

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<EvalResult>, latencies: List<Long>, label: String) {
        val avgLatencyMs = latencies.average().toLong()
        val divider = "─".repeat(80)
        val sb = StringBuilder()
        sb.appendLine("\n=== llama.cpp Eval — $label ===")
        sb.appendLine("Avg latency: ${avgLatencyMs}ms | Min: ${latencies.minOrNull()}ms | Max: ${latencies.maxOrNull()}ms")
        sb.appendLine("%-40s %-12s %5s %5s %5s %8s".format("Query", "Category", "P@5", "MRR", "SIM", "Lat(ms)"))
        sb.appendLine(divider)
        results.forEachIndexed { i, r ->
            val sim = r.sim?.let { "%.2f".format(it) } ?: "  —  "
            sb.appendLine(
                "%-40s %-12s %5.2f %5.2f %5s %8d".format(
                    r.query.query.take(40), r.query.category.take(12), r.p5, r.mrr, sim, latencies[i],
                ),
            )
        }
        sb.appendLine(divider)
        val avgMrr = results.map { it.mrr }.average()
        val avgSim = results.mapNotNull { it.sim }.let { if (it.isEmpty()) null else it.average() }
        sb.appendLine(
            "%-40s %-12s %5s %5.2f %5s %8d".format(
                "Overall avg", "", "", avgMrr, avgSim?.let { "%.2f".format(it) } ?: "—", avgLatencyMs,
            ),
        )
        android.util.Log.i("LlamaCppEval", sb.toString())
        println(sb)
    }

    private fun printDebloatTable(
        results: List<DebloatResult>,
        debloatLatencies: List<Long>,
        label: String = "synthetic corpus",
    ) {
        val avgDebloatMs = debloatLatencies.average().toLong()
        val divider = "─".repeat(90)
        val sb = StringBuilder()
        sb.appendLine("\n=== llama.cpp Debloat Eval — $label ===")
        sb.appendLine("Avg debloat latency: ${avgDebloatMs}ms | Min: ${debloatLatencies.minOrNull()}ms | Max: ${debloatLatencies.maxOrNull()}ms")
        sb.appendLine("%-35s %-12s %-30s %5s %5s %8s".format("Original query", "Category", "Debloated", "MRR-O", "MRR-D", "Lat(ms)"))
        sb.appendLine(divider)
        results.forEachIndexed { i, r ->
            sb.appendLine(
                "%-35s %-12s %-30s %5.2f %5.2f %8d".format(
                    r.query.query.take(35), r.query.category.take(12),
                    r.debloatedQuery.take(30), r.mrrOriginal, r.mrrDebloated, r.debloatMs,
                ),
            )
        }
        sb.appendLine(divider)
        val avgMrrOriginal = results.map { it.mrrOriginal }.average()
        val avgMrrDebloated = results.map { it.mrrDebloated }.average()
        sb.appendLine(
            "%-35s %-12s %-30s %5.2f %5.2f %8d".format(
                "Overall avg", "", "", avgMrrOriginal, avgMrrDebloated, avgDebloatMs,
            ),
        )
        for (cat in listOf("keyword", "semantic", "body-only", "negative")) {
            val sub = results.filter { it.query.category == cat }
            if (sub.isNotEmpty()) {
                sb.appendLine(
                    "%-35s %-12s %-30s %5.2f %5.2f".format(
                        "${cat.replaceFirstChar { it.uppercase() }} avg", "", "",
                        sub.map { it.mrrOriginal }.average(), sub.map { it.mrrDebloated }.average(),
                    ),
                )
            }
        }
        android.util.Log.i("LlamaCppEval", sb.toString())
        println(sb)
    }

    companion object {
        private const val PRE_FILTER_K = 8
    }
}
