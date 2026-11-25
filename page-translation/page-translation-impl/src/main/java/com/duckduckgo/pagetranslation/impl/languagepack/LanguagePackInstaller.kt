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

package com.duckduckgo.pagetranslation.impl.languagepack

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sha256
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.languagepack.RealLanguagePackFetcher.Companion.COMPRESSED_FILE_SUFFIX
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import com.duckduckgo.pagetranslation.impl.translatekit.TranslateKitLibrary
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.io.File
import java.io.IOException
import javax.inject.Inject

interface LanguagePackInstaller {
    /**
     * Checks the files [LanguagePackFetcher] downloaded into [stagingDirectory] against [model], decompresses them and moves
     * the pack to [packDirectory] if the manifest still lists [model]'s version for [pair]. Returns false, and deletes
     * [stagingDirectory], when the pack isn't installed.
     */
    suspend fun install(pair: String, model: TranslationModel, stagingDirectory: File, packDirectory: File): Boolean
}

@ContributesBinding(AppScope::class)
class RealLanguagePackInstaller @Inject constructor(
    private val translateKit: TranslateKitLibrary,
    private val store: ModelsManifestStore,
    private val dispatchers: DispatcherProvider,
) : LanguagePackInstaller {
    override suspend fun install(
        pair: String,
        model: TranslationModel,
        stagingDirectory: File,
        packDirectory: File,
    ): Boolean = withContext(dispatchers.io()) {
        val isInstalled = try {
            translateKit.init()
            installFiles(pair, model, stagingDirectory, packDirectory)
        } catch (_: IOException) {
            false
        } catch (e: IllegalStateException) {
            // translate-kit throws it when its native library fails to initialize
            logcat(ERROR) { "TranslateKit failed to initialize: ${e.asLog()}" }
            false
        }

        if (!isInstalled) {
            // a stopped job must not touch the staging directory, because a Retry's job may already be using it
            currentCoroutineContext().ensureActive()
            stagingDirectory.deleteRecursively()
            logcat(ERROR) { "Could not install page translation language pack" }
        }

        isInstalled
    }

    private suspend fun installFiles(pair: String, model: TranslationModel, stagingDirectory: File, packDirectory: File): Boolean {
        for ((key, file) in model.files) {
            currentCoroutineContext().ensureActive()
            val compressedFile = stagingDirectory.resolve(key + COMPRESSED_FILE_SUFFIX)
            val decompressedFile = stagingDirectory.resolve(key)

            if (!compressedFile.matches(file.bytes, file.sha256)) return false
            currentCoroutineContext().ensureActive()
            translateKit.decompressZstd(compressedFile, decompressedFile)
            if (!decompressedFile.matches(file.decompressedSize, file.decompressedHash)) return false

            currentCoroutineContext().ensureActive()
            compressedFile.delete()
        }

        currentCoroutineContext().ensureActive()
        stagingDirectory.resolve(MANIFEST_ENTRY_FILE_NAME).writeText(adapter.toJson(model))

        // the manifest can drop the pair or move it to another version while the pack downloads
        if (store.readManifest()?.models?.get(pair)?.version != model.version) return false

        currentCoroutineContext().ensureActive()
        return stagingDirectory.renameTo(packDirectory)
    }

    private fun File.matches(size: Long, sha256: String): Boolean = length() == size && this.sha256().equals(sha256, ignoreCase = true)

    companion object {
        const val MANIFEST_ENTRY_FILE_NAME = "manifest-entry.json"

        private val adapter: JsonAdapter<TranslationModel> = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(TranslationModel::class.java)
    }
}
