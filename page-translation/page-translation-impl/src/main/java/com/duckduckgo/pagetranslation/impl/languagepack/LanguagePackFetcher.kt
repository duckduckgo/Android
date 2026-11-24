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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject
import kotlin.coroutines.resumeWithException

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
class RealLanguagePackFetcher internal constructor(
    private val client: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
    private val getUsableSpace: (File) -> Long,
) : LanguagePackFetcher {
    @Inject
    constructor(
        @ModelsHttpClient client: Lazy<OkHttpClient>,
        dispatchers: DispatcherProvider,
    ) : this(client, dispatchers, File::getUsableSpace)

    override suspend fun fetch(
        baseUrl: String,
        model: TranslationModel,
        stagingDirectory: File,
        onProgress: suspend (downloadedBytes: Long) -> Unit,
    ): FetchResult = withContext(dispatchers.io()) {
        if (!stagingDirectory.deleteRecursively() || !stagingDirectory.mkdirs()) {
            logcat(ERROR) { "Could not prepare the page translation language pack staging directory" }
            return@withContext FetchResult.FAILURE
        }

        val result = download(baseUrl, model, stagingDirectory, onProgress)
        // a failed download is never resumed, so its files would only take up space
        if (result != FetchResult.SUCCESS) stagingDirectory.deleteRecursively()

        result
    }

    private suspend fun download(
        baseUrl: String,
        model: TranslationModel,
        stagingDirectory: File,
        onProgress: suspend (downloadedBytes: Long) -> Unit,
    ): FetchResult {
        // the downloaded files and their decompressed copies sit side by side until the pack is installed
        val requiredBytes = model.files.values.sumOf { it.bytes + it.decompressedSize }
        if (getUsableSpace(stagingDirectory) < requiredBytes) return FetchResult.NOT_ENOUGH_STORAGE

        var downloadedBytes = 0L
        var reportedBytes = 0L
        try {
            for ((key, file) in model.files) {
                val url = (baseUrl + file.path).toHttpUrlOrNull() ?: return FetchResult.FAILURE

                client.value.newCall(Request.Builder().url(url).build()).await().use { response ->
                    if (response.code != HTTP_OK) {
                        logcat(ERROR) { "Page translation language pack request failed" }
                        return FetchResult.FAILURE
                    }

                    currentCoroutineContext().ensureActive()
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
            // a stopped job must not touch the staging directory, because a Retry's job may already be using it
            currentCoroutineContext().ensureActive()

            // another pair's download can fill the volume after the check above passed
            if (getUsableSpace(stagingDirectory) < requiredBytes - downloadedBytes) return FetchResult.NOT_ENOUGH_STORAGE

            logcat(ERROR) { "Could not download page translation language pack" }
            return FetchResult.FAILURE
        }

        return FetchResult.SUCCESS
    }

    private suspend fun copyWithProgress(input: InputStream, output: OutputStream, onBytes: suspend (copiedBytes: Int) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) return

            // checked after the blocking read, which doesn't see cancellation, so a stopped job never writes into a Retry's file
            currentCoroutineContext().ensureActive()
            output.write(buffer, 0, read)
            onBytes(read)
        }
    }

    // a blocking execute() would keep waiting for the server after the job is stopped
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) = continuation.resume(response) { response.close() }

                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)
            },
        )
    }

    companion object {
        const val COMPRESSED_FILE_SUFFIX = ".zst"

        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 256 * 1024L
    }
}
