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
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.aihistorysearch.impl.EmbeddingScorer
import com.duckduckgo.history.api.HistoryEntry
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Embedding quality evaluation harness.
 *
 * Mirrors [Bm25ScorerEvalTest] exactly — same corpus, same queries, same metrics — so results
 * can be compared side-by-side. Requires a real device or emulator because MediaPipe's
 * TextEmbedder uses TFLite native inference and needs the USE Lite model from assets.
 *
 * Run:
 *   ./gradlew :ai-history-search-impl:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.duckduckgo.aihistorysearch.impl.eval.EmbeddingScorerEvalTest
 *
 * The quality table is printed to logcat — filter by tag "EmbeddingEval":
 *   adb logcat -s EmbeddingEval
 *
 * Expected semantic advantage over BM25:
 *   BM25  — semantic MRR ≈ 0.33  (only partial keyword accidents)
 *   USE   — semantic MRR ≥ 0.50  (should resolve "four-legged animals" → Horse)
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingScorerEvalTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scorer = EmbeddingScorer(context)

    @Test
    fun embeddingQualityReport() {
        val results = QUERIES.map { q ->
            val ranked = scorer.rank(q.query, ENTRIES)
            Triple(q, precisionAtK(ranked, q.relevantUrls, 5), mrr(ranked, q.relevantUrls))
        }
        printTable(results)
        assertFloors(results)
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    private fun precisionAtK(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>, k: Int): Double {
        if (relevant.isEmpty()) return if (ranked.take(k).isEmpty()) 1.0 else 0.0
        return ranked.take(k).count { it.url.toString() in relevant } / k.toDouble()
    }

    private fun mrr(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>): Double {
        if (relevant.isEmpty()) return 1.0
        val rank = ranked.indexOfFirst { it.url.toString() in relevant }
        return if (rank == -1) 0.0 else 1.0 / (rank + 1)
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun printTable(results: List<Triple<EvalQuery, Double, Double>>) {
        val header = "\n=== Embedding Quality Eval (Precision@5 / MRR) ==="
        val col = "%-34s %-14s %5s %5s"
        val row = "%-34s %-14s %5.2f %5.2f"
        val divider = "─".repeat(64)

        val lines = buildList {
            add(header)
            add(col.format("Query", "Category", "P@5", "MRR"))
            add(divider)
            results.forEach { (q, p5, mrrVal) ->
                add(row.format(q.query.take(34), q.category.take(14), p5, mrrVal))
            }
            add(divider)
            val avgP5 = results.map { it.second }.average()
            val avgMrr = results.map { it.third }.average()
            add(row.format("Overall avg", "", avgP5, avgMrr))
            for (cat in listOf("keyword", "semantic", "body-only", "negative")) {
                val sub = results.filter { it.first.category == cat }
                if (sub.isNotEmpty()) {
                    add(row.format("${cat.replaceFirstChar { it.uppercase() }} avg", "", sub.map { it.second }.average(), sub.map { it.third }.average()))
                }
            }
        }
        val table = lines.joinToString("\n")
        android.util.Log.i("EmbeddingEval", table)
        println(table)
    }

    // ── Floor thresholds ──────────────────────────────────────────────────────
    //
    // Key regressions guarded:
    //   keyword MRR  — embeddings must still find exact keyword matches
    //   semantic MRR — the primary advantage over BM25; must beat BM25's ~0.33
    //   body-only MRR — chunkText must be indexed and contribute to embeddings

    private fun assertFloors(results: List<Triple<EvalQuery, Double, Double>>) {
        val keywordMrr = results.filter { it.first.category == "keyword" }.map { it.third }.average()
        val semanticMrr = results.filter { it.first.category == "semantic" }.map { it.third }.average()
        val bodyOnlyMrr = results.filter { it.first.category == "body-only" }.map { it.third }.average()

        assertTrue(
            "Keyword MRR floor violated: expected ≥ 0.60, got %.2f".format(keywordMrr),
            keywordMrr >= 0.60,
        )
        assertTrue(
            "Semantic MRR floor violated: expected ≥ 0.50 (must beat BM25 on conceptual queries), got %.2f".format(semanticMrr),
            semanticMrr >= 0.50,
        )
        assertTrue(
            "Body-only MRR floor violated: expected ≥ 0.80 (chunkText must be indexed), got %.2f".format(bodyOnlyMrr),
            bodyOnlyMrr >= 0.80,
        )
    }

    // ── Corpus (mirrors SearchEvalCorpus in src/test — duplicated for androidTest access) ──

    private data class EvalQuery(val query: String, val category: String, val relevantUrls: Set<String>)

    private val ONE_DAY_AGO = listOf(LocalDateTime.now().minusDays(1))

    private fun page(
        url: String,
        title: String,
        description: String? = null,
        h1: String? = null,
        chunkText: String? = null,
    ) = HistoryEntry.VisitedPage(
        url = Uri.parse(url),
        title = title,
        visits = ONE_DAY_AGO,
        description = description,
        h1 = h1,
        chunkText = chunkText,
    )

    private val ENTRIES = listOf(
        // Keyword-matchable
        page("https://kotlinlang.org/docs/coroutines", "Kotlin Coroutines Guide"),
        page("https://developer.android.com/room", "Android Room Database Tutorial"),
        page("https://pandas.pydata.org/docs", "Python Pandas DataFrame Guide"),
        page("https://seriouseats.com/pasta-carbonara", "Best Pasta Carbonara Recipe"),
        page("https://lonelyplanet.com/barcelona", "Barcelona Travel Guide"),
        page("https://japan-guide.com/cherry-blossom", "Tokyo Cherry Blossom Season"),
        page("https://theatlantic.com/ev-batteries", "Electric Vehicle Battery Technology"),
        page("https://spacenews.com/starship-launch", "SpaceX Starship Launch"),
        // Semantic targets
        page(
            "https://en.wikipedia.org/wiki/Horse",
            "Horse \u2014 Wikipedia",
            description = "Large domesticated ungulate used for transport and sport",
        ),
        page("https://khanacademy.org/photosynthesis", "Photosynthesis Process"),
        page(
            "https://kingarthurbaking.com/sourdough",
            "How to Make Sourdough Bread",
            description = "Guide to wild yeast fermentation and long-rise bread",
        ),
        // Red herrings
        page("https://reptiles.com/python-care", "Python (Ball Python) Care Guide"),
        page("https://perfectdailygrind.com/java-coffee", "Java: Indonesian Coffee Origins"),
        // Body-only
        page(
            "https://medium.com/p/abc123",
            "Article",
            chunkText = "Android Kotlin Flow and StateFlow patterns for reactive ViewModel architecture " +
                "and unidirectional data flow in modern Android development",
        ),
        page(
            "https://reddit.com/r/cooking/xyz",
            "Thread",
            chunkText = "My sourdough starter doubled overnight after feeding with rye flour and water. " +
                "Here are my notes on maintaining a healthy bread starter culture.",
        ),
        page(
            "https://notion.so/user/notes123",
            "Notes",
            chunkText = "Electric vehicle home charging: Level 2 vs DC fast charging infrastructure. " +
                "Installation costs and grid capacity considerations for EV owners.",
        ),
        // Filler
        page("https://nytimes.com/crossword", "NYT Crossword Puzzle"),
        page("https://chess.com", "Chess.com \u2014 Play Chess Online"),
        page("https://weather.com/forecast", "10-Day Weather Forecast"),
        page("https://imdb.com/top250", "IMDb Top 250 Movies"),
    )

    private val QUERIES = listOf(
        EvalQuery("Kotlin coroutines", "keyword", setOf("https://kotlinlang.org/docs/coroutines")),
        EvalQuery("Android database", "keyword", setOf("https://developer.android.com/room")),
        EvalQuery("four-legged animals", "semantic", setOf("https://en.wikipedia.org/wiki/Horse")),
        EvalQuery("how plants make energy", "semantic", setOf("https://khanacademy.org/photosynthesis")),
        EvalQuery("fermenting bread dough", "semantic", setOf("https://kingarthurbaking.com/sourdough")),
        EvalQuery("Python programming tutorial", "keyword", setOf("https://pandas.pydata.org/docs")),
        EvalQuery("Android reactive programming", "body-only", setOf("https://medium.com/p/abc123")),
        EvalQuery(
            "sourdough bread starter", "body-only",
            setOf("https://reddit.com/r/cooking/xyz", "https://kingarthurbaking.com/sourdough"),
        ),
        EvalQuery(
            "EV charging infrastructure", "body-only",
            setOf("https://notion.so/user/notes123", "https://theatlantic.com/ev-batteries"),
        ),
        EvalQuery("tropical fish reef aquarium", "negative", emptySet()),
    )
}
