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
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.mobile.android.vpn.R
import dummy.VpnControllerActivity

class VpnNotificationBuilder {

    companion object {

        fun buildPersistentNotification(context: Context): Notification {
            registerChannel(context)

            val vpnControllerIntent = Intent(context, VpnControllerActivity::class.java)
            val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            return NotificationCompat.Builder(context,
                VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setContentTitle(context.getString(R.string.vpnNotificationTitle))
                .setSmallIcon(R.drawable.ic_vpn_notification_24)
                .setContentIntent(vpnControllerPendingIntent)
                .setOngoing(true)
                .setChannelId(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .build()
        }

        private fun registerChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID, "Tracker Protection Running", NotificationManager.IMPORTANCE_LOW)
                //NotificationManagerCompat.from(context).createNotificationChannel(channel)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun buildReminderNotification(context: Context): Notification{
            registerChannel(context)

            val vpnControllerIntent = Intent(context, VpnControllerActivity::class.java)
            val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            return NotificationCompat.Builder(context,
                VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID
            )
                .setContentTitle(context.getString(R.string.vpnReminderNotificationTitle))
                .setContentText(context.getString(R.string.vpnReminderNotificationText))
                .setSmallIcon(R.drawable.ic_vpn_notification_24)
                .setContentIntent(vpnControllerPendingIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setChannelId(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .build()
        }

        private const val VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = "TrackerProtectionVPN"
    }

}