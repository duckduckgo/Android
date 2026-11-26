/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License ats
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.pagetranslation.impl.manifest

import androidx.annotation.Keep

@Keep
data class ModelsManifest(
    val schemaVersion: Int,
    val generated: String? = null,
    val baseUrl: String,
    val models: Map<String, TranslationModel>,
) {
    @Keep
    data class TranslationModel(
        val sourceLanguage: String,
        val targetLanguage: String,
        val version: String,
        val architecture: String,
        val totalBytes: Long,
        val files: Map<String, ModelFile>,
    )

    @Keep
    data class ModelFile(
        val path: String,
        val bytes: Long,
        val sha256: String,
        val decompressedSize: Long,
        val decompressedHash: String,
    ) {
        /**
         * The name of this file decompressed into an installed pack: its [key] with the format extension of [path],
         * e.g. "model.bin" for "es-en/model.esen.intgemm.alphas.bin.zst". translate-kit tells a file's format by its extension.
         */
        fun getInstalledFileName(key: String): String {
            val extension = path.substringAfterLast('/').removeSuffix(COMPRESSED_EXTENSION).substringAfterLast('.', missingDelimiterValue = "")

            return if (extension.isEmpty()) key else "$key.$extension"
        }

        private companion object {
            const val COMPRESSED_EXTENSION = ".zst"
        }
    }

    /** The keys of [TranslationModel.files], which also name the decompressed files in an installed pack. */
    companion object {
        const val MODEL_FILE = "model"
        const val LEXICAL_SHORTLIST_FILE = "lexicalShortlist"
        const val VOCAB_FILE = "vocab"
        const val SOURCE_VOCAB_FILE = "srcVocab"
        const val TARGET_VOCAB_FILE = "trgVocab"
    }
}
