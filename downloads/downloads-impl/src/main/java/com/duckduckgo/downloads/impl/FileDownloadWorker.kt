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
import android.util.Base64
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.Data.MAX_DATA_BYTES
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadDestination
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class FileDownloadWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @Inject lateinit var networkFileDownloader: NetworkFileDownloader

    @Inject lateinit var callback: FileDownloadCallback

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        val pending = inputData.toPendingFileDownload()

        when {
            pending.isNetworkUrl -> networkFileDownloader.download(pending, callback)
            else -> callback.onError(url = pending.url, reason = DownloadFailReason.UnsupportedUrlType)
        }

        Result.success()
    }
}

@VisibleForTesting
internal const val URL = "URL"
private const val CONTENT_DISPOSITION = "CONTENT_DISPOSITION"
private const val MIME_TYPE = "MIME_TYPE"
private const val SUBFOLDER = "SUBFOLDER"
private const val DIRECTORY = "DIRECTORY"
private const val DESTINATION_TYPE = "DESTINATION_TYPE"
private const val DESTINATION_TREE_URI = "DESTINATION_TREE_URI"
private const val DESTINATION_DISPLAY_LABEL = "DESTINATION_DISPLAY_LABEL"

@VisibleForTesting
internal const val IS_URL_COMPRESSED = "IS_URL_COMPRESSED"
private const val MAX_URL_DATA_BYTES = MAX_DATA_BYTES * 0.9
private const val BYTE_ARRAY_SIZE = 256

private const val DESTINATION_TYPE_DEFAULT = "default"
private const val DESTINATION_TYPE_CUSTOM = "custom"

fun FileDownloader.PendingFileDownload.toInputData(): Data {
    val urlTooLarge = url.toByteArray().size > MAX_URL_DATA_BYTES
    val builder = Data.Builder()
        .putString(URL, if (urlTooLarge) compress(url) else url)
        .putString(CONTENT_DISPOSITION, contentDisposition)
        .putString(MIME_TYPE, mimeType)
        .putString(SUBFOLDER, subfolder)
        .putString(DIRECTORY, directory.absolutePath)
        .putBoolean(IS_URL_COMPRESSED, urlTooLarge)

    when (val resolvedDestination = destination) {
        is DownloadDestination.Default -> builder.putString(DESTINATION_TYPE, DESTINATION_TYPE_DEFAULT)
        is DownloadDestination.CustomTree ->
            builder
                .putString(DESTINATION_TYPE, DESTINATION_TYPE_CUSTOM)
                .putString(DESTINATION_TREE_URI, resolvedDestination.treeUri)
                .putString(DESTINATION_DISPLAY_LABEL, resolvedDestination.displayLabel)
    }

    return builder.build()
}

fun Data.toPendingFileDownload(): FileDownloader.PendingFileDownload {
    val isUrlCompressed = getBoolean(IS_URL_COMPRESSED, false)
    val url = if (isUrlCompressed) decompress(getString(URL)!!) else getString(URL)!!
    val destination = when (getString(DESTINATION_TYPE)) {
        DESTINATION_TYPE_CUSTOM -> DownloadDestination.CustomTree(
            treeUri = getString(DESTINATION_TREE_URI)!!,
            displayLabel = getString(DESTINATION_DISPLAY_LABEL).orEmpty(),
        )
        else -> DownloadDestination.Default
    }
    return FileDownloader.PendingFileDownload(
        url = url,
        contentDisposition = getString(CONTENT_DISPOSITION),
        mimeType = getString(MIME_TYPE),
        subfolder = getString(SUBFOLDER)!!,
        directory = File(getString(DIRECTORY)!!),
        destination = destination,
        isUrlCompressed = false,
    )
}

private fun compress(str: String): String {
    val out = ByteArrayOutputStream()
    val gzip = GZIPOutputStream(out)
    gzip.write(str.toByteArray())
    gzip.close()
    return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}

private fun decompress(str: String): String {
    val gis = GZIPInputStream(ByteArrayInputStream(Base64.decode(str, Base64.DEFAULT)))
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BYTE_ARRAY_SIZE)
    var len: Int
    while (gis.read(buffer).also { len = it } >= 0) {
        out.write(buffer, 0, len)
    }
    return String(out.toByteArray())
}
