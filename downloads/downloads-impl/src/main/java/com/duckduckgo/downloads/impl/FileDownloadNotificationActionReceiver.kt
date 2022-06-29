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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadCallback
import com.duckduckgo.downloads.api.FileDownloadManager
import com.duckduckgo.downloads.api.FileDownloadNotificationManager
import com.duckduckgo.downloads.api.FileDownloader
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FileDownloadNotificationActionReceiver @Inject constructor(
    private val context: Context,
    private val fileDownloadManager: FileDownloadManager,
    private val fileDownloader: FileDownloader,
    private val downloadCallback: DownloadCallback,
    private val fileDownloadNotificationManager: FileDownloadNotificationManager,
) : BroadcastReceiver(), DefaultLifecycleObserver {

    // TODO use the UA provider
    private val defaultUA = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/96.0.4664.104 Mobile Safari/537.36"

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Timber.v("Registering file download notification action receiver")
        context.registerReceiver(this, IntentFilter(INTENT_DOWNLOADS_NOTIFICATION_ACTION))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        context.unregisterReceiver(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != INTENT_DOWNLOADS_NOTIFICATION_ACTION) return

        val downloadId = intent.getLongExtra(DOWNLOAD_ID_EXTRA, -1)
        if (downloadId == -1L) return

        if (shouldDismissNotification(intent)) fileDownloadNotificationManager.cancelDownloadFileNotification(downloadId)

        if (isCancelIntent(intent)) {
            Timber.v("Received cancel download intent for download id $downloadId")
            fileDownloadManager.remove(downloadId)
        } else if (isRetryIntent(intent)) {
            Timber.v("Received retry download intent for download id $downloadId")
            val url = extractUrlFromRetryIntent(intent) ?: return
            FileDownloader.PendingFileDownload(
                url = url,
                userAgent = defaultUA,
                subfolder = Environment.DIRECTORY_DOWNLOADS,
            ).run {
                Timber.v("Retrying download for $url")
                fileDownloadManager.remove(downloadId)
                fileDownloader.download(this, downloadCallback)
            }
        }
    }

    companion object {
        private const val INTENT_DOWNLOADS_NOTIFICATION_ACTION = "com.duckduckgo.downloads.notification.action"
        private const val CTA = "CTA"
        private const val EXTRA_CANCEL = "EXTRA_CANCEL"
        private const val EXTRA_RETRY = "EXTRA_RETRY"
        private const val DISMISS_NOTIFICATION_EXTRA = "DISMISS_NOTIFICATION_EXTRA"
        private const val DOWNLOAD_ID_EXTRA = "downloadId"
        private const val URL_EXTRA = "URL"

        fun cancelDownloadIntent(downloadId: Long): Intent {
            return Intent(INTENT_DOWNLOADS_NOTIFICATION_ACTION).apply {
                putExtra(DOWNLOAD_ID_EXTRA, downloadId)
                putExtra(CTA, EXTRA_CANCEL)
            }
        }

        fun retryDownloadIntent(downloadId: Long, url: String): Intent {
            return Intent(INTENT_DOWNLOADS_NOTIFICATION_ACTION).apply {
                putExtra(DOWNLOAD_ID_EXTRA, downloadId)
                putExtra(CTA, EXTRA_RETRY)
                putExtra(URL_EXTRA, url)
                // we only need to manually dismiss the notification in download retries. This is because
                // the new retry will have a different download id
                putExtra(DISMISS_NOTIFICATION_EXTRA, true)
            }
        }

        private fun isCancelIntent(intent: Intent): Boolean {
            return intent.getStringExtra(CTA) == EXTRA_CANCEL
        }

        private fun isRetryIntent(intent: Intent): Boolean {
            return intent.getStringExtra(CTA) == EXTRA_RETRY
        }

        private fun extractUrlFromRetryIntent(intent: Intent): String? {
            return intent.getStringExtra(URL_EXTRA)
        }

        private fun shouldDismissNotification(intent: Intent): Boolean {
            return intent.getBooleanExtra(DISMISS_NOTIFICATION_EXTRA, false)
        }
    }
}
