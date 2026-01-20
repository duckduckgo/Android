/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.downloads.impl

import androidx.annotation.WorkerThread
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.downloads.api.DownloadFailReason.ConnectionRefused
import com.duckduckgo.downloads.api.DownloadFailReason.Other
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.impl.feature.FileDownloadFeature
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import logcat.asLog
import logcat.logcat
import okhttp3.ResponseBody
import okhttp3.internal.http2.StreamResetException
import okio.Buffer
import okio.sink
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random

class UrlFileDownloader @Inject constructor(
    private val downloadFileService: DownloadFileService,
    @Named("http1Fallback") private val downloadFileServiceHttp1: DownloadFileService,
    private val urlFileDownloadCallManager: UrlFileDownloadCallManager,
    private val cookieManagerWrapper: CookieManagerWrapper,
    private val fileDownloadFeature: FileDownloadFeature,
) {

    @WorkerThread
    fun downloadFile(
        pendingFileDownload: FileDownloader.PendingFileDownload,
        fileName: String,
        downloadCallback: DownloadCallback,
    ) {
        val url = pendingFileDownload.url
        val directory = pendingFileDownload.directory
        val downloadId = Random.nextLong()

        logcat { "Starting download $fileName / $url" }
        downloadCallback.onStart(
            DownloadItem(
                downloadId = downloadId,
                downloadStatus = STARTED,
                fileName = fileName,
                contentLength = 0,
                filePath = directory.path + File.separatorChar + fileName,
                createdAt = DatabaseDateFormatter.timestamp(),
            ),
        )

        val result = executeDownload(
            service = downloadFileService,
            pendingFileDownload = pendingFileDownload,
            fileName = fileName,
            directory = directory,
            downloadId = downloadId,
            downloadCallback = downloadCallback,
        )

        when (result) {
            is DownloadResult.Success -> {
                val file = directory.getOrCreate(fileName)
                // for file length we don't use body.contentLength() as it is not reliable. Eg. when downloading image from DDG search
                // as the link is a re-direct, contentLength() will be -1
                downloadCallback.onSuccess(downloadId, file.length(), file, pendingFileDownload.mimeType)
            }
            is DownloadResult.Cancelled -> {
                logcat { "Download $fileName cancelled" }
                downloadCallback.onCancel(downloadId)
                directory.getOrCreate(fileName).delete()
            }
            is DownloadResult.StreamResetError -> {
                logcat { "HTTP/2 stream reset error, retrying with HTTP/1.1 for $fileName" }
                directory.getOrCreate(fileName).delete()

                val retryResult = executeDownload(
                    service = downloadFileServiceHttp1,
                    pendingFileDownload = pendingFileDownload,
                    fileName = fileName,
                    directory = directory,
                    downloadId = downloadId,
                    downloadCallback = downloadCallback,
                )

                when (retryResult) {
                    is DownloadResult.Success -> {
                        val file = directory.getOrCreate(fileName)
                        downloadCallback.onSuccess(downloadId, file.length(), file, pendingFileDownload.mimeType)
                    }
                    is DownloadResult.Cancelled -> {
                        logcat { "Download $fileName cancelled during HTTP/1.1 retry" }
                        downloadCallback.onCancel(downloadId)
                        directory.getOrCreate(fileName).delete()
                    }
                    else -> {
                        logcat { "Download $fileName failed during HTTP/1.1 retry" }
                        downloadCallback.onError(url = url, downloadId = downloadId, reason = ConnectionRefused)
                        directory.getOrCreate(fileName).delete()
                    }
                }
            }
            is DownloadResult.Error -> {
                logcat { "Download $fileName failed: ${result.reason}" }
                downloadCallback.onError(url = url, downloadId = downloadId, reason = result.reason)
                directory.getOrCreate(fileName).delete()
            }
        }
    }

    private fun executeDownload(
        service: DownloadFileService,
        pendingFileDownload: FileDownloader.PendingFileDownload,
        fileName: String,
        directory: File,
        downloadId: Long,
        downloadCallback: DownloadCallback,
    ): DownloadResult {
        val url = pendingFileDownload.url
        val call = service.downloadFile(
            urlString = url,
            cookie = cookieManagerWrapper.getCookie(url).handleNull(),
            userAgent = pendingFileDownload.userAgent,
        )
        urlFileDownloadCallManager.add(downloadId, call)

        return try {
            val response = call.execute()

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    if (writeStreamingResponseBodyToDisk(downloadId, fileName, directory, body, downloadCallback)) {
                        DownloadResult.Success
                    } else {
                        if (call.isCanceled) {
                            DownloadResult.Cancelled
                        } else {
                            DownloadResult.Error(Other)
                        }
                    }
                } ?: DownloadResult.Error(Other)
            } else {
                logcat { "Failed to download $fileName / ${response.errorBody()?.string()}" }
                DownloadResult.Error(ConnectionRefused)
            }
        } catch (e: StreamResetException) {
            logcat { "StreamResetException during download $fileName: ${e.asLog()}" }
            DownloadResult.StreamResetError(e)
        } catch (t: Throwable) {
            logcat { "Failed to download $fileName: ${t.asLog()}" }
            if (call.isCanceled) {
                DownloadResult.Cancelled
            } else {
                DownloadResult.Error(ConnectionRefused)
            }
        }
    }

    private sealed class DownloadResult {
        data object Success : DownloadResult()
        data object Cancelled : DownloadResult()
        data class StreamResetError(val exception: StreamResetException) : DownloadResult()
        data class Error(val reason: com.duckduckgo.downloads.api.DownloadFailReason) : DownloadResult()
    }

    private fun writeStreamingResponseBodyToDisk(
        downloadId: Long,
        fileName: String,
        directory: File,
        body: ResponseBody,
        downloadCallback: DownloadCallback,
    ): Boolean {
        logcat { "Writing streaming response body to disk $fileName" }

        val contentLength = body.contentLength().takeIf { it > 0 }
        val calculateProgress: (Long) -> Int = if (contentLength != null) {
            // Calculate real progress when content length is known
            { bytesWritten -> (bytesWritten * 100 / contentLength).toInt() }
        } else {
            // Calculate fake progress when content length is not known
            var progressSteps = 0.0
            { floor(calculateFakeProgress(progressSteps) * 100.0).toInt().also { progressSteps += 0.0001 } }
        }

        val file = directory.getOrCreate(fileName)
        val sink = file.sink()
        val source = body.source()

        var totalRead = 0L
        val buffer = Buffer()
        val success = try {
            var progress = 0
            while (!source.exhausted()) {
                val didRead = source.read(buffer, READ_SIZE_BYTES)
                totalRead += didRead
                sink.write(buffer, didRead)
                val newProgress = calculateProgress(totalRead)
                if (newProgress != progress) {
                    progress = newProgress
                    downloadCallback.onProgress(downloadId, fileName, progress)
                }
            }
            true
        } catch (t: Throwable) {
            logcat { "Failed to write to disk $fileName: ${t.asLog()}" }
            false
        } finally {
            source.close()
            sink.close()
        }

        return success
    }

    /**
     * Returns a file in the given directory, creating it if it doesn't exist.
     */
    private fun File.getOrCreate(filename: String): File {
        val file = File(this, filename)

        if (!this.exists()) this.mkdirs()
        if (!file.exists()) file.createNewFile()
        return file
    }

    private fun String?.handleNull(): String? {
        if (this != null) return this

        // if there are no cookies, we omit sending the cookie header when ff is enabled
        return if (fileDownloadFeature.omitEmptyCookieHeader().isEnabled()) {
            null
        } else {
            "" // legacy behavior, we send an empty cookie header
        }
    }

    /**
     * This method calculates fake progress that will be used in cases where the file content length is not known.
     * The fake progress curve follows 1-Math.exp(-step)
     */
    private fun calculateFakeProgress(step: Double): Double {
        return(1 - exp(-step))
    }

    companion object {
        const val READ_SIZE_BYTES = 1024L * 100
    }
}
