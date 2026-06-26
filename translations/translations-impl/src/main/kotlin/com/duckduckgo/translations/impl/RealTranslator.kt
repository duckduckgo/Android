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
import io.github.marcosholgado.translatekit.TranslateKit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat


@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTranslator @Inject constructor(
    private val context: Context,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : Translator {

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
        // Model loading and translation are wired up in a later step.
        Result.failure(NotImplementedError("On-device translation is not wired up yet"))
    }

    // translate-kit-android ships a native engine that requires API 28+. Below this we never touch the
    // library, so the engine is never loaded on unsupported OS versions.
    private val MIN_SUPPORTED_SDK = 28

    private fun isDeviceSupported(): Boolean = appBuildConfig.sdkInt >= MIN_SUPPORTED_SDK
}
