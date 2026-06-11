/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.util.Base64
import androidx.annotation.WorkerThread
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.DownloadFailReason.DataUriParseException
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.impl.DataUriParser.ParseResult
import com.duckduckgo.downloads.impl.location.DownloadFileWriter
import com.duckduckgo.downloads.impl.location.writeBytes
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.random.Random

class DataUriDownloader @Inject constructor(
    private val dataUriParser: DataUriParser,
    private val downloadFileWriter: DownloadFileWriter,
) {

    @WorkerThread
    fun download(
        pending: PendingFileDownload,
        callback: DownloadCallback,
    ) {
        try {
            when (val parsedDataUri = dataUriParser.generate(pending.url, pending.fileName)) {
                is ParseResult.Invalid -> {
                    logcat { "Failed to extract data from data URI" }
                    callback.onError(url = pending.url, reason = DataUriParseException)
                }
                is ParseResult.ParsedDataUri -> {
                    val fileName = downloadFileWriter.resolveUniqueFileName(pending, parsedDataUri.filename.toString())
                    val writeTarget = downloadFileWriter.prepareTarget(pending, fileName)
                    if (writeTarget == null) {
                        callback.onError(url = pending.url, reason = DownloadFailReason.DataUriParseException)
                        return
                    }

                    val downloadId = Random.nextLong()
                    callback.onStart(
                        DownloadItem(
                            downloadId = downloadId,
                            downloadStatus = STARTED,
                            fileName = writeTarget.fileName,
                            contentLength = 0L,
                            filePath = writeTarget.storagePath,
                            createdAt = DatabaseDateFormatter.timestamp(),
                        ),
                    )

                    runCatching {
                        val imageByteArray = Base64.decode(parsedDataUri.data, Base64.DEFAULT)
                        writeTarget.writeBytes(imageByteArray)
                    }
                        .onSuccess { success ->
                            if (success) {
                                logcat { "Succeeded to decode Base64" }
                                callback.onSuccess(
                                    downloadId = downloadId,
                                    contentLength = downloadFileWriter.contentLength(writeTarget.storagePath),
                                    storagePath = writeTarget.storagePath,
                                    fileName = writeTarget.fileName,
                                    mimeType = parsedDataUri.mimeType,
                                )
                            } else {
                                writeTarget.cleanup()
                                callback.onError(url = pending.url, downloadId = downloadId, reason = DownloadFailReason.DataUriParseException)
                            }
                        }
                        .onFailure {
                            logcat { "Failed to decode Base64: ${it.asLog()}" }
                            writeTarget.cleanup()
                            callback.onError(url = pending.url, downloadId = downloadId, reason = DownloadFailReason.DataUriParseException)
                        }
                }
            }
        } catch (e: Exception) {
            logcat { "Failed to save data uri: ${e.asLog()}" }
            callback.onError(url = pending.url, reason = DownloadFailReason.DataUriParseException)
        }
    }
}
