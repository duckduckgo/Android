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

package com.duckduckgo.downloads.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.downloads.api.DownloadCallback
import com.duckduckgo.downloads.api.DownloadFailReason
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

class NetworkFileDownloader @Inject constructor(
    private val filenameExtractor: FilenameExtractor,
    private val fileService: DownloadFileService,
    private val urlFileDownloader: UrlFileDownloader,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) {

    fun download(pendingDownload: PendingFileDownload, callback: DownloadCallback) {
        Timber.d("Start download for ${pendingDownload.url}.")

        Timber.d(
            "Content-Disposition is ${pendingDownload.contentDisposition} and " +
                "Content-Type is ${pendingDownload.mimeType} for ${pendingDownload.url}."
        )

        if (pendingDownload.contentDisposition != null && pendingDownload.mimeType != null) {
            downloadFile(pendingDownload, callback)
        } else {
            requestHeaders(pendingDownload, callback)
        }
    }

    private fun requestHeaders(pendingDownload: PendingFileDownload, callback: DownloadCallback) {
        Timber.d("Make a HEAD request for ${pendingDownload.url} as there are no values for Content-Disposition or Content-Type.")

        fileService.getFileDetails(pendingDownload.url)?.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
                var updatedPendingDownload = pendingDownload.copy()

                if (response.isSuccessful) {
                    Timber.d("HEAD request successful for ${pendingDownload.url}")
                    val contentType = response.headers().get("content-type")
                    val contentDisposition = response.headers().get("content-disposition")

                    Timber.d(
                        "Retrieved new values from the HEAD request. " +
                            "Content-Disposition is $contentDisposition and Content-Type is $contentType."
                    )

                    if (contentType != null) {
                        updatedPendingDownload = updatedPendingDownload.copy(mimeType = contentType)
                    }

                    if (contentDisposition != null) {
                        updatedPendingDownload = updatedPendingDownload.copy(contentDisposition = contentDisposition)
                    }
                } else {
                    // This is a non-[200..300) response code. Proceed with download using the Download Manager.
                    Timber.d(
                        "HEAD request unsuccessful. " +
                            "Got a non-[200..300) response code for ${pendingDownload.url}. Error body: ${response.errorBody()}"
                    )
                }

                downloadFile(updatedPendingDownload, callback)
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                // Network exception occurred talking to the server or an unexpected exception occurred creating the request/processing the response.
                callback.onError(url = pendingDownload.url, reason = DownloadFailReason.ConnectionRefused)
            }
        })
    }

    private fun downloadFile(pendingDownload: PendingFileDownload, callback: DownloadCallback) {
        when (val extractionResult = filenameExtractor.extract(pendingDownload)) {
            is FilenameExtractor.FilenameExtractionResult.Extracted -> downloadFile(pendingDownload, extractionResult.filename, callback)
            is FilenameExtractor.FilenameExtractionResult.Guess -> downloadFile(pendingDownload, extractionResult.bestGuess, callback)
        }
    }

    private fun downloadFile(
        pendingDownload: PendingFileDownload,
        guessedFileName: String,
        callback: DownloadCallback
    ) {

        coroutineScope.launch {
            urlFileDownloader.downloadFile(pendingDownload, guessedFileName, callback)
        }
    }
}
