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
import com.duckduckgo.history.api.HistoryEntry
import java.time.LocalDateTime

data class EvalQuery(val query: String, val category: String, val relevantUrls: Set<String>)

/**
 * Synthetic corpus of 20 history entries and 10 eval queries.
 *
 * Entries are grouped by how relevance is carried:
 *  - Keyword-matchable: relevance is in title/URL (BM25 should find these)
 *  - Semantic targets: relevance is conceptual, not lexical (BM25 is expected to miss these)
 *  - Red herrings: superficially similar title/URL, wrong topic (test false-positive rate)
 *  - Body-only: title/URL are opaque; relevance only in chunkText (tests chunkText value)
 *  - Filler: unrelated entries that should never rank for any test query
 *
 * All visits are 1 day ago to avoid time-filter interaction.
 */
object SearchEvalCorpus {

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

    // ── Keyword-matchable (title/URL contains relevant terms) ─────────────────

    val ENTRY_KOTLIN_COROUTINES = page(
        url = "https://kotlinlang.org/docs/coroutines",
        title = "Kotlin Coroutines Guide",
    )

    val ENTRY_ANDROID_ROOM = page(
        url = "https://developer.android.com/room",
        title = "Android Room Database Tutorial",
    )

    val ENTRY_PANDAS = page(
        url = "https://pandas.pydata.org/docs",
        title = "Python Pandas DataFrame Guide",
    )

    val ENTRY_PASTA = page(
        url = "https://seriouseats.com/pasta-carbonara",
        title = "Best Pasta Carbonara Recipe",
    )

    val ENTRY_BARCELONA = page(
        url = "https://lonelyplanet.com/barcelona",
        title = "Barcelona Travel Guide",
    )

    val ENTRY_CHERRY_BLOSSOM = page(
        url = "https://japan-guide.com/cherry-blossom",
        title = "Tokyo Cherry Blossom Season",
    )

    val ENTRY_EV_BATTERIES = page(
        url = "https://theatlantic.com/ev-batteries",
        title = "Electric Vehicle Battery Technology",
    )

    val ENTRY_STARSHIP = page(
        url = "https://spacenews.com/starship-launch",
        title = "SpaceX Starship Launch",
    )

    // ── Semantic targets (title/URL lacks query terms; BM25 expected to miss) ─

    val ENTRY_HORSE = page(
        url = "https://en.wikipedia.org/wiki/Horse",
        title = "Horse \u2014 Wikipedia",
        description = "Large domesticated ungulate used for transport and sport",
    )

    val ENTRY_PHOTOSYNTHESIS = page(
        url = "https://khanacademy.org/photosynthesis",
        title = "Photosynthesis Process",
    )

    val ENTRY_SOURDOUGH_KING_ARTHUR = page(
        url = "https://kingarthurbaking.com/sourdough",
        title = "How to Make Sourdough Bread",
        description = "Guide to wild yeast fermentation and long-rise bread",
    )

    // ── Red herrings (similar surface, wrong topic) ───────────────────────────

    val ENTRY_PYTHON_SNAKE = page(
        url = "https://reptiles.com/python-care",
        title = "Python (Ball Python) Care Guide",
    )

    val ENTRY_JAVA_COFFEE = page(
        url = "https://perfectdailygrind.com/java-coffee",
        title = "Java: Indonesian Coffee Origins",
    )

    // ── Body-only (opaque title/URL; relevance only in chunkText) ────────────

    val ENTRY_MEDIUM_FLOW = page(
        url = "https://medium.com/p/abc123",
        title = "Article",
        chunkText = "Android Kotlin Flow and StateFlow patterns for reactive ViewModel architecture " +
            "and unidirectional data flow in modern Android development",
    )

    val ENTRY_REDDIT_SOURDOUGH = page(
        url = "https://reddit.com/r/cooking/xyz",
        title = "Thread",
        chunkText = "My sourdough starter doubled overnight after feeding with rye flour and water. " +
            "Here are my notes on maintaining a healthy bread starter culture.",
    )

    val ENTRY_NOTION_EV = page(
        url = "https://notion.so/user/notes123",
        title = "Notes",
        chunkText = "Electric vehicle home charging: Level 2 vs DC fast charging infrastructure. " +
            "Installation costs and grid capacity considerations for EV owners.",
    )

    // ── Filler (unrelated; should never surface for any test query) ───────────

    val ENTRY_CROSSWORD = page(
        url = "https://nytimes.com/crossword",
        title = "NYT Crossword Puzzle",
    )

    val ENTRY_CHESS = page(
        url = "https://chess.com",
        title = "Chess.com — Play Chess Online",
    )

    val ENTRY_WEATHER = page(
        url = "https://weather.com/forecast",
        title = "10-Day Weather Forecast",
    )

    val ENTRY_MOVIES = page(
        url = "https://imdb.com/top250",
        title = "IMDb Top 250 Movies",
    )

    // ── Full corpus ───────────────────────────────────────────────────────────

    val ENTRIES: List<HistoryEntry.VisitedPage> = listOf(
        ENTRY_KOTLIN_COROUTINES,
        ENTRY_ANDROID_ROOM,
        ENTRY_PANDAS,
        ENTRY_PASTA,
        ENTRY_BARCELONA,
        ENTRY_CHERRY_BLOSSOM,
        ENTRY_EV_BATTERIES,
        ENTRY_STARSHIP,
        ENTRY_HORSE,
        ENTRY_PHOTOSYNTHESIS,
        ENTRY_SOURDOUGH_KING_ARTHUR,
        ENTRY_PYTHON_SNAKE,
        ENTRY_JAVA_COFFEE,
        ENTRY_MEDIUM_FLOW,
        ENTRY_REDDIT_SOURDOUGH,
        ENTRY_NOTION_EV,
        ENTRY_CROSSWORD,
        ENTRY_CHESS,
        ENTRY_WEATHER,
        ENTRY_MOVIES,
    )

    // ── Queries ───────────────────────────────────────────────────────────────

    val QUERIES: List<EvalQuery> = listOf(
        EvalQuery(
            query = "Kotlin coroutines",
            category = "keyword",
            relevantUrls = setOf("https://kotlinlang.org/docs/coroutines"),
        ),
        EvalQuery(
            query = "Android database",
            category = "keyword",
            relevantUrls = setOf("https://developer.android.com/room"),
        ),
        EvalQuery(
            query = "four-legged animals",
            category = "semantic",
            relevantUrls = setOf("https://en.wikipedia.org/wiki/Horse"),
        ),
        EvalQuery(
            query = "how plants make energy",
            category = "semantic",
            relevantUrls = setOf("https://khanacademy.org/photosynthesis"),
        ),
        EvalQuery(
            query = "fermenting bread dough",
            category = "semantic",
            relevantUrls = setOf("https://kingarthurbaking.com/sourdough"),
        ),
        EvalQuery(
            query = "Python programming tutorial",
            category = "keyword",
            // reptiles.com is a red herring — should NOT count as relevant
            relevantUrls = setOf("https://pandas.pydata.org/docs"),
        ),
        EvalQuery(
            query = "Android reactive programming",
            category = "body-only",
            relevantUrls = setOf("https://medium.com/p/abc123"),
        ),
        EvalQuery(
            query = "sourdough bread starter",
            category = "body-only",
            relevantUrls = setOf(
                "https://reddit.com/r/cooking/xyz",
                "https://kingarthurbaking.com/sourdough",
            ),
        ),
        EvalQuery(
            query = "EV charging infrastructure",
            category = "body-only",
            relevantUrls = setOf(
                "https://notion.so/user/notes123",
                "https://theatlantic.com/ev-batteries",
            ),
        ),
        EvalQuery(
            query = "tropical fish reef aquarium",
            category = "negative",
            relevantUrls = emptySet(),
        ),
    )
}
