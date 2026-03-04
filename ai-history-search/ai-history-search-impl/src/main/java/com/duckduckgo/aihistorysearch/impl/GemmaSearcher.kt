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
import com.duckduckgo.history.api.HistoryEntry
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.net.URL
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

/**
 * Shadow path that runs Gemma 3 1B IT on-device via the MediaPipe LLM Inference Task.
 * Works on any Android device with ≥4 GB RAM (~90% coverage).
 *
 * Nothing leaves the device. Produces both a ranked list and a synthesized answer,
 * logcatted only — the caller always falls through to the next ranking path so Duck.ai
 * opens normally. PoC to evaluate on-device LLM quality and latency without UI work.
 *
 * The model (~529 MB) is downloaded automatically to [modelPath] on the first @history
 * query that has [gemmaEnabled] ON. Subsequent calls return null while downloading;
 * inference begins on the next call after the download completes.
 */
internal class GemmaSearcher(
    private val context: Context?,
    private val modelPath: String,
) {
    /** Production constructor: derives model path from app files dir. */
    constructor(context: Context) : this(
        context = context,
        modelPath = File(context.filesDir, "models/$MODEL_FILENAME").absolutePath,
    )

    /** Test constructor: bypasses context for unit tests that only exercise [buildPrompt]. */
    constructor(modelPath: String) : this(
        context = null,
        modelPath = modelPath,
    )

    @Volatile private var llmInference: LlmInference? = null
    private val isDownloading = AtomicBoolean(false)
    private val downloaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Runs on-device inference. Never throws — all outcomes are logcatted.
     * The caller always falls through to the next path regardless.
     */
    suspend fun search(query: String, entries: List<HistoryEntry.VisitedPage>) {
        try {
            val model = getOrLoadModel() ?: return
            val prompt = buildPrompt(query, entries)
            val start = System.currentTimeMillis()
            val response = withContext(Dispatchers.IO) { model.generateResponse(prompt) }
            val ms = System.currentTimeMillis() - start
            logcat { "GemmaSearcher: result in ${ms}ms:\n$response" }
        } catch (e: Exception) {
            logcat { "GemmaSearcher: error — ${e.message}" }
        }
    }

    private suspend fun getOrLoadModel(): LlmInference? {
        llmInference?.let { return it }
        return withContext(Dispatchers.IO) {
            val ctx = context ?: return@withContext null
            val file = File(modelPath)
            when {
                file.exists() -> loadModel(ctx, file)
                isDownloading.get() -> {
                    logcat { "GemmaSearcher: model download in progress, skipping this query" }
                    null
                }
                else -> {
                    startDownload()
                    null
                }
            }
        }
    }

    private fun loadModel(ctx: Context, file: File): LlmInference? {
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(512)
                .build()
            LlmInference.createFromOptions(ctx, options).also { llmInference = it }
        } catch (e: Exception) {
            logcat { "GemmaSearcher: failed to load model — ${e.message}" }
            null
        }
    }

    private fun startDownload() {
        if (!isDownloading.compareAndSet(false, true)) return
        downloaderScope.launch {
            val dest = File(modelPath)
            val partial = File("$modelPath.part")
            try {
                dest.parentFile?.mkdirs()
                logcat { "GemmaSearcher: downloading model (~529 MB) from $DOWNLOAD_URL" }
                URL(DOWNLOAD_URL).openStream().use { input ->
                    partial.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                partial.renameTo(dest)
                logcat { "GemmaSearcher: model ready — trigger @history again to run inference" }
            } catch (e: Exception) {
                partial.delete()
                logcat { "GemmaSearcher: download failed — ${e.message}" }
            } finally {
                isDownloading.set(false)
            }
        }
    }

    internal fun buildPrompt(query: String, entries: List<HistoryEntry.VisitedPage>): String {
        val formatter = DateTimeFormatter.ofPattern("MMM d")
        val history = entries.take(MAX_ENTRIES).joinToString("\n") { entry ->
            val date = entry.visits.maxOrNull()?.format(formatter) ?: "?"
            "- ${entry.title} — ${entry.url} ($date)"
        }
        return """
            Here are pages from my browser history:
            $history

            Question: $query

            1. List the 3–5 most relevant pages in order of relevance, with a one-sentence reason for each.
            2. Write a 2–3 sentence summary of what these pages suggest about my research on this topic.
            If nothing is relevant, say so clearly.
        """.trimIndent()
    }

    companion object {
        internal const val MAX_ENTRIES = 30
        const val MODEL_FILENAME = "gemma3-1b-it-int4.task"
        private const val DOWNLOAD_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"
    }
}
