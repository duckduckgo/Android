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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

class NetworkFileDownloader @Inject constructor(
    private val context: Context,
    private val filenameExtractor: FilenameExtractor,
    private val fileService: DownloadFileService
) {

    fun download(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener) {

        if (!downloadManagerAvailable()) {
            callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
            return
        }

        fileService.getFileDetails(pendingDownload.url)?.enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: Response<Void>) {
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
                        is FilenameExtractor.FilenameExtractionResult.Extracted -> downloadFile(updatedPendingDownload, extractionResult.filename, callback)
                        is FilenameExtractor.FilenameExtractionResult.Guess -> {
                            downloadFile(updatedPendingDownload, extractionResult.bestGuess, callback)
                        }
                    }
                } else {
                    Timber.d("Connection failed ${response.errorBody()}")
                    callback.downloadFailed("Connection failed", DownloadFailReason.ConnectionRefused)
                }
            }

            override fun onFailure(call: Call<Void>, t: Throwable) {
                callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
                return
            }
        })

    }

    private fun downloadManagerAvailable(): Boolean {
        return when (context.packageManager.getApplicationEnabledSetting(DOWNLOAD_MANAGER_PACKAGE)) {
            COMPONENT_ENABLED_STATE_DISABLED -> false
            COMPONENT_ENABLED_STATE_DISABLED_USER -> false
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> true
        }
    }

    private fun downloadFile(pendingDownload: PendingFileDownload, guessedFileName: String, callback: FileDownloader.FileDownloadListener) {
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
