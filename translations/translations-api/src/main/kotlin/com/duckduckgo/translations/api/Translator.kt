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

package com.duckduckgo.translations.api

import android.webkit.WebView
import kotlinx.coroutines.flow.Flow

/**
 * On-device, offline neural machine translation and language detection.
 *
 * All work happens locally — no text leaves the device. Availability is gated on both the
 * device (the native library must have loaded; it is unavailable on unsupported OS versions
 * and CPU architectures) and remote configuration, so callers must check [isAvailable] before
 * surfacing any translation UI.
 */
interface Translator {

    /**
     * Whether on-device translation is available right now: the native engine has loaded on
     * this device and the feature is enabled. When this returns `false`, no translation UI
     * should be shown and the other methods will fail.
     *
     * Safe to call at any time; cheap and non-blocking.
     */
    fun isAvailable(): Boolean

    /**
     * Best-effort offline detection of the dominant language of the page currently rendered in
     * [webView], sampled from its visible text.
     *
     * @return the [DetectedLanguage] on success (or `null` inside the result when the page had no
     * text to sample), or a failure if the engine is unavailable or detection could not be performed.
     */
    suspend fun detectPageLanguage(webView: WebView): Result<DetectedLanguage?>

    /**
     * Translate the page rendered in [webView] from [sourceLanguage] to [targetLanguage] (ISO 639-1
     * codes, e.g. "en", "es"), in place. Collecting the returned flow starts the work and emits its
     * progress: the model is downloaded on demand if missing, then the page content is translated and
     * written back. Cancelling collection cancels the work.
     *
     * The DOM is mutated directly — the page is never reloaded — so render mode, running scripts,
     * listeners and scroll position are preserved. Content already translated on a previous run is
     * skipped, so the flow is safe to re-run.
     */
    fun translatePage(
        webView: WebView,
        sourceLanguage: String,
        targetLanguage: String,
    ): Flow<TranslationStatus>
}

/**
 * The result of offline language detection.
 *
 * @property language the detected language as an ISO 639-1 code (e.g. "en", "es").
 * @property confidence the engine's confidence in [language], in the range `0.0`..`1.0`.
 */
data class DetectedLanguage(
    val language: String,
    val confidence: Float,
)

/** Progress of an in-place [Translator.translatePage] run. */
sealed class TranslationStatus {

    /** The language-pair model is being fetched; [percent] is 0..100, or null when the size is unknown. */
    data class DownloadingModel(val percent: Int?) : TranslationStatus()

    /** The model is ready and the page content is being translated and written back. */
    data object Translating : TranslationStatus()

    /** The page has been translated in place. */
    data object Completed : TranslationStatus()

    data class Failed(val reason: String? = null) : TranslationStatus()
}
