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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.translations.api.DetectedLanguage
import com.duckduckgo.translations.api.Translator
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import io.github.marcosholgado.translatekit.ModelSpec
import io.github.marcosholgado.translatekit.TranslateKit
import io.github.marcosholgado.translatekit.TranslationModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTranslator @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val modelStore: ModelStore,
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

    override suspend fun detectLanguage(text: String): Result<DetectedLanguage> = withContext(dispatchers.io()) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("On-device translation engine is unavailable"))
        }
        runCatching {
            val result = TranslateKit.detectLanguage(text)
            DetectedLanguage(language = result.language, confidence = result.confidence)
        }
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        isHtml: Boolean,
    ): Result<String> = withContext(dispatchers.io()) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("On-device translation engine is unavailable"))
        }
        runCatching {
            modelLock.withLock {
                val model = loadedModelFor(sourceLanguage, targetLanguage)
                model.translate(text, isHtml).text
            }
        }
    }

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
}
