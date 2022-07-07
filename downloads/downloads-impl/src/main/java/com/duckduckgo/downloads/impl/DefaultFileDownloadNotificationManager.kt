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

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.FileDownloadNotificationManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.File
import javax.inject.Inject

@AnyThread
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultFileDownloadNotificationManager @Inject constructor(
    private val notificationManager: NotificationManager,
    private val applicationContext: Context,
    private val appBuildConfig: AppBuildConfig,
) : FileDownloadNotificationManager {

    @AnyThread
    override fun showDownloadInProgressNotification(downloadId: Long, filename: String, progress: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.toInt(),
            FileDownloadNotificationActionReceiver.cancelDownloadIntent(downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADING.id)
            .setContentTitle(applicationContext.getString(R.string.downloadInProgress))
            .setContentText("$filename ($progress%)")
            .setSmallIcon(R.drawable.ic_file_download_white_24dp)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(R.drawable.ic_file_download_white_24dp, applicationContext.getString(R.string.downloadsCancel), pendingIntent)
            .build()

        notificationManager.notify(downloadId.toInt(), notification)
    }

    @AnyThread
    override fun showDownloadFinishedNotification(downloadId: Long, file: File, mimeType: String?) {
        val filename = file.name

        val intent = createIntentToOpenFile(applicationContext, file)

        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADED.id)
            .setContentTitle(filename)
            .setContentText(applicationContext.getString(R.string.downloadComplete))
            .setContentIntent(PendingIntent.getActivity(applicationContext, downloadId.toInt(), intent, pendingIntentFlags))
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_file_download_white_24dp)
            .build()

        notificationManager.cancel(downloadId.toInt())
        // cancelling notifications is a deferred action, and I have seen how in subsequent notify calls end up in the wrong end state
        waitForNotificationToBeCancelled(downloadId)
        notificationManager.notify(downloadId.toInt(), notification)
    }

    @AnyThread
    override fun showDownloadFailedNotification(downloadId: Long, url: String?) {
        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADED.id)
            .setContentTitle(applicationContext.getString(R.string.downloadFailed))
            .setSmallIcon(R.drawable.ic_file_download_white_24dp)
            .apply {
                url?.let { fileUrl ->
                    val pendingIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        downloadId.toInt(),
                        FileDownloadNotificationActionReceiver.retryDownloadIntent(downloadId, fileUrl),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    addAction(R.drawable.ic_file_download_white_24dp, applicationContext.getString(R.string.downloadsRetry), pendingIntent)
                }
            }
            .build()

        // cancelling notifications is a deferred action, and I have seen how in subsequent notify calls end up in the wrong end state
        notificationManager.cancel(downloadId.toInt())
        waitForNotificationToBeCancelled(downloadId)
        notificationManager.notify(downloadId.toInt(), notification)
    }

    @AnyThread
    override fun cancelDownloadFileNotification(downloadId: Long) {
        notificationManager.cancel(downloadId.toInt())
    }

    private fun createIntentToOpenFile(applicationContext: Context, file: File): Intent {
        val fileUri = getFilePathUri(applicationContext, file)
        return Intent().apply {
            setDataAndType(fileUri, applicationContext.contentResolver?.getType(fileUri))
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun getFilePathUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${appBuildConfig.applicationId}.provider", file)
    }

    private fun waitForNotificationToBeCancelled(downloadId: Long) {
        while (notificationManager.activeNotifications.any { it.id == downloadId.toInt() }) {
            Thread.sleep(10)
        }
    }
}
