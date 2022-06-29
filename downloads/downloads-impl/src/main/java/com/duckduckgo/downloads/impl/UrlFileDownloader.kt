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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.downloads.api.DownloadCallback
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.sink
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.random.Random

class UrlFileDownloader @Inject constructor(
    private val downloadFileService: DownloadFileService,
    private val realFileDownloadManager: RealFileDownloadManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun downloadFile(
        pendingFileDownload: FileDownloader.PendingFileDownload,
        fileName: String,
        downloadCallback: DownloadCallback,
    ) = withContext(dispatcherProvider.io()) {
        val url = pendingFileDownload.url
        val directory = pendingFileDownload.directory
        val call = downloadFileService.downloadFile(url)
        val downloadId = Random.nextLong()
        realFileDownloadManager.add(downloadId, call)

        Timber.d("Starting download $fileName")
        downloadCallback.onStart(
            DownloadItem(
                id = 0,
                downloadId = downloadId,
                downloadStatus = STARTED,
                fileName = fileName,
                contentLength = 0,
                filePath = directory.path + File.separatorChar + fileName,
                createdAt = DatabaseDateFormatter.timestamp()
            )

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
                        realFileDownloadManager.remove(downloadId)
                    } else {
                        if (call.isCanceled) {
                            Timber.v("Download $fileName cancelled")
                            downloadCallback.onCancel(downloadId)
                        } else {
                            Timber.v("Download $fileName failed")
                            downloadCallback.onError(url = url, downloadId = downloadId, reason = DownloadFailReason.Other)
                        }
                        // clean up
                        realFileDownloadManager.remove(downloadId)
                        directory.getOrCreate(fileName).delete()
                    }
                }
            } else {
                Timber.w("Failed to download $fileName / ${response.errorBody()?.string()}")
                downloadCallback.onError(url = url, downloadId = downloadId, reason = DownloadFailReason.ConnectionRefused)
                realFileDownloadManager.remove(downloadId)
            }
        }.onFailure {
            Timber.w(it, "Failed to download $fileName")
            if (call.isCanceled) {
                downloadCallback.onCancel(downloadId)
            } else {
                downloadCallback.onError(url = url, downloadId = downloadId, reason = DownloadFailReason.ConnectionRefused)
            }
            // clean up
            realFileDownloadManager.remove(downloadId)
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
        Timber.d("Writing streaming response body to disk $fileName")

        val contentLength = body.contentLength()
        val file = directory.getOrCreate(fileName)
        val sink = file.sink()
        val source = body.source()

        var totalRead = 0L
        val buffer = Buffer()
        val success = try {
            while (!source.exhausted()) {
                val didRead = source.read(buffer, READ_SIZE_BYTES)
                totalRead += didRead
                sink.write(buffer, didRead)
                val progress = totalRead * 100 / contentLength
                downloadCallback.onProgress(downloadId, fileName, progress.toInt())
            }
            true
        } catch (t: Throwable) {
            Timber.e(t, "Failed to write to disk $fileName")
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

    companion object {
        const val READ_SIZE_BYTES = 1024L * 100
    }
}
