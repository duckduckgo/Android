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

import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.annotation.WorkerThread
import com.duckduckgo.app.browser.downloader.FileDownloader.FileDownloadListener
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import timber.log.Timber
import java.io.File
import java.io.Serializable
import javax.inject.Inject

interface FileDownloader {

    @WorkerThread
    fun download(pending: PendingFileDownload, callback: FileDownloadListener)

    data class PendingFileDownload(
        val url: String,
        val contentDisposition: String? = null,
        val mimeType: String? = null,
        val subfolder: String,
        val userAgent: String,
        val directory: File = Environment.getExternalStoragePublicDirectory(subfolder)
    ) : Serializable

    interface FileDownloadListener {
        fun downloadStartedDataUri()
        fun downloadStartedNetworkFile()
        fun downloadFinishedDataUri(file: File, mimeType: String?)
        fun downloadFinishedNetworkFile(file: File, mimeType: String?)
        fun downloadFailed(message: String, downloadFailReason: DownloadFailReason)
        fun downloadCancelled()
        fun downloadOpened()
    }
}

class AndroidFileDownloader @Inject constructor(
    private val dataUriDownloader: DataUriDownloader,
    private val networkFileDownloader: NetworkFileDownloader
) : FileDownloader {

    @WorkerThread
    override fun download(pending: PendingFileDownload, callback: FileDownloadListener) {
        when {
            pending.isNetworkUrl -> networkFileDownloader.download(pending, callback)
            pending.isDataUrl -> dataUriDownloader.download(pending, callback)
            else -> callback.downloadFailed("Not supported", DownloadFailReason.UnsupportedUrlType)
        }
    }
}

fun PendingFileDownload.guessFileName(): String {
    val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
    Timber.i("Guessed filename of $guessedFileName for url $url")
    return guessedFileName
}

val PendingFileDownload.isDataUrl get() = URLUtil.isDataUrl(url)

val PendingFileDownload.isNetworkUrl get() = URLUtil.isNetworkUrl(url)

sealed class DownloadFailReason {

    object DownloadManagerDisabled : DownloadFailReason()
    object UnsupportedUrlType : DownloadFailReason()
    object Other : DownloadFailReason()
    object DataUriParseException : DownloadFailReason()

    companion object {
        val DOWNLOAD_MANAGER_SETTINGS_URI: Uri = Uri.parse("package:com.android.providers.downloads")
    }
}
