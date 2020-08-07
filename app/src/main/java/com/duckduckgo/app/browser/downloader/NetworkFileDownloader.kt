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
import javax.inject.Inject

class NetworkFileDownloader @Inject constructor(private val context: Context, private val filenameExtractor: FilenameExtractor) {

    fun download(pendingDownload: PendingFileDownload, callback: FileDownloader.FileDownloadListener) {

        if (!downloadManagerAvailable()) {
            callback.downloadFailed(context.getString(R.string.downloadManagerDisabled), DownloadFailReason.DownloadManagerDisabled)
            return
        }

        val guessedFileName = filenameExtractor.extract(pendingDownload)

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
        callback.downloadStarted()
    }

    private fun downloadManagerAvailable(): Boolean {
        return when (context.packageManager.getApplicationEnabledSetting(DOWNLOAD_MANAGER_PACKAGE)) {
            COMPONENT_ENABLED_STATE_DISABLED -> false
            COMPONENT_ENABLED_STATE_DISABLED_USER -> false
            COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
            else -> true
        }
    }

    companion object {
        private const val DOWNLOAD_MANAGER_PACKAGE = "com.android.providers.downloads"
    }
}
