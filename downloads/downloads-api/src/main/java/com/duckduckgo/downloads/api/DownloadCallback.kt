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

package com.duckduckgo.downloads.api

import com.duckduckgo.downloads.api.model.DownloadItem
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Interface containing download callbacks. */
interface DownloadCallback {
    /**
     * Called when a download started. Takes a [downloadItem] as parameter with all data related to the file that is downloaded.
     */
    fun onStart(downloadItem: DownloadItem)

    /**
     * Called during the download progress. Takes as parameters the [downloadId] and the [progress] of the download.
     */
    fun onProgress(downloadId: Long, filename: String, progress: Int)

    /**
     * Called when a download done using the DownloadManager finishes with success. Takes as parameters the [downloadId] and [contentLength]
     * provided by the DownloadManager.
     */
    fun onSuccess(downloadId: Long, contentLength: Long, file: File, mimeType: String?)

    /**
     * Called when a download done without using the DownloadManager finishes with success. Takes as parameters the [file]
     * downloaded and the [mimeType] associated with the download.
     */
    fun onSuccess(file: File, mimeType: String?)

    /**
     * Called when the download fails. Takes as optional parameter the [url] which started the download. Takes optional parameters download [url] and
     * [downloadId] and a mandatory parameter [reason] describing why the download has failed.
     */
    fun onError(url: String? = null, downloadId: Long? = null, reason: DownloadFailReason)

    /**
     * Called when the download is cancelled from the app or from the notification. Takes as mandatory parameter the [downloadId] provided by
     * the DownloadManager.
     */
    fun onCancel(downloadId: Long)

    /**
     * Data stream that sequentially emits commands of type [DownloadCommand].
     */
    fun commands(): Flow<DownloadCommand>
}
