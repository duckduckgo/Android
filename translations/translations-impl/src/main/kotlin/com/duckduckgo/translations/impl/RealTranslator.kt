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
import com.duckduckgo.translations.api.DetectedLanguage
import com.duckduckgo.translations.api.ModelDownloadStatus
import com.duckduckgo.translations.api.TranslationModelManager
import com.duckduckgo.translations.api.TranslationStatus
import com.duckduckgo.translations.api.Translator
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import io.github.marcosholgado.translatekit.ModelSpec
import io.github.marcosholgado.translatekit.TranslateKit
import io.github.marcosholgado.translatekit.TranslationModel
import kotlinx.coroutines.CoroutineScope
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
            // Nothing translatable on the page (or everything is already translated): a no-op success.
            emit(TranslationStatus.Completed)
            return@flow
        }

        // Block fragments translate as HTML (engine splits sentences and re-projects inline tags); orphan
        // text nodes are plain text. Both are written back in place via the WebView.
        val blockResult = translateBatchOrEmpty(collected.blocks, sourceLanguage, targetLanguage, isHtml = true)
        val textResult = translateBatchOrEmpty(collected.texts, sourceLanguage, targetLanguage, isHtml = false)
        when {
            blockResult.isFailure -> emit(TranslationStatus.Failed(blockResult.failureMessage()))
            textResult.isFailure -> emit(TranslationStatus.Failed(textResult.failureMessage()))
            else -> {
                applyTranslations(
                    webView,
                    blockTranslations = blockResult.getOrDefault(emptyList()),
                    textTranslations = textResult.getOrDefault(emptyList()),
                )
                emit(TranslationStatus.Completed)
            }
        }
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

    private suspend fun translateBatchOrEmpty(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        isHtml: Boolean,
    ): Result<List<String>> =
        if (texts.isEmpty()) {
            Result.success(emptyList())
        } else {
            translateBatch(texts, sourceLanguage, targetLanguage, isHtml)
        }

    private fun Result<List<String>>.failureMessage(): String =
        exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "Translation failed"

    private suspend fun collectTranslationUnits(webView: WebView): CollectedTranslationUnits {
        val encoded = evaluateJavascript(webView, TranslationDomJs.COLLECT_UNITS)
        if (encoded == null || encoded.trim().let { it.isEmpty() || it == "null" }) {
            return CollectedTranslationUnits(emptyList(), emptyList())
        }
        return try {
            val obj = JSONObject(encoded)
            CollectedTranslationUnits(
                blocks = obj.optJSONArray("blocks").toStringList(),
                texts = obj.optJSONArray("texts").toStringList(),
            )
        } catch (_: Exception) {
            CollectedTranslationUnits(emptyList(), emptyList())
        }
    }

    private suspend fun applyTranslations(
        webView: WebView,
        blockTranslations: List<String>,
        textTranslations: List<String>,
    ) {
        val blocksJson = JSONArray(blockTranslations).toString()
        val textsJson = JSONArray(textTranslations).toString()
        evaluateJavascript(webView, TranslationDomJs.applyTranslations(blocksJson, textsJson))
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
        val blocks: List<String>,
        val texts: List<String>,
    )
}
