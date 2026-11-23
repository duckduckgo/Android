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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.ModelsHttpClient
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject

interface LanguagePackFetcher {
    enum class FetchResult { SUCCESS, NOT_ENOUGH_STORAGE, FAILURE }

    /** Downloads every file of [model] into [stagingDirectory], replacing what an earlier run left there. */
    suspend fun fetch(
        baseUrl: String,
        model: TranslationModel,
        stagingDirectory: File,
        onProgress: suspend (downloadedBytes: Long) -> Unit,
    ): FetchResult
}

@ContributesBinding(AppScope::class)
class RealLanguagePackFetcher @Inject constructor(
    @ModelsHttpClient private val client: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
    private val getUsableSpace: (File) -> Long = File::getUsableSpace,
) : LanguagePackFetcher {
    override suspend fun fetch(
        baseUrl: String,
        model: TranslationModel,
        stagingDirectory: File,
        onProgress: suspend (downloadedBytes: Long) -> Unit,
    ): FetchResult = withContext(dispatchers.io()) {
        stagingDirectory.deleteRecursively()
        stagingDirectory.mkdirs()

        // the downloaded files and their decompressed copies sit side by side until the pack is installed
        val requiredBytes = model.files.values.sumOf { it.bytes + it.decompressedSize }
        if (getUsableSpace(stagingDirectory) < requiredBytes) return@withContext FetchResult.NOT_ENOUGH_STORAGE

        var downloadedBytes = 0L
        var reportedBytes = 0L
        try {
            for ((key, file) in model.files) {
                val url = (baseUrl + file.path).toHttpUrlOrNull() ?: return@withContext FetchResult.FAILURE

                client.value.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code != HTTP_OK) {
                        logcat(ERROR) { "Page translation language pack request failed" }
                        return@withContext FetchResult.FAILURE
                    }

                    stagingDirectory.resolve(key + COMPRESSED_FILE_SUFFIX).outputStream().use { output ->
                        copyWithProgress(response.body!!.byteStream(), output) { copiedBytes ->
                            downloadedBytes += copiedBytes
                            if (downloadedBytes - reportedBytes >= PROGRESS_STEP_BYTES) {
                                reportedBytes = downloadedBytes
                                onProgress(downloadedBytes)
                            }
                        }
                    }
                }
                reportedBytes = downloadedBytes
                onProgress(downloadedBytes)
            }
        } catch (_: IOException) {
            logcat(ERROR) { "Could not download page translation language pack" }
            return@withContext FetchResult.FAILURE
        }

        FetchResult.SUCCESS
    }

    // a blocking read ignores coroutine cancellation, so a stopped job would otherwise finish the whole file
    private suspend fun copyWithProgress(input: InputStream, output: OutputStream, onBytes: suspend (copiedBytes: Int) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read == -1) return

            output.write(buffer, 0, read)
            onBytes(read)
        }
    }

    companion object {
        const val COMPRESSED_FILE_SUFFIX = ".zst"

        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024L
    }
}
