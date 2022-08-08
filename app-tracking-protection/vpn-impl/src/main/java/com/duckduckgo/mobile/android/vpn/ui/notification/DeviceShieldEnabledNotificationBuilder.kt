/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity

class DeviceShieldEnabledNotificationBuilder {

    companion object {

        private const val VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = "com.duckduckgo.mobile.android.vpn.notification.ongoing"
        private const val VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME = "App Tracking Protection Status"
        private const val VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION = "Ongoing state of App Tracking Protection"

        private fun registerOngoingNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID,
                        VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    )
                channel.description = VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_DESCRIPTION
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun buildDeviceShieldEnabledNotification(
            context: Context,
            deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
            notificationPressHandler: OngoingNotificationPressedHandler
        ): Notification {

            registerOngoingNotificationChannel(context)

            val privacyReportIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = notificationPressHandler)
            val vpnShowDashboardPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(privacyReportIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_enabled)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationHeader, deviceShieldNotification.title)

            return NotificationCompat.Builder(context, VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnShowDashboardPendingIntent)
                .setCustomContentView(notificationLayout)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setChannelId(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }

        fun buildTrackersBlockedNotification(
            context: Context,
            deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
            notificationPressHandler: OngoingNotificationPressedHandler
        ): Notification {

            registerOngoingNotificationChannel(context)

            val privacyReportIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = notificationPressHandler)
            val vpnShowDashboardPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(privacyReportIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_trackers)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationHeader, deviceShieldNotification.title)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationMessage, deviceShieldNotification.message)

            return NotificationCompat.Builder(context, VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnShowDashboardPendingIntent)
                .setCustomContentView(notificationLayout)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setOngoing(true)
                .addAction(NotificationActionReportIssue.mangeRecentAppsNotificationAction(context))
                .setChannelId(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}
