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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.translations.api.ModelDownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import logcat.logcat
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject

class ModelDownloader @Inject constructor(
    private val service: TranslationModelService,
    private val modelStore: ModelStore,
    private val dispatchers: DispatcherProvider,
) {

    fun download(sourceLanguage: String, targetLanguage: String): Flow<ModelDownloadStatus> = flow {
        val manifest = service.manifest(MODELS_JSON_URL)
        val key = "${sourceLanguage.lowercase()}-${targetLanguage.lowercase()}"
        val variant = manifest.models[key]
            ?.filter { it.releaseStatus == RELEASE_STATUS }
            ?.let { releases ->
                ARCHITECTURE_PREFERENCE.firstNotNullOfOrNull { arch ->
                    releases.firstOrNull {
                        it.architecture == arch
                    }
                } ?: releases.firstOrNull()
            }
        if (variant == null) {
            emit(ModelDownloadStatus.Failed("No released model for $key"))
            return@flow
        }

        val finalDir = modelStore.modelDir(sourceLanguage, targetLanguage)
        val tmpDir = File(finalDir.parentFile, "${finalDir.name}.tmp")
        tmpDir.deleteRecursively()
        if (!tmpDir.mkdirs()) {
            emit(ModelDownloadStatus.Failed("Could not create ${tmpDir.path}"))
            return@flow
        }

        try {
            emit(ModelDownloadStatus.InProgress(0))
            for (file in variant.files.requiredFiles()) {
                fetchAndExtract(manifest.baseUrl, file, tmpDir)
            }
            finalDir.deleteRecursively()
            if (!tmpDir.renameTo(finalDir)) {
                throw IOException("Could not move model into place at ${finalDir.path}")
            }
            logcat { "Downloaded translation model $key (${variant.architecture})" }
            emit(ModelDownloadStatus.Completed)
        } catch (e: Exception) {
            tmpDir.deleteRecursively()
            logcat { "Translation model download failed for $key: ${e.message}" }
            emit(ModelDownloadStatus.Failed(e.message))
        }
    }.flowOn(dispatchers.io())

    // Streams the (gzipped) file, decompresses to [destDir], and — for files that declare one —
    // verifies the SHA-256 of the decompressed bytes. Emits progress while the sized model downloads.
    private suspend fun FlowCollector<ModelDownloadStatus>.fetchAndExtract(
        baseUrl: String,
        file: ModelFile,
        destDir: File,
    ) {
        val outFile = File(destDir, file.path.substringAfterLast('/').removeSuffix(GZIP_SUFFIX))
        val digest = file.uncompressedHash?.let { MessageDigest.getInstance("SHA-256") }
        var lastPercent = -1

        service.downloadFile("$baseUrl/${file.path}").byteStream().use { raw ->
            GZIPInputStream(raw).use { gzip ->
                outFile.outputStream().use { out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        val read = gzip.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        written += read
                        val size = file.uncompressedSize
                        if (size != null && size > 0) {
                            val percent = (written * 100 / size).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                emit(ModelDownloadStatus.InProgress(percent))
                            }
                        }
                    }
                }
            }
        }

        if (digest != null) {
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha.equals(file.uncompressedHash, ignoreCase = true)) {
                throw IOException("Incorrect sha for ${file.path}")
            }
        }
    }

    private fun ModelFiles.requiredFiles(): List<ModelFile> =
        listOfNotNull(model, lexicalShortlist, vocab, srcVocab, trgVocab)

    companion object {
        private const val MODELS_JSON_URL =
            "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json"
        private const val RELEASE_STATUS = "Release"

        // Quality-first: base-memory is Mozilla's mobile-tuned variant (~30 MB) and matches or beats
        // base while being smaller; tiny (~16 MB) is the lightweight fallback; base is rarely worth it.
        private val ARCHITECTURE_PREFERENCE = listOf("base-memory", "tiny", "base")
        private const val GZIP_SUFFIX = ".gz"
    }
}
