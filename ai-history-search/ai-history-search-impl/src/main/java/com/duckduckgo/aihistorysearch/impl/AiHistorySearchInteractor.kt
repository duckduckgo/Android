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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatContextualResult
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.first
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
) {

    /**
     * Builds a [DuckChatContextualResult.Submit] for [query] without opening Duck.ai.
     * Throws [NoHistoryException] if no matching history entries are found.
     */
    suspend fun buildResult(query: String): DuckChatContextualResult.Submit {
        logcat { "AiHistorySearch: buildResult query='$query'" }
        val allHistory = navigationHistory.getHistory().first()
        val pages = allHistory.filterIsInstance<HistoryEntry.VisitedPage>()

        val cutoff = LocalDateTime.now().minusDays(parseLookbackDays(query))
        val filtered = pages
            .filter { entry -> entry.visits.any { it.isAfter(cutoff) } }
            .sortedByDescending { entry -> entry.visits.maxOrNull() }
            .take(MAX_ENTRIES)

        if (filtered.isEmpty()) throw NoHistoryException()

        logcat { "AiHistorySearch: ${filtered.size} history entries found" }
        return DuckChatContextualResult.Submit(
            prompt = buildPrompt(query),
            context = buildContext(filtered),
        )
    }

    /**
     * Estimates how many days back to fetch history based on the query.
     * Intentionally generous — Duck.ai handles precise temporal filtering.
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

    private fun buildPrompt(query: String): String =
        "I'm searching my browser history. My question: \"$query\". " +
            "Note: page titles in my history may not use the exact words in my question — " +
            "please reason about what topics the pages likely cover. " +
            "Which pages are most relevant? Please list them with their URLs."

    private fun buildContext(entries: List<HistoryEntry.VisitedPage>): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val content = buildString {
            appendLine(
                "Instructions for using this browser history:\n" +
                    "- Match pages by concept and topic, not just by keyword. " +
                    "Use your world knowledge to infer what a page is about from its title and URL. " +
                    "For example, a question about \"animals with four limbs\" should match a page titled \"Horse — Wikipedia\".\n" +
                    "- Use the visit date on each entry to answer any time-based parts of the question.\n" +
                    "- If no entries directly match the topic, check whether any entries cover related or adjacent topics. " +
                    "If so, proactively mention them and explain the connection — for example, if the user asks about cars " +
                    "but the history only contains pages about trucks and motorbikes, surface those and note that " +
                    "while there are no car pages, there are pages about related types of vehicles.\n" +
                    "- If nothing in the history is relevant at all, say so clearly.",
            )
            appendLine()
            appendLine("Browser history (last $LOOKBACK_DAYS days):")
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
        private const val MAX_ENTRIES = 20
        private val DAYS_PATTERN = Regex("""(\d+)\s*days?\s*(ago|prior|back|before)""")
        private val WEEKS_PATTERN = Regex("""(\d+)\s*weeks?\s*(ago|prior|back|before)""")
    }
}
