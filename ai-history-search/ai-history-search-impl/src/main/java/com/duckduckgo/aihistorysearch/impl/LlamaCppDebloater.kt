package com.duckduckgo.aihistorysearch.impl

import android.content.Context
import java.io.File
import logcat.logcat

/**
 * Query debloating: strips conversational padding from verbose queries, producing
 * a compact keyword-like form that BM25 and embeddings score better.
 *
 * Uses the same GGUF model as [LlamaCppSearcher] but caps output at [MAX_OUTPUT_TOKENS]
 * tokens — inference should complete in ~1–3 s vs ~10–20 s for full generation.
 *
 * Examples:
 *   "So I was reading something the other day about how eating less often might be
 *    healthy, something about fasting windows"  →  "intermittent fasting"
 *   "That article about the chemical in your brain that makes you feel good"
 *                                               →  "dopamine reward brain"
 *
 * Shares the [LlamaCppInference] instance with [LlamaCppSearcher] if constructed with
 * the same [inference] object (avoids loading the model twice).
 */
internal class LlamaCppDebloater(
    private val inference: LlamaCppInference,
) {
    constructor(context: Context) : this(
        LlamaCppInference(
            modelPath = File(context.filesDir, "models/${LlamaCppSearcher.MODEL_FILENAME}").absolutePath,
            nCtx = 512, // debloating needs very little context
        ),
    )

    /**
     * Returns a debloated query, or the original query unchanged if the model is
     * unavailable or the query looks already compact (≤ 5 tokens by whitespace).
     */
    suspend fun debloat(query: String): String {
        // Skip debloating for short queries — they're already keyword-like
        if (query.split(" ").size <= 5) {
            logcat { "LlamaCppDebloater: query is short, skipping debloating" }
            return query
        }
        return try {
            if (!inference.load()) return query
            val prompt = Companion.buildPrompt(query)
            val start = System.currentTimeMillis()
            val raw = inference.complete(prompt, maxNewTokens = MAX_OUTPUT_TOKENS).trim()
            val ms = System.currentTimeMillis() - start
            // Take only the first line (model sometimes adds explanation after the query)
            val debloated = raw.lines().firstOrNull { it.isNotBlank() }?.trim() ?: query
            logcat { "LlamaCppDebloater: '${query.take(60)}' → '$debloated' (${ms}ms)" }
            debloated.ifBlank { query }
        } catch (e: Exception) {
            logcat { "LlamaCppDebloater: error — ${e.message}" }
            query
        }
    }

    companion object {
        /** Max tokens to generate. 20 is enough for a 3–6 word search query. */
        internal const val MAX_OUTPUT_TOKENS = 20

        internal fun buildPrompt(query: String): String =
            """
            <start_of_turn>user
            Rewrite the following as a short, concise search query (3–6 words maximum). Output only the search query, nothing else.

            Input: $query
            <end_of_turn>
            <start_of_turn>model
            Output:
            """.trimIndent()
    }
}
