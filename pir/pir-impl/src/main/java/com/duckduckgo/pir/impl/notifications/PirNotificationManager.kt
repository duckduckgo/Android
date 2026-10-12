/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.notifications

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.R
import com.duckduckgo.pir.impl.dashboard.PirDashboardWebViewActivity
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PirNotificationManager {
    fun createNotificationChannel()
    fun showScanStatusNotification(
        title: String,
        message: String,
    )

    fun createScanStatusNotification(
        title: String,
        message: String,
    ): Notification

    fun showFreemiumScanCompleteNotification(): Boolean

    fun cancelNotifications()
}

@ContributesBinding(AppScope::class)
class RealPirNotificationManager @Inject constructor(
    private val context: Context,
    private val notificationManagerCompat: NotificationManagerCompat,
) : PirNotificationManager {

    override fun createNotificationChannel() {
        // Define the importance level of the notification channel
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        // Create the NotificationChannel with a unique ID, name, and importance level
        val channel =
            NotificationChannel(PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID, PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME, importance)
        channel.description = PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION

        // Register the channel with the system
        val notificationManager = context.getSystemService(
            NotificationManager::class.java,
        )
        notificationManager?.createNotificationChannel(channel)
    }

    override fun showScanStatusNotification(
        title: String,
        message: String,
    ) {
        notificationManagerCompat.checkPermissionAndNotify(
            context,
            PIR_FOREGROUND_SERVICE_NOTIFICATION_ID_STATUS_COMPLETE,
            createScanStatusNotification(
                title = title,
                message = message,
            ),
        )
    }

    override fun createScanStatusNotification(
        title: String,
        message: String,
    ): Notification {
        val notificationIntent = Intent(
            context,
            PirDashboardWebViewActivity::class.java,
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return buildNotification(title = title, message = message, contentIntent = pendingIntent)
    }

    override fun showFreemiumScanCompleteNotification(): Boolean = runCatching {
        val hasPermission = VERSION.SDK_INT < 33 || ActivityCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
        if (hasPermission) {
            // the scan can finish in a :pir process that never ran the dashboard, which is where the channel is otherwise created
            createNotificationChannel()
            notificationManagerCompat.checkPermissionAndNotify(
                context,
                PIR_FOREGROUND_SERVICE_NOTIFICATION_ID_STATUS_COMPLETE,
                createFreemiumScanCompleteNotification(),
            )
        }
        hasPermission
    }.getOrDefault(false)

    override fun cancelNotifications() {
        notificationManagerCompat.cancel(PIR_FOREGROUND_SERVICE_NOTIFICATION_ID_STATUS_COMPLETE)
    }

    private fun createFreemiumScanCompleteNotification(): Notification {
        val notificationIntent = Intent(context, PirDashboardWebViewActivity::class.java)
            .putExtra(PirDashboardWebViewActivity.EXTRA_FROM_FREEMIUM_SCAN_COMPLETE_NOTIFICATION, true)
        // PendingIntent matching ignores extras, so sharing the scan status request code would hand back that intent without the extra
        val pendingIntent = PendingIntent.getActivity(
            context,
            FREEMIUM_SCAN_COMPLETE_REQUEST_CODE,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return buildNotification(
            title = context.getString(R.string.pirFreemiumNotificationTitleComplete),
            message = context.getString(R.string.pirFreemiumNotificationMessageComplete),
            contentIntent = pendingIntent,
            autoCancel = true,
        )
    }

    private fun buildNotification(
        title: String,
        message: String,
        contentIntent: PendingIntent,
        autoCancel: Boolean = false,
    ): Notification {
        return NotificationCompat.Builder(context, PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentIntent(contentIntent)
            .setAutoCancel(autoCancel)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
    }

    companion object {
        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME = "Personal Information Removal Status"
        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION = "Status updates for Personal Information Removal"

        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = "com.duckduckgo.pir.PirNotificationChannel"
        private const val PIR_FOREGROUND_SERVICE_NOTIFICATION_ID_STATUS_COMPLETE = 987
        private const val FREEMIUM_SCAN_COMPLETE_REQUEST_CODE = 1
    }
}
