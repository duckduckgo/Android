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

package com.duckduckgo.pagetranslation.impl

import androidx.annotation.Keep

@Keep
internal data class ModelsManifest(
    val schemaVersion: Int,
    val generated: String? = null,
    val baseUrl: String,
    val models: Map<String, TranslationModel>,
)

@Keep
internal data class TranslationModel(
    val sourceLanguage: String,
    val targetLanguage: String,
    val version: String,
    val architecture: String,
    val totalBytes: Long,
    val files: Map<String, ModelFile>,
)

@Keep
internal data class ModelFile(
    val path: String,
    val bytes: Long,
    val sha256: String,
    val decompressedSize: Long,
    val decompressedHash: String,
)
