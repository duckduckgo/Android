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
    suspend fun onStart(downloadItem: DownloadItem)

    /**
     * Called when a download done using the DownloadManager finishes with success. Takes as parameters the [downloadId] and [contentLength]
     * provided by the DownloadManager.
     */
    suspend fun onSuccess(downloadId: Long, contentLength: Long)

    /**
     * Called when a download done without using the DownloadManager finishes with success. Takes as parameters the [file]
     * downloaded and the [mimeType] associated with the download.
     */
    suspend fun onSuccess(file: File, mimeType: String?)

    /**
     * Called when a download done using the DownloadManager is cancelled by the user from the notification. The DownloadManager considers
     * this as a successful download. However, the downloadId gets removed from the internal DB maintained by the DownloadManager.
     * Takes as parameter the [downloadId] provided by the DownloadManager when the download is enqueued.
     */
    suspend fun onCancel(downloadId: Long)

    /**
     * Called when the download fails. Takes as optional parameters the [downloadId] provided by the DownloadManager if that was used
     * and the [url] which started the download. Takes as mandatory parameter the [reason] describing why the download has failed.
     */
    suspend fun onFailure(downloadId: Long? = null, url: String? = null, reason: DownloadFailReason)

    /**
     * Data stream that sequentially emits commands of type [DownloadCommand].
     */
    fun commands(): Flow<DownloadCommand>
}
