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

package com.duckduckgo.translations.impl

import android.content.Context
import android.webkit.WebView
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.translatekit.ModelSpec
import com.duckduckgo.translatekit.TranslateKit
import com.duckduckgo.translatekit.TranslationModel
import com.duckduckgo.translations.api.DetectedLanguage
import com.duckduckgo.translations.api.ModelDownloadStatus
import com.duckduckgo.translations.api.TranslationModelManager
import com.duckduckgo.translations.api.TranslationStatus
import com.duckduckgo.translations.api.Translator
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import javax.inject.Inject
import kotlin.coroutines.resume

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTranslator @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val modelStore: ModelStore,
    private val modelManager: TranslationModelManager,
) : Translator {

    // A single loaded model is cached in memory; loadModel mmaps native memory, so we keep one handle
    // and close it when switching pairs. The mutex serialises load/translate against the cached handle.
    private val modelLock = Mutex()
    private var loadedPairId: String? = null
    private var loadedModel: TranslationModel? = null

    init {
        // Load the native engine lazily and off the main thread. init() is idempotent and crash-safe:
        // on an unsupported ABI it catches the link error and leaves the engine uninitialised.
        if (isDeviceSupported()) {
            appCoroutineScope.launch(dispatchers.io()) {
                TranslateKit.init(context)
                logcat { "TranslateKit init complete; isInitialized=${TranslateKit.isInitialized()}" }
            }
        }
    }

    override fun isAvailable(): Boolean = isDeviceSupported() && TranslateKit.isInitialized()

    override suspend fun detectPageLanguage(webView: WebView): Result<DetectedLanguage?> {
        val sample = decodeJavascriptStringResult(evaluateJavascript(webView, TranslationDomJs.DETECT_SAMPLE))
        if (sample.isNullOrBlank()) return Result.success(null)
        return detectLanguage(sample).map { it }
    }

    private suspend fun detectLanguage(text: String): Result<DetectedLanguage> = withContext(dispatchers.io()) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("On-device translation engine is unavailable"))
        }
        runCatching {
            val result = TranslateKit.detectLanguage(text)
            DetectedLanguage(language = result.language, confidence = result.confidence)
        }
    }

    override fun translatePage(
        webView: WebView,
        sourceLanguage: String,
        targetLanguage: String,
    ): Flow<TranslationStatus> = flow {
        // Models are downloaded on demand: if the pair isn't present, fetch it (surfacing progress) first.
        if (!modelManager.isModelDownloaded(sourceLanguage, targetLanguage)) {
            var downloadError: String? = null
            modelManager.downloadModel(sourceLanguage, targetLanguage).collect { status ->
                when (status) {
                    is ModelDownloadStatus.InProgress -> emit(TranslationStatus.DownloadingModel(status.percent))
                    is ModelDownloadStatus.Failed -> downloadError = status.reason ?: "Model download failed"
                    ModelDownloadStatus.Completed -> {}
                }
            }
            downloadError?.let {
                emit(TranslationStatus.Failed(it))
                return@flow
            }
        }

        emit(TranslationStatus.Translating)
        // Walk the live DOM, collecting block fragments (innerHTML) and orphan text nodes (nodeValue) and
        // stashing references for write-back.
        val collected = collectTranslationUnits(webView)
        if (collected.blocks.isEmpty() && collected.texts.isEmpty()) {
            // Nothing translatable yet (or everything is already translated). Still a success — and we keep
            // watching, because a shell page may hydrate its content in afterwards.
            emit(TranslationStatus.Completed)
            watchAndTranslateNewContent(webView, sourceLanguage, targetLanguage)
            return@flow
        }

        // Orphan text nodes are few and cheap; translate them in one batch up front (isHtml=false) so the
        // visible page is fully translated once the first block chunk lands.
        if (collected.texts.isNotEmpty()) {
            val textResult = translateBatch(collected.texts, sourceLanguage, targetLanguage, isHtml = false)
            textResult.exceptionOrNull()?.let {
                emit(TranslationStatus.Failed(textResult.failureMessage()))
                return@flow
            }
            applyTexts(webView, textResult.getOrDefault(emptyList()))
        }

        // Block fragments are the bulk of the work, so translate them viewport-first in chunks and write each
        // chunk back as it lands (isHtml=true; the engine re-projects inline tags). The user sees the visible
        // page translated within the first chunk; the rest — including off-screen and hidden content — streams
        // in behind it. We report Completed once that first chunk is in place so the progress dialog can
        // dismiss, then keep filling the page in the background.
        val order = viewportFirstOrder(collected.blockTops, collected.viewportTop, collected.viewportHeight)
        var reportedCompleted = false
        for ((chunkIndex, indices) in order.chunked(BLOCK_CHUNK_SIZE).withIndex()) {
            val chunkHtmls = indices.map { collected.blocks[it] }
            val chunkResult = translateBatch(chunkHtmls, sourceLanguage, targetLanguage, isHtml = true)
            if (chunkResult.isFailure) {
                // Fail loudly only while the user is still waiting on the first (visible) chunk; once we've
                // reported Completed the visible page is already translated, so later failures just stop.
                if (!reportedCompleted) emit(TranslationStatus.Failed(chunkResult.failureMessage()))
                return@flow
            }
            applyBlocks(webView, indices, chunkResult.getOrDefault(emptyList()))
            if (chunkIndex == 0) {
                reportedCompleted = true
                emit(TranslationStatus.Completed)
            }
        }

        // The page is translated; now keep it that way. Many sites inject text after load — island hydration
        // (the Guardian re-renders English nodes over our translation when a deferred carousel scrolls into
        // view), lazy sections, infinite scroll. This suspends until collection is cancelled (the user
        // navigates away, the tab is closed, the Fragment is destroyed), so it self-terminates with the page.
        watchAndTranslateNewContent(webView, sourceLanguage, targetLanguage)
    }

    // Installs the DOM MutationObserver and polls its generation counter; on a change (debounced, since
    // hydration arrives in bursts) re-runs collect→translate→apply over just the newly added/changed nodes.
    // Loops until the document is replaced (poll returns -1) or the calling coroutine is cancelled.
    private suspend fun watchAndTranslateNewContent(
        webView: WebView,
        sourceLanguage: String,
        targetLanguage: String,
    ) {
        try {
            evaluateJavascript(webView, TranslationDomJs.INSTALL_OBSERVER)
            var lastGeneration = readGeneration(webView) ?: return
            while (true) {
                delay(OBSERVER_POLL_INTERVAL_MS)
                val generation = readGeneration(webView) ?: break // observer gone => document replaced
                if (generation == lastGeneration) continue
                // Let the burst of mutations settle before scanning so we translate hydrated content once.
                delay(OBSERVER_DEBOUNCE_MS)
                val settled = readGeneration(webView) ?: break
                if (settled != generation) {
                    lastGeneration = settled // still changing; reassess on the next tick
                    continue
                }
                translateNewlyAddedContent(webView, sourceLanguage, targetLanguage)
                // Our own write-backs may have bumped the counter; resync so we don't immediately re-scan.
                lastGeneration = readGeneration(webView) ?: break
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { evaluateJavascript(webView, TranslationDomJs.DISCONNECT_OBSERVER) }
            }
        }
    }

    // Re-collects the (now only newly added/changed) units and translates them in place, viewport-first.
    // Silent: the dialog is long gone, so failures are logged and the watch loop carries on.
    private suspend fun translateNewlyAddedContent(
        webView: WebView,
        sourceLanguage: String,
        targetLanguage: String,
    ) {
        val collected = collectTranslationUnits(webView)
        if (collected.blocks.isEmpty() && collected.texts.isEmpty()) return

        if (collected.texts.isNotEmpty()) {
            val textResult = translateBatch(collected.texts, sourceLanguage, targetLanguage, isHtml = false)
            if (textResult.isFailure) return
            applyTexts(webView, textResult.getOrDefault(emptyList()))
        }
        val order = viewportFirstOrder(collected.blockTops, collected.viewportTop, collected.viewportHeight)
        for (indices in order.chunked(BLOCK_CHUNK_SIZE)) {
            val chunkHtmls = indices.map { collected.blocks[it] }
            val chunkResult = translateBatch(chunkHtmls, sourceLanguage, targetLanguage, isHtml = true)
            if (chunkResult.isFailure) return
            applyBlocks(webView, indices, chunkResult.getOrDefault(emptyList()))
        }
    }

    // Reads the observer's generation counter. null means "stop watching": the observer is gone (-1, the
    // document was replaced) or the result was unreadable (the WebView is tearing down).
    private suspend fun readGeneration(webView: WebView): Int? {
        val raw = evaluateJavascript(webView, TranslationDomJs.POLL_GENERATION)?.trim() ?: return null
        val value = raw.toDoubleOrNull()?.toInt() ?: return null
        return value.takeIf { it >= 0 }
    }

    // Orders block indices so currently-visible blocks translate first, then off-screen blocks nearest the
    // viewport, then blocks that aren't laid out (hidden) last — matching what the user is looking at.
    private fun viewportFirstOrder(
        blockTops: List<Double>,
        viewportTop: Double,
        viewportHeight: Double,
    ): List<Int> {
        val viewportBottom = viewportTop + viewportHeight
        fun group(top: Double): Int = when {
            top >= HIDDEN_BLOCK_TOP -> 2
            top in viewportTop..viewportBottom -> 0
            else -> 1
        }
        fun distance(top: Double): Double = when {
            top < viewportTop -> viewportTop - top
            top > viewportBottom -> top - viewportBottom
            else -> 0.0
        }
        return blockTops.indices.sortedWith(
            compareBy({ group(blockTops[it]) }, { if (group(blockTops[it]) == 1) distance(blockTops[it]) else blockTops[it] }),
        )
    }

    // Translate a batch of texts in one call, returning them in order. Impl-internal: only page translation
    // needs it, and it stays off the public Translator surface.
    private suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        isHtml: Boolean,
    ): Result<List<String>> = withContext(dispatchers.io()) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("On-device translation engine is unavailable"))
        }
        runCatching {
            modelLock.withLock {
                val model = loadedModelFor(sourceLanguage, targetLanguage)
                model.translate(texts, isHtml).map { it.text }
            }
        }.onFailure {
            logcat(ERROR) { "Batch translation failed ($sourceLanguage-$targetLanguage, ${texts.size} nodes): ${it.asLog()}" }
        }
    }

    private fun Result<List<String>>.failureMessage(): String =
        exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "Translation failed"

    private suspend fun collectTranslationUnits(webView: WebView): CollectedTranslationUnits {
        val encoded = evaluateJavascript(webView, TranslationDomJs.COLLECT_UNITS)
        if (encoded == null || encoded.trim().let { it.isEmpty() || it == "null" }) {
            return CollectedTranslationUnits()
        }
        return try {
            val obj = JSONObject(encoded)
            CollectedTranslationUnits(
                blocks = obj.optJSONArray("blocks").toStringList(),
                blockTops = obj.optJSONArray("blockTops").toDoubleList(),
                texts = obj.optJSONArray("texts").toStringList(),
                viewportTop = obj.optDouble("viewportTop", 0.0),
                viewportHeight = obj.optDouble("viewportHeight", 0.0),
            )
        } catch (_: Exception) {
            CollectedTranslationUnits()
        }
    }

    private suspend fun applyBlocks(webView: WebView, indices: List<Int>, translations: List<String>) {
        val indicesJson = JSONArray(indices).toString()
        val translationsJson = JSONArray(translations).toString()
        evaluateJavascript(webView, TranslationDomJs.applyBlocks(indicesJson, translationsJson))
    }

    private suspend fun applyTexts(webView: WebView, translations: List<String>) {
        val translationsJson = JSONArray(translations).toString()
        evaluateJavascript(webView, TranslationDomJs.applyTexts(translationsJson))
    }

    // Evaluates [js] in [webView] and resumes with the raw JSON-encoded result. evaluateJavascript must be
    // called on the main thread; the native translate work stays off it.
    private suspend fun evaluateJavascript(webView: WebView, js: String): String? =
        withContext(dispatchers.main()) {
            suspendCancellableCoroutine<String?> { continuation ->
                webView.evaluateJavascript(js) { result ->
                    continuation.resume(result)
                }
            }
        }

    /** Decodes a JSON-encoded string from [WebView.evaluateJavascript] (escapes quotes, newlines, etc.). */
    private fun decodeJavascriptStringResult(encoded: String?): String? {
        if (encoded == null) return null
        val trimmed = encoded.trim()
        if (trimmed.isEmpty() || trimmed == "null") return null
        return try {
            when (val value = JSONTokener(trimmed).nextValue()) {
                is String -> value.takeIf { it.isNotEmpty() }
                else -> null
            }
        } catch (_: Exception) {
            trimmed.trim('"').takeIf { it.isNotEmpty() && it != "null" }
        }
    }

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }

    private fun JSONArray?.toDoubleList(): List<Double> =
        if (this == null) emptyList() else List(length()) { getDouble(it) }

    // Returns the loaded handle for the pair, loading it (and closing any previously loaded one) on a
    // cache miss. Must be called under [modelLock]. Throws if the model has not been downloaded.
    private fun loadedModelFor(sourceLanguage: String, targetLanguage: String): TranslationModel {
        val pairId = "${sourceLanguage.lowercase()}${targetLanguage.lowercase()}"
        loadedModel?.let { if (loadedPairId == pairId) return it }

        val files = modelStore.resolve(sourceLanguage, targetLanguage)
            ?: throw IllegalStateException("Model not downloaded for $sourceLanguage-$targetLanguage")
        val model = TranslateKit.loadModel(
            ModelSpec(
                sourceLang = sourceLanguage,
                targetLang = targetLanguage,
                modelPath = files.modelPath,
                vocabPaths = files.vocabPaths,
                shortlistPath = files.shortlistPath,
                configYaml = null,
            ),
        )
        loadedModel?.let { TranslateKit.unloadModel(it) }
        loadedModel = model
        loadedPairId = pairId
        logcat { "Loaded translation model $pairId" }
        return model
    }

    // translate-kit-android ships a native engine that requires API 28+. Below this we never touch the
    // library, so the engine is never loaded on unsupported OS versions.
    private val MIN_SUPPORTED_SDK = 28

    private fun isDeviceSupported(): Boolean = appBuildConfig.sdkInt >= MIN_SUPPORTED_SDK

    private data class CollectedTranslationUnits(
        val blocks: List<String> = emptyList(),
        val blockTops: List<Double> = emptyList(),
        val texts: List<String> = emptyList(),
        val viewportTop: Double = 0.0,
        val viewportHeight: Double = 0.0,
    )

    companion object {
        // Blocks translate in chunks so the visible page lands fast and the rest streams in behind it.
        private const val BLOCK_CHUNK_SIZE = 50

        // Blocks not laid out (display:none, detached) report this top from COLLECT_UNITS and sort last.
        private const val HIDDEN_BLOCK_TOP = 1_000_000_000.0

        // How often to poll the MutationObserver's generation counter while watching for new content, and how
        // long to wait for a burst of mutations (e.g. island hydration) to settle before re-scanning.
        private const val OBSERVER_POLL_INTERVAL_MS = 750L
        private const val OBSERVER_DEBOUNCE_MS = 400L
    }
}
