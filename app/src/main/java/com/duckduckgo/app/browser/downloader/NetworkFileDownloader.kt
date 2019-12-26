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

import android.app.DownloadManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.net.toUri
import com.duckduckgo.app.browser.downloader.FileDownloader.FileDownloadListener
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class NetworkFileDownloader @Inject constructor(private val context: Context) {

    fun download(
        pendingDownload: FileDownloader.PendingFileDownload,
        callback: FileDownloadListener
    ) {
        val guessedFileName = guessFileName(pendingDownload)
        val fileToDownload = File(pendingDownload.directory, guessedFileName)
        val alreadyDownloaded = fileToDownload.exists()
        callback.confirmDownload(
            DownloadFileData(fileToDownload, alreadyDownloaded),
            object : UserDownloadAction {
                override fun acceptAndReplace() {
                    File(pendingDownload.directory, guessedFileName).delete()
                    initiateDownload(pendingDownload, guessedFileName)
                }

                override fun accept() {
                    initiateDownload(pendingDownload, guessedFileName)
                }

                override fun cancel() {
                    Timber.i("Cancelled download for url ${pendingDownload.url}")
                }
            })
    }

    private fun initiateDownload(
        pendingDownload: FileDownloader.PendingFileDownload,
        guessedFileName: String?
    ) {
        val request = DownloadManager.Request(pendingDownload.url.toUri()).apply {
            allowScanningByMediaScanner()
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(pendingDownload.url))
            setDestinationInExternalPublicDir(pendingDownload.subfolder, guessedFileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
        manager?.enqueue(request)
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