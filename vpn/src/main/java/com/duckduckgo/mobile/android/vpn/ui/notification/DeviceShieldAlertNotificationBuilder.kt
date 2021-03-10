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
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import com.duckduckgo.mobile.android.vpn.ui.report.PrivacyReportActivity

class DeviceShieldAlertNotificationBuilder {

    companion object {

        private const val VPN_ALERTS_CHANNEL_ID = "DeviceShieldAlertChannel"

        private fun registerAlertChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(VPN_ALERTS_CHANNEL_ID, "Device Shield Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun buildReminderNotification(context: Context, silent: Boolean): Notification {
            registerAlertChannel(context)

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_disabled)

            registerAlertChannel(context)

            val vpnControllerIntent = Intent(context, PrivacyReportActivity::class.java)
            val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val restartVpnIntent = Intent(context, VpnReminderReceiver::class.java).let { intent ->
                intent.action = TrackerBlockingVpnService.ACTION_VPN_REMINDER_RESTART
                PendingIntent.getBroadcast(context, 0, intent, 0)
            }

            return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnControllerPendingIntent)
                .setCustomContentView(notificationLayout)
                .setOngoing(true)
                .setSilent(silent)
                .addAction(
                    NotificationCompat.Action(
                        R.drawable.ic_vpn_notification_24,
                        context.getString(R.string.deviceShieldOnboardingTitle),
                        restartVpnIntent
                    )
                )
                .setChannelId(VPN_ALERTS_CHANNEL_ID)
                .build()
        }

        fun buildDeviceShieldNotification(
            context: Context,
            deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification
        ): Notification {

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_report)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationText, deviceShieldNotification.title)

            return buildNotification(context, notificationLayout, false)
        }

        private fun buildNotification(context: Context, content: RemoteViews, silent: Boolean): Notification {
            registerAlertChannel(context)

            val vpnControllerIntent = Intent(context, PrivacyReportActivity::class.java)
            val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnControllerPendingIntent)
                .setSilent(silent)
                .setCustomContentView(content)
                .setAutoCancel(true)
                .setChannelId(VPN_ALERTS_CHANNEL_ID)
                .build()
        }
    }

}
