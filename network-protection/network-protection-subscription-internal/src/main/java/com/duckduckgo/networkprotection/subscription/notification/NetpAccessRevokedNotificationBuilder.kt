/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.subscription.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.browser.api.ui.BrowserScreens.SettingsScreenNoParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.notification.RealNetPDisabledNotificationBuilder
import com.duckduckgo.networkprotection.subscription.R
import javax.inject.Inject

class NetpAccessRevokedNotificationBuilder @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
) {
    private fun registerChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(context)
            if (notificationManager.getNotificationChannel(RealNetPDisabledNotificationBuilder.NETP_ALERTS_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    RealNetPDisabledNotificationBuilder.NETP_ALERTS_CHANNEL_ID,
                    RealNetPDisabledNotificationBuilder.NETP_ALERTS_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                channel.description = RealNetPDisabledNotificationBuilder.NETP_ALERTS_CHANNEL_DESCRIPTION
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    internal fun buildVpnAccessRevokedNotification(context: Context): Notification {
        registerChannel(context)

        val intent = globalActivityStarter.startIntent(context, SettingsScreenNoParams)
        val pendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(context, RealNetPDisabledNotificationBuilder.NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentIntent(pendingIntent)
            .setContentText(context.getString(R.string.netpNotificationVpnAccessRevoked))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.netpNotificationVpnAccessRevoked),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(false)
            .build()
    }
}
