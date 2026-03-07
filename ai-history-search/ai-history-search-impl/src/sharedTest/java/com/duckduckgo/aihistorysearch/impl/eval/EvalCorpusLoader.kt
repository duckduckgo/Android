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
import java.io.InputStream
import java.time.LocalDateTime
import org.json.JSONObject

data class EvalQuery(
    val query: String,
    val category: String,
    val relevantUrls: Set<String>,
    /** Expected answer text for answer-quality eval. Null for negative queries. */
    val referenceAnswer: String? = null,
)

data class EvalCorpus(
    val entries: List<HistoryEntry.VisitedPage>,
    val queries: List<EvalQuery>,
)

/**
 * Parses eval_corpus.json into an [EvalCorpus].
 *
 * JVM (Robolectric) callers:
 *   EvalCorpusLoader.load(checkNotNull(javaClass.getResourceAsStream("/eval_corpus.json")))
 *
 * androidTest callers:
 *   EvalCorpusLoader.load(context.assets.open("eval_corpus.json"))
 */
object EvalCorpusLoader {

    private val ONE_DAY_AGO = listOf(LocalDateTime.now().minusDays(1))

    fun load(stream: InputStream): EvalCorpus {
        val root = JSONObject(stream.bufferedReader().readText())

        val entriesArr = root.getJSONArray("entries")
        val entries = (0 until entriesArr.length()).map { i ->
            val obj = entriesArr.getJSONObject(i)
            HistoryEntry.VisitedPage(
                url = Uri.parse(obj.getString("url")),
                title = obj.getString("title"),
                visits = ONE_DAY_AGO,
                description = obj.optNullableString("description"),
                h1 = obj.optNullableString("h1"),
                chunkText = obj.optNullableString("chunkText"),
            )
        }

        val queriesArr = root.getJSONArray("queries")
        val queries = (0 until queriesArr.length()).map { i ->
            val obj = queriesArr.getJSONObject(i)
            val urlsArr = obj.getJSONArray("relevantUrls")
            EvalQuery(
                query = obj.getString("query"),
                category = obj.getString("category"),
                relevantUrls = (0 until urlsArr.length()).map { j -> urlsArr.getString(j) }.toSet(),
                referenceAnswer = obj.optNullableString("referenceAnswer"),
            )
        }

        return EvalCorpus(entries, queries)
    }

    /** Returns null rather than the string "null" for absent/null JSON fields. */
    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null
}
