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

package com.duckduckgo.networkprotection.impl.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

interface NetPDisabledNotificationBuilder {
    fun buildDisabledNotification(context: Context): Notification

    fun buildSnoozeNotification(
        context: Context,
        triggerAtMillis: Long,
    ): Notification

    fun buildDisabledByVpnNotification(context: Context): Notification

    fun buildUnsafeWifiWithoutVpnNotification(context: Context): Notification
}

@ContributesBinding(AppScope::class)
class RealNetPDisabledNotificationBuilder @Inject constructor(
    private val netPNotificationActions: NetPNotificationActions,
) : NetPDisabledNotificationBuilder {
    private val defaultDateTimeFormatter = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

    private fun registerChannel(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getNotificationChannel(NETP_ALERTS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NETP_ALERTS_CHANNEL_ID,
                NETP_ALERTS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.description = NETP_ALERTS_CHANNEL_DESCRIPTION
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun buildDisabledNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.netpNotificationDisabled)))
            .setContentTitle(context.getString(R.string.netp_name))
            .setContentIntent(getPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(netPNotificationActions.getEnableNetpNotificationAction(context))
            .addAction(netPNotificationActions.getReportIssueNotificationAction(context))
            .setAutoCancel(false)
            .build()
    }

    override fun buildSnoozeNotification(
        context: Context,
        triggerAtMillis: Long,
    ): Notification {
        fun getAction(): NotificationCompat.Action {
            return NotificationCompat.Action(
                R.drawable.ic_baseline_feedback_24,
                context.getString(R.string.netpNotificationCTAReconnectNow),
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, NetPEnableReceiver::class.java).apply {
                        action = NetPEnableReceiver.ACTION_VPN_SNOOZE_CANCEL
                    },
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setContentTitle(context.getString(R.string.netpNotificationSnoozeTitle))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    String.format(
                        context.getString(R.string.netpNotificationSnoozeBody),
                        formatTime(triggerAtMillis),
                    ),
                ),
            )
            .setContentIntent(getPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(getAction())
            .setOngoing(true)
            .build()
    }

    private fun formatTime(triggerAtMillis: Long): String {
        return defaultDateTimeFormatter.format(Date(System.currentTimeMillis() + triggerAtMillis))
    }

    override fun buildDisabledByVpnNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.netpNotificationDisabledByVpn)))
            .setContentTitle(context.getString(R.string.netp_name))
            .setContentIntent(getPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(netPNotificationActions.getEnableNetpNotificationAction(context))
            .addAction(netPNotificationActions.getReportIssueNotificationAction(context))
            .setAutoCancel(false)
            .build()
    }

    override fun buildUnsafeWifiWithoutVpnNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.netpUnsafeWifi)))
            .setContentTitle(context.getString(R.string.netp_name))
            .setContentIntent(getPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(netPNotificationActions.getEnableNetpNotificationAction(context))
            .setAutoCancel(false)
            .build()
    }

    private fun getPendingIntent(context: Context): PendingIntent? = TaskStackBuilder.create(context).run {
        addNextIntentWithParentStack(
            Intent(
                context,
                Class.forName("com.duckduckgo.networkprotection.impl.management.NetworkProtectionManagementActivity"),
            ),
        )
        getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    companion object {
        const val NETP_ALERTS_CHANNEL_ID = "com.duckduckgo.networkprotection.impl.alerts"
        const val NETP_ALERTS_CHANNEL_NAME = "Network Protection Alerts"
        const val NETP_ALERTS_CHANNEL_DESCRIPTION = "Alerts from Network Protection"
    }
}
