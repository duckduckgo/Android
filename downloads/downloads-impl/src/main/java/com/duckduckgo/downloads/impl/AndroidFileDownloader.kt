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

import android.webkit.URLUtil
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.downloads.api.DownloadDestinationResolver
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AndroidFileDownloader constructor(
    private val dataUriDownloader: DataUriDownloader,
    private val callback: FileDownloadCallback,
    private val workManager: WorkManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val downloadDestinationResolver: DownloadDestinationResolver,
) : FileDownloader {

    override fun enqueueDownload(
        pending: PendingFileDownload,
    ) {
        coroutineScope.launch(dispatcherProvider.io()) {
            val resolved = downloadDestinationResolver.resolve()
            val pendingWithDestination = pending.copy(destination = resolved.destination)
            if (resolved.usedFallback) {
                callback.onDownloadLocationFallbackUsed()
            }
            when {
                pendingWithDestination.isNetworkUrl -> enqueueToWorker(pendingWithDestination)
                pendingWithDestination.isDataUrl -> dataUriDownloader.download(pendingWithDestination, callback)
                else -> callback.onError(url = pending.url, reason = DownloadFailReason.UnsupportedUrlType)
            }
        }
    }

    private fun enqueueToWorker(pending: PendingFileDownload) {
        OneTimeWorkRequestBuilder<FileDownloadWorker>()
            .setInputData(pending.toInputData())
            .build()
            .let {
                workManager.enqueue(it)
            }
    }
}

val PendingFileDownload.isDataUrl get() = URLUtil.isDataUrl(url)

val PendingFileDownload.isNetworkUrl get() = URLUtil.isNetworkUrl(url)
