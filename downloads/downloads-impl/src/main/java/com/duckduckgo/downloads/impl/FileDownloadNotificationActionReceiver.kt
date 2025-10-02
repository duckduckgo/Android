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
import android.os.Environment
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.registerNotExportedReceiver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.*
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName.DOWNLOAD_REQUEST_CANCELLED_BY_USER
import com.duckduckgo.downloads.impl.pixels.DownloadsPixelName.DOWNLOAD_REQUEST_RETRIED
import com.duckduckgo.downloads.store.DownloadStatus.STARTED
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FileDownloadNotificationActionReceiver @Inject constructor(
    private val context: Context,
    private val fileDownloader: FileDownloader,
    private val fileDownloadNotificationManager: FileDownloadNotificationManager,
    private val downloadsRepository: DownloadsRepository,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : BroadcastReceiver(), MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        logcat { "Registering file download notification action receiver" }
        context.registerNotExportedReceiver(this, IntentFilter(INTENT_DOWNLOADS_NOTIFICATION_ACTION))

        // When the app process is killed and restarted, this onCreate method is called and we take the opportunity
        // to clean up the pending downloads that were in progress and will be no longer downloading.
        coroutineScope.launch(dispatcherProvider.io()) {
            purgePendingDownloads()
        }
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
            logcat { "Received cancel download intent for download id $downloadId" }
            pixel.fire(DOWNLOAD_REQUEST_CANCELLED_BY_USER)

            coroutineScope.launch(dispatcherProvider.io()) {
                downloadsRepository.delete(downloadId)
            }
        } else if (isRetryIntent(intent)) {
            logcat { "Received retry download intent for download id $downloadId" }
            pixel.fire(DOWNLOAD_REQUEST_RETRIED)

            val url = extractUrlFromRetryIntent(intent) ?: return
            PendingFileDownload(
                url = url,
                subfolder = Environment.DIRECTORY_DOWNLOADS,
            ).run {
                logcat { "Retrying download for $url" }
                coroutineScope.launch(dispatcherProvider.io()) {
                    downloadsRepository.delete(downloadId)
                    fileDownloader.enqueueDownload(this@run)
                }
            }
        }
    }

    private suspend fun purgePendingDownloads() {
        downloadsRepository.getDownloads().filter { it.downloadStatus == STARTED }.map { it.downloadId }.run {
            downloadsRepository.delete(this)
            forEach { fileDownloadNotificationManager.cancelDownloadFileNotification(it) }
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
