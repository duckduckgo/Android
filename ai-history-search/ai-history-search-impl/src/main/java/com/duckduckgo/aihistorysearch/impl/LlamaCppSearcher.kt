package com.duckduckgo.aihistorysearch.impl

import android.content.Context
import com.duckduckgo.history.api.HistoryEntry
import java.io.File
import java.time.format.DateTimeFormatter
import logcat.logcat

/**
 * Shadow eval path: runs llama.cpp (GGUF) for full history ranking.
 * Same prompt as [GemmaSearcher] — results are directly comparable.
 *
 * Model must be pushed to [modelPath] before use:
 *   huggingface-cli download bartowski/google_gemma-3-1b-it-GGUF \
 *       google_gemma-3-1b-it-Q4_K_M.gguf --local-dir .
 *   adb push google_gemma-3-1b-it-Q4_K_M.gguf /data/local/tmp/
 */
internal class LlamaCppSearcher(
    private val context: Context,
    private val modelPath: String = File(context.filesDir, "models/$MODEL_FILENAME").absolutePath,
) {
    private val inference by lazy { LlamaCppInference(modelPath, nCtx = 4096) }

    suspend fun search(query: String, entries: List<HistoryEntry.VisitedPage>) {
        val file = File(modelPath)
        if (!file.exists() || file.length() < MIN_MODEL_BYTES) {
            logcat { "LlamaCppSearcher: model not found at $modelPath — skipping" }
            return
        }
        try {
            if (!inference.load()) return
            val prompt = buildPrompt(query, entries)
            val start = System.currentTimeMillis()
            val response = inference.complete(prompt, maxNewTokens = 512)
            val ms = System.currentTimeMillis() - start
            logcat { "LlamaCppSearcher: result in ${ms}ms:\n$response" }
        } catch (e: Exception) {
            logcat { "LlamaCppSearcher: error — ${e.message}" }
        }
    }

    /** Eval-only: returns raw response string, or null on failure. */
    internal suspend fun searchForEval(
        query: String,
        entries: List<HistoryEntry.VisitedPage>,
    ): String? {
        return try {
            if (!inference.load()) return null
            val prompt = buildPrompt(query, entries)
            inference.complete(prompt, maxNewTokens = 512).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logcat { "LlamaCppSearcher.searchForEval: error — ${e.message}" }
            null
        }
    }

    /** Identical prompt to GemmaSearcher — enables direct quality comparison. */
    internal fun buildPrompt(query: String, entries: List<HistoryEntry.VisitedPage>): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val history = entries.take(MAX_ENTRIES).joinToString("\n") { entry ->
            val date = entry.visits.maxOrNull()?.format(formatter) ?: "?"
            buildString {
                append("- ${entry.title} — ${entry.url} ($date)")
                entry.h1?.takeIf { it.isNotBlank() && it != entry.title }?.let { append(" [$it]") }
                entry.description?.takeIf { it.isNotBlank() }?.let { append(": $it") }
                entry.chunkText?.takeIf { it.isNotBlank() }?.let { append("\n  ").append(it.take(300)) }
            }
        }
        // Gemma 3 chat template (same as GemmaSearcher)
        return """
            <start_of_turn>user
            You are a browser history search assistant.

            Browser history:
            $history

            Search query: $query

            Which of the above pages are directly relevant to the search query? A page is relevant only if its title, URL, description, or content directly addresses the query topic. Do not include pages about unrelated topics.

            List only the relevant pages, one per line:
            - [title] — [url]: one sentence explaining why it matches

            If no pages match, respond with exactly: Nothing relevant found.
            <end_of_turn>
            <start_of_turn>model
        """.trimIndent()
    }

    companion object {
        internal const val MAX_ENTRIES = 30
        const val MODEL_FILENAME = "google_gemma-3-1b-it-Q4_K_M.gguf"
        private const val MIN_MODEL_BYTES = 100 * 1024 * 1024L
    }
}
