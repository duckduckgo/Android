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

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatContextualResult
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class NoHistoryException : Exception("No history found for the given time period")

@SingleInstanceIn(AppScope::class)
class AiHistorySearchInteractor @Inject constructor(
    private val navigationHistory: NavigationHistory,
    private val feature: AiHistorySearchFeature,
    private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
) {

    private val embeddingScorer: EmbeddingScorer by lazy { EmbeddingScorer(context) }
    private val geminiNanoSearcher: GeminiNanoSearcher by lazy { GeminiNanoSearcher() }
    private val gemmaSearcher: GemmaSearcher by lazy { GemmaSearcher(context) }

    /**
     * Builds a [DuckChatContextualResult.Submit] for [query] without opening Duck.ai.
     *
     * Routing:
     * - [AiHistorySearchFeature.aiCoreEnabled] ON  → Gemini Nano shadow call (logcat only,
     *   never short-circuits). Duck.ai always opens via one of the paths below.
     * - [AiHistorySearchFeature.embeddingsEnabled] ON  → rank entries on-device with semantic
     *   embeddings (USE Lite), send only the top [MAX_ENTRIES_EMBEDDINGS] matches.
     * - [AiHistorySearchFeature.bm25Enabled] ON  → rank entries on-device with BM25, send only the
     *   top [MAX_ENTRIES_BM25] matches. The full history never leaves the device.
     * - Both embeddings and BM25 OFF → send the full time-filtered history (up to
     *   [MAX_ENTRIES_FULL] entries) and let Duck.ai do the semantic matching.
     *
     * Throws [NoHistoryException] if no matching history entries are found.
     */
    suspend fun buildResult(query: String): DuckChatContextualResult.Submit {
        val embeddingsOn = feature.embeddingsEnabled().isEnabled()
        val bm25On = feature.bm25Enabled().isEnabled()
        logcat { "AiHistorySearch: buildResult query='$query' embeddings=$embeddingsOn bm25=$bm25On" }

        val allHistory = navigationHistory.getHistory().first()
        val pages = allHistory.filterIsInstance<HistoryEntry.VisitedPage>()

        val cutoff = LocalDateTime.now().minusDays(parseLookbackDays(query))
        val timeFiltered = pages
            .filter { entry -> entry.visits.any { it.isAfter(cutoff) } }
            .sortedByDescending { entry -> entry.visits.maxOrNull() }

        if (timeFiltered.isEmpty()) throw NoHistoryException()

        // AICore shadow path — logcats result only, never short-circuits Duck.ai
        if (feature.aiCoreEnabled().isEnabled()) {
            appScope.launch { geminiNanoSearcher.search(query, timeFiltered) }
        }
        if (feature.gemmaEnabled().isEnabled()) {
            appScope.launch { gemmaSearcher.search(query, timeFiltered) }
        }

        return when {
            embeddingsOn -> buildEmbeddingsResult(query, timeFiltered)
            bm25On       -> buildBm25Result(query, timeFiltered)
            else         -> buildFullResult(query, timeFiltered.take(MAX_ENTRIES_FULL))
        }
    }

    // ---- BM25 path -----------------------------------------------------------------------

    private fun buildBm25Result(
        query: String,
        timeFiltered: List<HistoryEntry.VisitedPage>,
    ): DuckChatContextualResult.Submit {
        val ranked = Bm25Scorer.rank(query, timeFiltered).take(MAX_ENTRIES_BM25)
        if (ranked.isEmpty()) throw NoHistoryException()
        logcat { "AiHistorySearch (BM25): ${ranked.size} entries after on-device ranking" }

        val instructions =
            "Instructions for using these results:\n" +
                "- These are the most relevant pages from my browser history for this query, " +
                "pre-matched on-device.\n" +
                "- Use the visit date to answer any time-based parts of the question.\n" +
                "- Summarize what these pages are about and how they relate to my question.\n" +
                "- If nothing here clearly matches, say so."

        return DuckChatContextualResult.Submit(
            prompt = "I searched my browser history and found these pages matching \"$query\". " +
                "Can you help me understand what I was looking at?",
            context = buildContext(ranked, instructions, "Browser History (top matches)"),
        )
    }

    // ---- Embeddings path -----------------------------------------------------------------

    private fun buildEmbeddingsResult(
        query: String,
        timeFiltered: List<HistoryEntry.VisitedPage>,
    ): DuckChatContextualResult.Submit {
        val ranked = embeddingScorer.rank(query, timeFiltered).take(MAX_ENTRIES_EMBEDDINGS)
        if (ranked.isEmpty()) throw NoHistoryException()
        logcat { "AiHistorySearch (embeddings): ${ranked.size} entries after on-device ranking" }

        val instructions =
            "Instructions for using these results:\n" +
                "- These are the most relevant pages from my browser history, matched on-device " +
                "using semantic similarity — not just keywords.\n" +
                "- Use the visit date to answer any time-based parts of the question.\n" +
                "- Summarize what these pages are about and how they relate to my question.\n" +
                "- If nothing here clearly matches, say so."

        return DuckChatContextualResult.Submit(
            prompt = "I searched my browser history and found these pages matching \"$query\". " +
                "Can you help me understand what I was looking at?",
            context = buildContext(ranked, instructions, "Browser History (semantic matches)"),
        )
    }

    // ---- Full history path ---------------------------------------------------------------

    private fun buildFullResult(
        query: String,
        entries: List<HistoryEntry.VisitedPage>,
    ): DuckChatContextualResult.Submit {
        logcat { "AiHistorySearch (full): ${entries.size} entries sent to Duck.ai" }

        val instructions =
            "Instructions for using this browser history:\n" +
                "- Match pages by concept and topic, not just by keyword. " +
                "Use your world knowledge to infer what a page is about from its title and URL. " +
                "For example, a question about \"animals with four limbs\" should match a page titled \"Horse — Wikipedia\".\n" +
                "- Use the visit date on each entry to answer any time-based parts of the question.\n" +
                "- If no entries directly match the topic, check whether any entries cover related or adjacent topics. " +
                "If so, proactively mention them and explain the connection — for example, if the user asks about cars " +
                "but the history only contains pages about trucks and motorbikes, surface those and note that " +
                "while there are no car pages, there are pages about related types of vehicles.\n" +
                "- If nothing in the history is relevant at all, say so clearly."

        return DuckChatContextualResult.Submit(
            prompt = "I'm searching my browser history. My question: \"$query\". " +
                "Note: page titles in my history may not use the exact words in my question — " +
                "please reason about what topics the pages likely cover. " +
                "Which pages are most relevant? Please list them with their URLs.",
            context = buildContext(entries, instructions, "Browser History (last $LOOKBACK_DAYS days)"),
        )
    }

    // ---- Shared helpers ------------------------------------------------------------------

    /**
     * Estimates how many days back to fetch history based on the query.
     * Intentionally generous — temporal precision is handled by the chosen path.
     * Handles explicit numbers ("4 days ago", "2 weeks ago"), keywords
     * ("yesterday", "last week"), and compound phrases ("between yesterday
     * and last week") by collecting all matches and taking the largest.
     */
    internal fun parseLookbackDays(query: String): Long {
        val lq = query.lowercase()

        // Explicit "N days ago / prior / back / before"
        DAYS_PATTERN.find(lq)?.groupValues?.get(1)?.toLongOrNull()?.let { return it + 1 }

        // Explicit "N weeks ago / prior / back / before"
        WEEKS_PATTERN.find(lq)?.groupValues?.get(1)?.toLongOrNull()?.let { return it * 7 + 1 }

        // Keyword-based — collect all matches, return the largest so compound
        // phrases like "between yesterday and last week" work correctly.
        var days = 0L
        if (lq.contains("today")) days = maxOf(days, 1L)
        if (lq.contains("yesterday")) days = maxOf(days, 2L)
        if (lq.contains("this week") || lq.contains("last week")) days = maxOf(days, 14L)
        if (lq.contains("this month") || lq.contains("last month")) days = maxOf(days, 60L)
        return if (days > 0L) days else LOOKBACK_DAYS
    }

    private fun buildContext(
        entries: List<HistoryEntry.VisitedPage>,
        instructions: String,
        label: String,
    ): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val content = buildString {
            appendLine(instructions)
            appendLine()
            appendLine("$label:")
            entries.forEach { entry ->
                val date = entry.visits.maxOrNull()?.format(formatter) ?: "?"
                appendLine("- ${entry.title} — ${entry.url} ($date)")
            }
        }.trim()
        return JSONObject().apply {
            put("title", "Browser History")
            put("url", "https://duckduckgo.com/")
            put("content", content)
            put("favicon", JSONArray())
            put("truncated", false)
        }.toString()
    }

    companion object {
        private const val LOOKBACK_DAYS = 30L
        internal const val MAX_ENTRIES_FULL = 20
        internal const val MAX_ENTRIES_BM25 = 10
        internal const val MAX_ENTRIES_EMBEDDINGS = 10
        private val DAYS_PATTERN = Regex("""(\d+)\s*days?\s*(ago|prior|back|before)""")
        private val WEEKS_PATTERN = Regex("""(\d+)\s*weeks?\s*(ago|prior|back|before)""")
    }
}
