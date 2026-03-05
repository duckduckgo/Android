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
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.duckduckgo.history.api.HistoryEntry
import logcat.logcat

internal class EmbeddingScorer(context: Context) {

    private val embedder: TextEmbedder by lazy {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .build()
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
        TextEmbedder.createFromOptions(context, options)
    }

    /**
     * Embeds [query] and all [entries] on-device, then returns entries sorted by cosine
     * similarity (highest first), excluding entries below [SIMILARITY_THRESHOLD].
     * Called from a background coroutine — synchronous inference is fine here.
     */
    fun rank(query: String, entries: List<HistoryEntry.VisitedPage>): List<HistoryEntry.VisitedPage> {
        val queryEmbedding = embed(query)
        val scored = entries.map { entry ->
            val text = buildString {
                if (entry.title.isNotBlank()) append(entry.title).append(" ")
                append(entry.url)
                entry.h1?.takeIf { it.isNotBlank() && it != entry.title }?.let { append(" ").append(it) }
                entry.description?.takeIf { it.isNotBlank() }?.let { append(" ").append(it) }
            }
            entry to TextEmbedder.cosineSimilarity(queryEmbedding, embed(text))
        }
        val sorted = scored.sortedByDescending { (_, sim) -> sim }
        val scoresSummary = sorted.take(20).joinToString(" | ") { (entry, sim) ->
            "${"%.3f".format(sim)} ${entry.title.take(20)}"
        }
        logcat { "AiHistorySearch (embeddings) '$query': $scoresSummary" }
        val topScore = sorted.first().second
        val threshold = maxOf(SIMILARITY_MIN_THRESHOLD, topScore - SIMILARITY_MARGIN)
        val passing = sorted.filter { (_, sim) -> sim >= threshold }
        logcat { "AiHistorySearch (embeddings): ${passing.size}/${entries.size} pass ${"%.3f".format(threshold)} (top=${"%.3f".format(topScore)} floor=$SIMILARITY_MIN_THRESHOLD margin=$SIMILARITY_MARGIN)" }
        return passing.map { (entry, _) -> entry }
    }

    private fun embed(text: String): Embedding =
        embedder.embed(text).embeddingResult().embeddings().first()

    companion object {
        private const val MODEL_ASSET = "universal_sentence_encoder_lite.tflite"
        // USE compresses all English text into a high baseline (~0.68–0.75 even for unrelated pairs).
        // A fixed threshold can't reliably separate signal from noise, so we use a dynamic threshold:
        // keep entries within SIMILARITY_MARGIN of the top score, but never below SIMILARITY_MIN_THRESHOLD.
        private const val SIMILARITY_MIN_THRESHOLD = 0.75
        private const val SIMILARITY_MARGIN = 0.10
    }
}
