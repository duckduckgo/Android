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

package com.duckduckgo.networkprotection.impl.alerts

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
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.R
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetPAlertNotiticationBuilder {
    fun buildReconnectingNotification(context: Context): Notification
    fun buildReconnectedNotification(context: Context): Notification
    fun buildReconnectionFailedNotification(context: Context): Notification
}

@ContributesBinding(AppScope::class)
class RealNetPAlertNotiticationBuilder @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : NetPAlertNotiticationBuilder {

    @Suppress("NewApi") // we use appBuildConfig
    private fun registerChannel(context: Context) {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(NETP_ALERTS_CHANNEL_ID) == null) {
                val channel = NotificationChannel(NETP_ALERTS_CHANNEL_ID, NETP_ALERTS_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                channel.description = NETP_ALERTS_CHANNEL_DESCRIPTION
                notificationManager.createNotificationChannel(channel)
                notificationManager.isNotificationPolicyAccessGranted
            }
        }
    }

    override fun buildReconnectingNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_reconnecting))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    override fun buildReconnectedNotification(context: Context): Notification {
        registerChannel(context)
        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_reconnect_success))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    override fun buildReconnectionFailedNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_reconnect_failed))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
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
