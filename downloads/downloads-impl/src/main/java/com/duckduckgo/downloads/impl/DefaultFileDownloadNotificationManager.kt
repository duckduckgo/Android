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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.FileDownloadNotificationManager
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val DOWNLOAD_IN_PROGRESS_GROUP = "com.duckduckgo.downloads.IN_PROGRESS"
private const val SUMMARY_ID = 0

@AnyThread
@ContributesBinding(
    scope = AppScope::class,
    boundType = FileDownloadNotificationManager::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class DefaultFileDownloadNotificationManager @Inject constructor(
    private val notificationManager: NotificationManagerCompat,
    private val applicationContext: Context,
    private val appBuildConfig: AppBuildConfig,
) : FileDownloadNotificationManager, BrowserLifecycleObserver {

    // Group notifications are not automatically cleared when the last notification in the group is removed. So we need to do this manually.
    private val groupNotificationsCounter = AtomicReference<Map<Long, String>>(mapOf())

    // This is not great but didn't find any other way to do it. When the user closes the app all the downloads are cancelled
    // but the in progress notifications are not dismissed however they should.
    // This will flag when the application is closing so that we don't post any more notifications.
    private val applicationClosing = AtomicBoolean(false)

    @AnyThread
    override fun showDownloadInProgressNotification(downloadId: Long, filename: String, progress: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.toInt(),
            FileDownloadNotificationActionReceiver.cancelDownloadIntent(downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADING.id)
            .setPriority(FileDownloadNotificationChannelType.FILE_DOWNLOADING.priority)
            .setContentTitle(applicationContext.getString(R.string.downloadInProgress))
            .setContentText("$filename ($progress%).")
            .setShowWhen(false)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setGroup(DOWNLOAD_IN_PROGRESS_GROUP)
            .addAction(
                com.duckduckgo.mobile.android.R.drawable.ic_downloads_white_16,
                applicationContext.getString(R.string.downloadsCancel),
                pendingIntent,
            )
            .build()

        val summary = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADING.id)
            .setPriority(FileDownloadNotificationChannelType.FILE_DOWNLOADING.priority)
            .setShowWhen(false)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setGroup(DOWNLOAD_IN_PROGRESS_GROUP)
            .setGroupSummary(true)
            .build()

        // we don't want to post any notification while the DDG application is closing
        if (applicationClosing.get()) {
            cancelDownloadFileNotification(downloadId)
            return
        }

        notificationManager.apply {
            checkPermissionAndNotify(applicationContext, downloadId.toInt(), notification)
            checkPermissionAndNotify(applicationContext, SUMMARY_ID, summary)
            groupNotificationsCounter.atomicUpdateAndGet { it.plus(downloadId to filename) }
        }
    }

    @AnyThread
    override fun showDownloadFinishedNotification(downloadId: Long, file: File, mimeType: String?) {
        val filename = file.name

        val intent = createIntentToOpenFile(applicationContext, file)

        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADED.id)
            .setPriority(FileDownloadNotificationChannelType.FILE_DOWNLOADING.priority)
            .setShowWhen(false)
            .setContentTitle(filename)
            .setContentText(applicationContext.getString(R.string.notificationDownloadComplete))
            .setContentIntent(PendingIntent.getActivity(applicationContext, downloadId.toInt(), intent, pendingIntentFlags))
            .setAutoCancel(true)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .build()

        cancelDownloadFileNotification(downloadId)

        // we don't want to post any notification while the DDG application is closing
        if (applicationClosing.get()) return
        notificationManager.checkPermissionAndNotify(applicationContext, downloadId.toInt(), notification)
    }

    @AnyThread
    override fun showDownloadFailedNotification(downloadId: Long, url: String?) {
        val notification = NotificationCompat.Builder(applicationContext, FileDownloadNotificationChannelType.FILE_DOWNLOADED.id)
            .setPriority(FileDownloadNotificationChannelType.FILE_DOWNLOADING.priority)
            .setShowWhen(false)
            .setContentTitle(applicationContext.getString(R.string.notificationDownloadFailed))
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .apply {
                url?.let { fileUrl ->
                    val pendingIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        downloadId.toInt(),
                        FileDownloadNotificationActionReceiver.retryDownloadIntent(downloadId, fileUrl),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    addAction(
                        com.duckduckgo.mobile.android.R.drawable.ic_downloads_white_16,
                        applicationContext.getString(R.string.downloadsRetry),
                        pendingIntent,
                    )
                }
                groupNotificationsCounter.get()[downloadId]?.let { fileName ->
                    setContentText(fileName)
                }
            }
            .build()

        cancelDownloadFileNotification(downloadId)

        // we don't want to post any notification while the DDG application is closing
        if (applicationClosing.get()) return
        notificationManager.checkPermissionAndNotify(applicationContext, downloadId.toInt(), notification)
    }

    @AnyThread
    override fun cancelDownloadFileNotification(downloadId: Long) {
        groupNotificationsCounter.atomicUpdateAndGet { it - downloadId }.run {
            if (isEmpty()) {
                notificationManager.cancel(SUMMARY_ID)
            }
        }
        notificationManager.cancel(downloadId.toInt())
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        // because this is a singleton in AppScope, we want to make sure this state doesn't persist across app launches.
        applicationClosing.set(false)
    }

    override fun onClose() {
        synchronized(groupNotificationsCounter) {
            applicationClosing.set(true)
            val downloadIds = groupNotificationsCounter.get().keys
            downloadIds.forEach { cancelDownloadFileNotification(it) }
            groupNotificationsCounter.set(mapOf())
        }
        // we can't detect the app swipe closed event reliably because the app lifecycle behaves differently when the AppTP (process) is enabled
        // or disabled. Eg. Swipe closing the app when AppTP (process) is enabled doens't really kill the app as it happens when AppTP is disabled
        // The only way I found is the postDelayed below. If the Runnable is executed, it means the app is still alive, so we want in progress
        // notifications to continue appearing
        Handler(Looper.getMainLooper()).postDelayed({ applicationClosing.set(false) }, 250)
    }

    // We could have used [AtomicReference#getAndUpdate] but it's not available in Android API level 24.
    private fun AtomicReference<Map<Long, String>>.atomicUpdateAndGet(updateFunction: UpdateInProgress): Map<Long, String> {
        var prev: Map<Long, String>
        var next: Map<Long, String>
        do {
            prev = get()
            next = updateFunction.update(prev)
        } while (!compareAndSet(prev, next))
        return next
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

    private fun interface UpdateInProgress {
        fun update(current: Map<Long, String>): Map<Long, String>
    }
}
