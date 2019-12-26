/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.webkit.URLUtil
import timber.log.Timber
import java.io.File
import javax.inject.Inject


class NetworkFileDownloadManager @Inject constructor(private val networkDownloader: NetworkFileDownloader) {

    fun download(
        pendingDownload: FileDownloader.PendingFileDownload,
        callback: FileDownloader.FileDownloadListener
    ) {
        val guessedFileName = guessFileName(pendingDownload)
        val fileToDownload = File(pendingDownload.directory, guessedFileName)
        val alreadyDownloaded = fileToDownload.exists()
        callback.confirmDownload(
            DownloadFileData(fileToDownload, alreadyDownloaded),
            object : UserDownloadAction {
                override fun acceptAndReplace() {
                    File(pendingDownload.directory, guessedFileName).delete()
                    networkDownloader.download(pendingDownload)
                }

                override fun accept() {
                    networkDownloader.download(pendingDownload)
                }

                override fun cancel() {
                    Timber.i("Cancelled download for url ${pendingDownload.url}")
                }
            })
    }

    private fun guessFileName(pending: FileDownloader.PendingFileDownload): String {
        val guessedFileName =
            URLUtil.guessFileName(pending.url, pending.contentDisposition, pending.mimeType)
        Timber.i("Guessed filename of $guessedFileName for url ${pending.url}")
        return guessedFileName
    }

    interface UserDownloadAction {
        fun accept()
        fun acceptAndReplace()
        fun cancel()
    }

    class DownloadFileData(val file: File, val alreadyDownloaded: Boolean)
}