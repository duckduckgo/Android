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
import com.duckduckgo.downloads.impl.location.DownloadFileWriter
import com.duckduckgo.downloads.impl.location.writeStreaming
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import logcat.asLog
import logcat.logcat
import okhttp3.ResponseBody
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random

class UrlFileDownloader @Inject constructor(
    private val downloadFileService: DownloadFileService,
    private val urlFileDownloadCallManager: UrlFileDownloadCallManager,
    private val cookieManagerWrapper: CookieManagerWrapper,
    private val fileDownloadFeature: FileDownloadFeature,
    private val downloadFileWriter: DownloadFileWriter,
) {

    @WorkerThread
    fun downloadFile(
        pendingFileDownload: FileDownloader.PendingFileDownload,
        fileName: String,
        downloadCallback: DownloadCallback,
    ) {
        val url = pendingFileDownload.url
        val call = downloadFileService.downloadFile(
            urlString = url,
            cookie = cookieManagerWrapper.getCookie(url).handleNull(),
        )
        val downloadId = Random.nextLong()
        urlFileDownloadCallManager.add(downloadId, call)

        val resolvedFileName = downloadFileWriter.resolveUniqueFileName(pendingFileDownload, fileName)
        val writeTarget = downloadFileWriter.prepareTarget(pendingFileDownload, resolvedFileName)
        if (writeTarget == null) {
            callbackOnWriteTargetError(url, downloadId, downloadCallback)
            return
        }

        logcat { "Starting download $resolvedFileName / $url" }
        downloadCallback.onStart(
            DownloadItem(
                downloadId = downloadId,
                downloadStatus = STARTED,
                fileName = writeTarget.fileName,
                contentLength = 0,
                filePath = writeTarget.storagePath,
                createdAt = DatabaseDateFormatter.timestamp(),
            ),
        )

        runCatching {
            val response = call.execute()

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    if (writeStreamingResponseBodyToDisk(downloadId, writeTarget, body, downloadCallback)) {
                        val contentLength = downloadFileWriter.contentLength(writeTarget.storagePath)
                        downloadCallback.onSuccess(
                            downloadId = downloadId,
                            contentLength = contentLength,
                            storagePath = writeTarget.storagePath,
                            fileName = writeTarget.fileName,
                            mimeType = pendingFileDownload.mimeType,
                        )
                    } else {
                        if (call.isCanceled) {
                            logcat { "Download $resolvedFileName cancelled" }
                            downloadCallback.onCancel(downloadId)
                        } else {
                            logcat { "Download $resolvedFileName failed" }
                            downloadCallback.onError(url = url, downloadId = downloadId, reason = Other)
                        }
                        writeTarget.cleanup()
                    }
                }
            } else {
                logcat { "Failed to download $resolvedFileName / ${response.errorBody()?.string()}" }
                writeTarget.cleanup()
                downloadCallback.onError(url = url, downloadId = downloadId, reason = ConnectionRefused)
            }
        }.onFailure {
            logcat { "Failed to download $resolvedFileName: ${it.asLog()}" }
            writeTarget.cleanup()
            if (call.isCanceled) {
                downloadCallback.onCancel(downloadId)
            } else {
                downloadCallback.onError(url = url, downloadId = downloadId, reason = ConnectionRefused)
            }
        }
    }

    private fun callbackOnWriteTargetError(
        url: String,
        downloadId: Long,
        downloadCallback: DownloadCallback,
    ) {
        downloadCallback.onError(url = url, downloadId = downloadId, reason = Other)
    }

    private fun writeStreamingResponseBodyToDisk(
        downloadId: Long,
        writeTarget: com.duckduckgo.downloads.impl.location.DownloadWriteTarget,
        body: ResponseBody,
        downloadCallback: DownloadCallback,
    ): Boolean {
        logcat { "Writing streaming response body to disk ${writeTarget.fileName}" }

        val contentLength = body.contentLength().takeIf { it > 0 }
        val calculateProgress: (Long) -> Int = if (contentLength != null) {
            { bytesWritten -> (bytesWritten * 100 / contentLength).toInt() }
        } else {
            var progressSteps = 0.0
            { floor(calculateFakeProgress(progressSteps) * 100.0).toInt().also { progressSteps += 0.0001 } }
        }

        val source = body.source()
        var progress = 0
        val success = try {
            writeTarget.writeStreaming(READ_SIZE_BYTES, { buffer, readSize ->
                if (source.exhausted()) 0L else source.read(buffer, readSize)
            }) { totalRead ->
                val newProgress = calculateProgress(totalRead)
                if (newProgress != progress) {
                    progress = newProgress
                    downloadCallback.onProgress(downloadId, writeTarget.fileName, progress)
                }
            }
        } finally {
            source.close()
        }

        return success
    }

    private fun String?.handleNull(): String? {
        if (this != null) return this

        return if (fileDownloadFeature.omitEmptyCookieHeader().isEnabled()) {
            null
        } else {
            ""
        }
    }

    private fun calculateFakeProgress(step: Double): Double {
        return (1 - exp(-step))
    }

    companion object {
        const val READ_SIZE_BYTES = 1024L * 100
    }
}
