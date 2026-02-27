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
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.GenAiException.ErrorCode
import com.google.mlkit.genai.prompt.Generation
import java.time.format.DateTimeFormatter
import logcat.logcat

/**
 * Shadow path that runs Gemini Nano entirely on-device via the ML Kit GenAI Prompt API.
 *
 * Nothing leaves the device. The on-device answer is logcatted only — the caller always
 * falls through to the next ranking path (embeddings → BM25 → full) so Duck.ai opens
 * normally. This is a PoC to evaluate on-device quality and latency without UI work.
 *
 * Requires Android API 26+. On unsupported devices the model is simply unavailable and
 * the method returns without logging anything meaningful.
 */
internal class GeminiNanoSearcher {

    /**
     * Attempts on-device inference with Gemini Nano. Never throws — all outcomes are
     * logcatted. The caller always falls through to the next ranking path regardless.
     */
    suspend fun search(query: String, entries: List<HistoryEntry.VisitedPage>) {
        try {
            val model = Generation.getClient()
            when (val status = model.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> logcat { "GeminiNano: UNAVAILABLE on this device" }
                FeatureStatus.DOWNLOADABLE -> {
                    logcat { "GeminiNano: DOWNLOADABLE — triggering background download" }
                    model.download().collect { downloadStatus ->
                        logcat { "GeminiNano: download status $downloadStatus" }
                    }
                }
                FeatureStatus.DOWNLOADING -> logcat { "GeminiNano: model is currently downloading" }
                FeatureStatus.AVAILABLE -> {
                    val prompt = buildPrompt(query, entries)
                    val start = System.currentTimeMillis()
                    val response = model.generateContent(prompt)
                    val ms = System.currentTimeMillis() - start
                    val text = response.candidates.firstOrNull()?.text ?: "<empty>"
                    logcat { "GeminiNano: result in ${ms}ms:\n$text" }
                }
                else -> logcat { "GeminiNano: unexpected status $status" }
            }
        } catch (e: GenAiException) {
            // errorCode maps to GenAiException.ErrorCode constants (IntDef).
            // NOT_AVAILABLE / NEEDS_SYSTEM_UPDATE / AICORE_INCOMPATIBLE are expected on
            // devices where Gemini Nano is not provisioned — treat them like UNAVAILABLE.
            when (e.errorCode) {
                ErrorCode.NOT_AVAILABLE,
                ErrorCode.NEEDS_SYSTEM_UPDATE,
                ErrorCode.AICORE_INCOMPATIBLE,
                -> logcat { "GeminiNano: UNAVAILABLE on this device (errorCode=${e.errorCode})" }
                else -> logcat { "GeminiNano: error (errorCode=${e.errorCode}) — ${e.message}" }
            }
        } catch (e: Exception) {
            logcat { "GeminiNano: unexpected error — ${e.message}" }
        }
    }

    private fun buildPrompt(query: String, entries: List<HistoryEntry.VisitedPage>): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val history = entries.take(MAX_ENTRIES).joinToString("\n") { entry ->
            val date = entry.visits.maxOrNull()?.format(formatter) ?: "?"
            "- ${entry.title} — ${entry.url} ($date)"
        }
        return """
            Here are pages from my browser history:
            $history

            Question: $query

            Identify the most relevant pages and summarize what they are about in relation to the question.
            If nothing is relevant, say so clearly.
        """.trimIndent()
    }

    companion object {
        private const val MAX_ENTRIES = 30  // ~4000 token budget; 30 entries fits comfortably
    }
}
