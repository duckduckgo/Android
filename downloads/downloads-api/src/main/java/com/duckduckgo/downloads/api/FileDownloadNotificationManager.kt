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

import androidx.annotation.AnyThread
import java.io.File

interface FileDownloadNotificationManager {
    /**
     * Show an "in progress" notification for the file being downloaded.
     * It can be called multiple times with the [progress] of the download, and receives the [downloadId] and the [filename].
     * Although it is safe to call this method from any thread, if called too frequently, it is recommended to call it on a background thread.
     */
    @AnyThread
    fun showDownloadInProgressNotification(downloadId: Long, filename: String, progress: Int = 0)

    /**
     * Call this method to show the "download complete" notification.
     * Takes as parameters the [downloadId] and the [filename].
     * Safe to call from any thread.
     */
    @AnyThread
    fun showDownloadFinishedNotification(downloadId: Long, file: File, mimeType: String?)

    /**
     * Call this method to show the "download failed" notification.
     * Takes as parameters the [downloadId] and the download [url].
     * Safe to call from any thread.
     */
    @AnyThread
    fun showDownloadFailedNotification(downloadId: Long, url: String?)

    /**
     * Call this method to show the "download cancelled" notification.
     * Takes as parameter the [downloadId].
     * Safe to call from any thread.
     */
    @AnyThread
    fun cancelDownloadFileNotification(downloadId: Long)
}
