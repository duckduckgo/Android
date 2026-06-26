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
     * Best-effort offline detection of the dominant language of [text].
     *
     * @return a [DetectedLanguage] on success, or a failure if the engine is unavailable or
     * detection could not be performed.
     */
    suspend fun detectLanguage(text: String): Result<DetectedLanguage>

    /**
     * Translate [text] from [sourceLanguage] to [targetLanguage] (ISO 639-1 codes, e.g. "en",
     * "es"). Set [isHtml] when [text] contains HTML markup that should be preserved.
     *
     * @return the translated text on success, or a failure if the engine is unavailable, the
     * language pair is not supported, or translation could not be performed.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        isHtml: Boolean = false,
    ): Result<String>
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
