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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject

/**
 * Owns the on-disk layout for downloaded models. The filesystem is the source of truth for what is
 * available — a model lives under `filesDir/translations/<pairId>/` and only exists there once its
 * download has fully completed (the downloader writes to a temp dir and atomically renames into
 * place), so a present directory containing a model binary means a usable model.
 */
interface ModelStore {
    fun modelDir(sourceLanguage: String, targetLanguage: String): File
    fun isDownloaded(sourceLanguage: String, targetLanguage: String): Boolean

    /** Resolves the on-disk files for a downloaded pair into what the engine needs, or null if absent. */
    fun resolve(sourceLanguage: String, targetLanguage: String): ResolvedModelFiles?
}

data class ResolvedModelFiles(
    val modelPath: String,
    val vocabPaths: List<String>,
    val shortlistPath: String,
)

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealModelStore @Inject constructor(
    private val context: Context,
) : ModelStore {

    private val rootDir: File
        get() = File(context.filesDir, MODELS_DIR_NAME)

    override fun modelDir(sourceLanguage: String, targetLanguage: String): File =
        File(rootDir, pairId(sourceLanguage, targetLanguage))

    override fun isDownloaded(sourceLanguage: String, targetLanguage: String): Boolean {
        val dir = modelDir(sourceLanguage, targetLanguage)
        return dir.isDirectory && dir.listFiles()?.any { it.isModelBinary() } == true
    }

    override fun resolve(sourceLanguage: String, targetLanguage: String): ResolvedModelFiles? {
        val files = modelDir(sourceLanguage, targetLanguage).listFiles()?.toList() ?: return null
        // Mozilla's naming: model.<pair>.intgemm.alphas.bin, lex.*.bin, and either vocab.<pair>.spm or
        // srcvocab/trgvocab.<pair>.spm. The shortlist also ends in .bin, so the model is matched by prefix.
        val model = files.firstOrNull { it.isModelBinary() } ?: return null
        val shortlist = files.firstOrNull { it.name.startsWith(SHORTLIST_PREFIX) } ?: return null
        // For src/trg pairs, "srcvocab" sorts before "trgvocab" — the source-then-target order the engine wants.
        val vocabs = files.filter { it.name.endsWith(VOCAB_SUFFIX) }.sortedBy { it.name }
        if (vocabs.isEmpty()) return null
        return ResolvedModelFiles(
            modelPath = model.absolutePath,
            vocabPaths = vocabs.map { it.absolutePath },
            shortlistPath = shortlist.absolutePath,
        )
    }

    private fun File.isModelBinary(): Boolean = name.startsWith(MODEL_PREFIX) && name.endsWith(MODEL_FILE_SUFFIX)

    // Concatenated lowercase pair id (e.g. "enes"), matching Mozilla's model filenames.
    private fun pairId(sourceLanguage: String, targetLanguage: String): String =
        "${sourceLanguage.lowercase()}${targetLanguage.lowercase()}"

    companion object {
        private const val MODELS_DIR_NAME = "translations"
        private const val MODEL_PREFIX = "model"
        private const val MODEL_FILE_SUFFIX = ".bin"
        private const val SHORTLIST_PREFIX = "lex"
        private const val VOCAB_SUFFIX = ".spm"
    }
}
