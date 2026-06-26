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

import kotlinx.coroutines.flow.Flow

/**
 * Manages the on-device translation models that [Translator] needs. A language pair can only be
 * translated once its model has been downloaded; callers check [isModelDownloaded] and, if needed,
 * trigger [downloadModel] before translating.
 */
interface TranslationModelManager {

    /**
     * Whether the model for the [sourceLanguage]→[targetLanguage] pair is already present on the
     * device (ISO 639-1 codes, e.g. "en", "es").
     */
    suspend fun isModelDownloaded(sourceLanguage: String, targetLanguage: String): Boolean

    /**
     * Download the model for the [sourceLanguage]→[targetLanguage] pair, emitting progress until it
     * completes or fails. Collecting the flow starts the download; cancelling it cancels the download.
     * A no-op completing immediately if the model is already present.
     */
    fun downloadModel(sourceLanguage: String, targetLanguage: String): Flow<ModelDownloadStatus>
}

sealed class ModelDownloadStatus {

    /** Download underway; [percent] is 0..100, or null when the total size is unknown. */
    data class InProgress(val percent: Int?) : ModelDownloadStatus()

    data object Completed : ModelDownloadStatus()

    data class Failed(val reason: String? = null) : ModelDownloadStatus()
}
