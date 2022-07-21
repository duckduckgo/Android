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

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class FileDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    @Inject lateinit var networkFileDownloader: NetworkFileDownloader
    @Inject lateinit var callback: FileDownloadCallback
    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        val pending = inputData.toPendingFileDownload()

        when {
            // the worker should only handle URL downloads
            pending.isNetworkUrl -> networkFileDownloader.download(pending, callback)
            else -> callback.onError(url = pending.url, reason = DownloadFailReason.UnsupportedUrlType)
        }

        Result.success()
    }
}

private const val URL = "URL"
private const val CONTENT_DISPOSITION = "CONTENT_DISPOSITION"
private const val MIME_TYPE = "MIME_TYPE"
private const val SUBFOLDER = "SUBFOLDER"
private const val UA = "UA"
private const val DIRECTORY = "DIRECTORY"

fun FileDownloader.PendingFileDownload.toInputData(): Data {
    return Data.Builder()
        .putString(URL, url)
        .putString(CONTENT_DISPOSITION, contentDisposition)
        .putString(MIME_TYPE, mimeType)
        .putString(SUBFOLDER, subfolder)
        .putString(UA, userAgent)
        .putString(DIRECTORY, directory.absolutePath)
        .build()
}

fun Data.toPendingFileDownload(): FileDownloader.PendingFileDownload {
    return FileDownloader.PendingFileDownload(
        url = getString(URL)!!,
        contentDisposition = getString(CONTENT_DISPOSITION),
        mimeType = getString(MIME_TYPE),
        subfolder = getString(SUBFOLDER)!!,
        userAgent = getString(UA)!!,
        directory = File(getString(DIRECTORY)!!)
    )
}
