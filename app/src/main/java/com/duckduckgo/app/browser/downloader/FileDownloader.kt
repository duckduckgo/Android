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

import android.os.Environment
import android.webkit.URLUtil
import androidx.annotation.WorkerThread
import java.io.File
import javax.inject.Inject

class FileDownloader @Inject constructor(
    private val dataUriDownloader: DataUriDownloader,
    private val networkDownloader: NetworkFileDownloader
) {

    @WorkerThread
    fun download(pending: PendingFileDownload?, callback: FileDownloadListener?) {

        if (pending == null) {
            return
        }

        when {
            URLUtil.isNetworkUrl(pending.url) -> networkDownloader.download(pending)
            URLUtil.isDataUrl(pending.url) -> dataUriDownloader.download(pending, callback)
            else -> callback?.downloadFailed("Not supported")
        }
    }

    data class PendingFileDownload(
        val url: String,
        val contentDisposition: String? = null,
        val mimeType: String? = null,
        val subfolder: String,
        val directory: File = Environment.getExternalStoragePublicDirectory(subfolder)
    )

    interface FileDownloadListener {
        fun downloadStarted()
        fun downloadFinished(file: File, mimeType: String?)
        fun downloadFailed(message: String)
    }
}