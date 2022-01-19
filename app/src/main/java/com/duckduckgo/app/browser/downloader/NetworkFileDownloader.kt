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
import android.content.pm.PackageManager.*
import android.webkit.CookieManager
import androidx.core.net.toUri
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

class NetworkFileDownloader @Inject constructor(
    private val context: Context,
    private val filenameExtractor: FilenameExtractor,
    private val fileService: DownloadFileService,
    private val appBuildConfig: AppBuildConfig
) {

    fun download(
        pendingDownload: PendingFileDownload,
        callback: FileDownloader.FileDownloadListener
    ) {
        if (appBuildConfig.flavor == INTERNAL) {
            downloadV2(pendingDownload, callback)
        } else {
            downloadV1(pendingDownload, callback)
        }
    }

    private fun downloadV1(
        pendingDownload: PendingFileDownload,
        callback: FileDownloader.FileDownloadListener
    ) {

        if (!downloadManagerAvailable()) {
            callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
            return
        }

        fileService.getFileDetails(pendingDownload.url)?.enqueue(object : Callback<Void> {
            override fun onResponse(
                call: Call<Void>,
                response: Response<Void>
            ) {
                if (response.isSuccessful) {
                    var updatedPendingDownload = pendingDownload.copy()

                    val contentType = response.headers().get("content-type")
                    val contentDisposition = response.headers().get("content-disposition")

                    if (contentType != null) {
                        updatedPendingDownload = updatedPendingDownload.copy(mimeType = contentType)
                    }

                    if (contentDisposition != null) {
                        updatedPendingDownload = updatedPendingDownload.copy(contentDisposition = contentDisposition)
                    }

                    when (val extractionResult = filenameExtractor.extract(updatedPendingDownload)) {
                        is FilenameExtractor.FilenameExtractionResult.Extracted -> downloadFile(
                            updatedPendingDownload,
                            extractionResult.filename,
                            callback
                        )
                        is FilenameExtractor.FilenameExtractionResult.Guess -> {
                            downloadFile(updatedPendingDownload, extractionResult.bestGuess, callback)
                        }
                    }
                } else {
                    // TODO [Improve downloads] This is not a connection failed error, but a non-[200..300) response code.
                    Timber.d("Connection failed ${response.errorBody()}")
                    callback.downloadFailed("Connection failed", DownloadFailReason.ConnectionRefused)
                }
            }

            override fun onFailure(
                call: Call<Void>,
                t: Throwable
            ) {
                // TODO [Improve downloads] This is a connection failed, the reason provided is misleading.
                callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
                return
            }
        })
    }

    private fun downloadV2(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener) {
        Timber.d("Start download for ${pendingDownload.url}.")

        if (!downloadManagerAvailable()) {
            Timber.d("Download manager not available, end downloading ${pendingDownload.url}.")
            callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
            return
        }

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

    private fun requestHeaders(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener) {
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
                callback.downloadFailed(context.getString(R.string.downloadsErrorMessage), DownloadFailReason.ConnectionRefused)
                return
            }
        })
    }

    private fun downloadFile(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener) {
        when (val extractionResult = filenameExtractor.extract(pendingDownload)) {
            is FilenameExtractor.FilenameExtractionResult.Extracted -> downloadFile(pendingDownload, extractionResult.filename, callback)
            is FilenameExtractor.FilenameExtractionResult.Guess -> downloadFile(pendingDownload, extractionResult.bestGuess, callback)
        }
    }

    private fun downloadManagerAvailable(): Boolean {
        return when (context.packageManager.getApplicationEnabledSetting(DOWNLOAD_MANAGER_PACKAGE)) {
            COMPONENT_ENABLED_STATE_DISABLED -> false
            COMPONENT_ENABLED_STATE_DISABLED_USER -> false
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> true
        }
    }

    private fun downloadFile(
        pendingDownload: PendingFileDownload,
        guessedFileName: String,
        callback: FileDownloader.FileDownloadListener
    ) {
        val request = DownloadManager.Request(pendingDownload.url.toUri()).apply {
            allowScanningByMediaScanner()
            addRequestHeader("User-Agent", pendingDownload.userAgent)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(pendingDownload.url))
            setMimeType(pendingDownload.mimeType)
            setDestinationInExternalPublicDir(pendingDownload.subfolder, guessedFileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
        manager?.enqueue(request)
        callback.downloadStartedNetworkFile()
    }

    companion object {
        private const val DOWNLOAD_MANAGER_PACKAGE = "com.android.providers.downloads"
    }
}
