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

/**
 * Shape of Mozilla's `models.json` (the prototype model source — see SPEC-2 §5.1a). [models] is keyed
 * by pair id, e.g. "en-es", each mapping to one variant per architecture.
 */
data class ModelsManifest(
    val baseUrl: String,
    val models: Map<String, List<ModelVariant>>,
)

data class ModelVariant(
    val architecture: String,
    val releaseStatus: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val files: ModelFiles,
)

/**
 * The files that make up a model. A pair has either a single shared [vocab] (the common case) or a
 * separate [srcVocab]/[trgVocab] pair. Only [model] carries an `uncompressedHash`/`uncompressedSize`.
 */
data class ModelFiles(
    val model: ModelFile,
    val lexicalShortlist: ModelFile,
    val vocab: ModelFile? = null,
    val srcVocab: ModelFile? = null,
    val trgVocab: ModelFile? = null,
)

data class ModelFile(
    val path: String,
    val uncompressedSize: Long? = null,
    val uncompressedHash: String? = null,
)
