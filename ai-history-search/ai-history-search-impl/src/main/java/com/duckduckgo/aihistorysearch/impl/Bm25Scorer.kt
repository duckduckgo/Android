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

import com.duckduckgo.history.api.HistoryEntry
import kotlin.math.ln

/**
 * On-device BM25 ranking for browser history entries.
 *
 * Each history entry is treated as a document whose text is the concatenation of its title and URL.
 * Entries are scored against the search query and the caller receives them sorted by relevance,
 * with zero-scoring entries (no query term present) excluded.
 *
 * BM25 parameters: k1 = 1.5 (term saturation), b = 0.75 (length normalisation).
 */
internal object Bm25Scorer {

    private const val K1 = 1.5
    private const val B = 0.75

    /**
     * Returns [entries] sorted descending by BM25 relevance to [query].
     * Entries with a score of zero (none of the query terms appear in the entry) are excluded.
     * If [entries] is empty or [query] produces no tokens after stop-word removal, returns
     * [entries] unchanged so the caller always has something to work with.
     */
    fun rank(
        query: String,
        entries: List<HistoryEntry.VisitedPage>,
    ): List<HistoryEntry.VisitedPage> {
        if (entries.isEmpty()) return emptyList()

        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return entries

        val docs: List<List<String>> = entries.map { entry ->
            val extra = listOfNotNull(entry.description, entry.h1).joinToString(" ")
            tokenize("${entry.title} ${entry.url} $extra")
        }
        val n = docs.size
        val avgDocLen = docs.map { it.size }.average().takeIf { it > 0.0 } ?: 1.0

        // Inverse document frequency for each distinct query term.
        val idf: Map<String, Double> = queryTerms.associateWith { term ->
            val docsWithTerm = docs.count { term in it }
            ln((n - docsWithTerm + 0.5) / (docsWithTerm + 0.5) + 1.0)
        }

        return entries.zip(docs)
            .map { (entry, docTerms) ->
                val tf = docTerms.groupingBy { it }.eachCount()
                val docLen = docTerms.size.toDouble()
                val score = queryTerms.sumOf { term ->
                    val termFreq = tf[term]?.toDouble() ?: 0.0
                    val idfVal = idf[term] ?: 0.0
                    idfVal * (termFreq * (K1 + 1.0)) /
                        (termFreq + K1 * (1.0 - B + B * docLen / avgDocLen))
                }
                entry to score
            }
            .filter { (_, score) -> score > 0.0 }
            .sortedByDescending { (_, score) -> score }
            .map { (entry, _) -> entry }
    }

    /**
     * Lowercases [text], strips non-alphanumeric characters, splits on whitespace,
     * and removes stop words and very short tokens.
     */
    internal fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in STOP_WORDS }

    private val STOP_WORDS = setOf(
        // Articles / conjunctions / prepositions
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "up", "out",
        // Auxiliary verbs
        "be", "is", "it", "its", "was", "are", "were", "been", "being",
        "have", "has", "had", "do", "does", "did",
        "will", "would", "could", "should", "may", "might", "shall", "can",
        "not", "no", "so", "yet",
        // Pronouns
        "i", "my", "me", "we", "our", "you", "your",
        "he", "she", "his", "her", "they", "their",
        // Question words
        "what", "which", "who", "whom", "when", "where", "why", "how",
        // Common verbs / phrases that appear in history queries but add no signal
        "reading", "read", "about", "looking", "searched", "find", "found",
        // Temporal words — already handled by parseLookbackDays
        "today", "yesterday", "week", "weeks", "month", "months",
        "day", "days", "ago", "last", "this", "recent", "recently",
    )
}
