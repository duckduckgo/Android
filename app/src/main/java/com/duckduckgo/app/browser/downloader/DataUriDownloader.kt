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

package com.duckduckgo.app.browser.downloader

import android.util.Base64
import androidx.annotation.WorkerThread
import com.duckduckgo.app.browser.downloader.DataUriParser.GeneratedFilename
import com.duckduckgo.app.browser.downloader.DataUriParser.ParseResult
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

class DataUriDownloader @Inject constructor(
    private val dataUriParser: DataUriParser
) {

    @WorkerThread
    fun download(
        pending: FileDownloader.PendingFileDownload,
        callback: FileDownloader.FileDownloadListener?
    ) {

        try {
            callback?.downloadStartedDataUri()

            when (val parsedDataUri = dataUriParser.generate(pending.url)) {
                is ParseResult.Invalid -> {
                    Timber.w("Failed to extract data from data URI")
                    callback?.downloadFailed("Failed to extract data from data URI", DownloadFailReason.DataUriParseException)
                    return
                }
                is ParseResult.ParsedDataUri -> {
                    val file = initialiseFilesOnDisk(pending, parsedDataUri.filename)

                    runCatching {
                        writeBytesToFiles(parsedDataUri.data, file)
                    }
                        .onSuccess {
                            Timber.v("Succeeded to decode Base64")
                            callback?.downloadFinishedDataUri(file, parsedDataUri.mimeType)
                        }
                        .onFailure {
                            Timber.e(it, "Failed to decode Base64")
                            callback?.downloadFailed("Failed to download data uri", DownloadFailReason.DataUriParseException)
                        }
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "Failed to save data uri")
            callback?.downloadFailed("Failed to download data uri", DownloadFailReason.DataUriParseException)
        }
    }

    private fun writeBytesToFiles(
        data: String?,
        file: File
    ) {
        val imageByteArray = Base64.decode(data, Base64.DEFAULT)
        file.writeBytes(imageByteArray)
    }

    private fun initialiseFilesOnDisk(
        pending: FileDownloader.PendingFileDownload,
        generatedFilename: GeneratedFilename
    ): File {
        val downloadDirectory = pending.directory
        val file = File(downloadDirectory, generatedFilename.toString())

        if (!downloadDirectory.exists()) downloadDirectory.mkdirs()
        if (!file.exists()) file.createNewFile()
        return file
    }
}
