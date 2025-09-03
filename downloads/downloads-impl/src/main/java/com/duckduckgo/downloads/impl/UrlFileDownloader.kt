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
import java.io.File
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.floor
import kotlin.random.Random
import logcat.asLog
import logcat.logcat
import okhttp3.ResponseBody
import okio.Buffer
import okio.sink

class UrlFileDownloader @Inject constructor(
    private val downloadFileService: DownloadFileService,
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
        val call = downloadFileService.downloadFile(
            urlString = url,
            cookie = cookieManagerWrapper.getCookie(url).handleNull(),
        )
        val downloadId = Random.nextLong()
        urlFileDownloadCallManager.add(downloadId, call)

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

        runCatching {
            val response = call.execute()

            if (response.isSuccessful) {
                response.body()?.let {
                    if (writeStreamingResponseBodyToDisk(downloadId, fileName, directory, it, downloadCallback)) {
                        val file = directory.getOrCreate(fileName)
                        // for file length we don't use body.contentLength() as it is not reliable. Eg. when downloading image from DDG search
                        // as the link is a re-direct, contentLength() will be -1
                        downloadCallback.onSuccess(downloadId, file.length(), file, pendingFileDownload.mimeType)
                    } else {
                        if (call.isCanceled) {
                            logcat { "Download $fileName cancelled" }
                            downloadCallback.onCancel(downloadId)
                        } else {
                            logcat { "Download $fileName failed" }
                            downloadCallback.onError(url = url, downloadId = downloadId, reason = Other)
                        }
                        // clean up
                        directory.getOrCreate(fileName).delete()
                    }
                }
            } else {
                logcat { "Failed to download $fileName / ${response.errorBody()?.string()}" }
                downloadCallback.onError(url = url, downloadId = downloadId, reason = ConnectionRefused)
            }
        }.onFailure {
            logcat { "Failed to download $fileName: ${it.asLog()}" }
            if (call.isCanceled) {
                downloadCallback.onCancel(downloadId)
            } else {
                downloadCallback.onError(url = url, downloadId = downloadId, reason = ConnectionRefused)
            }
            // clean up
            directory.getOrCreate(fileName).delete()
        }
    }

    private fun writeStreamingResponseBodyToDisk(
        downloadId: Long,
        fileName: String,
        directory: File,
        body: ResponseBody,
        downloadCallback: DownloadCallback,
    ): Boolean {
        logcat { "Writing streaming response body to disk $fileName" }

        // ensure content length never 0
        val contentLength = if (body.contentLength() > 0) body.contentLength() else -1
        val file = directory.getOrCreate(fileName)
        val sink = file.sink()
        val source = body.source()

        var totalRead = 0L
        val buffer = Buffer()
        val success = try {
            var progressSteps = 0.0
            while (!source.exhausted()) {
                val didRead = source.read(buffer, READ_SIZE_BYTES)
                totalRead += didRead
                sink.write(buffer, didRead)
                val fakeProgress = floor(calculateFakeProgress(progressSteps) * 100.0).toInt().also { progressSteps += 0.0001 }
                val calculatedProgress = (totalRead * 100 / contentLength)
                val progress = if (calculatedProgress < 0L) fakeProgress else calculatedProgress
                downloadCallback.onProgress(downloadId, fileName, progress.toInt())
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
