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
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.mobile.android.vpn.ui.OpenVpnBreakageCategoryWithBrokenApp
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.alerts.RealNetPAlertNotiticationBuilder.Companion.NETP_ALERTS_CHANNEL_DESCRIPTION
import com.duckduckgo.networkprotection.impl.alerts.RealNetPAlertNotiticationBuilder.Companion.NETP_ALERTS_CHANNEL_ID
import com.duckduckgo.networkprotection.impl.alerts.RealNetPAlertNotiticationBuilder.Companion.NETP_ALERTS_CHANNEL_NAME
import com.duckduckgo.networkprotection.impl.di.NetpBreakageCategories
import com.duckduckgo.networkprotection.impl.notification.NetPEnableReceiver.Companion.ACTION_NETP_DISABLED_RESTART
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface NetPDisabledNotificationBuilder {
    fun buildDisabledNotification(context: Context): Notification
    fun buildDisabledByVpnNotification(context: Context): Notification
}

@ContributesBinding(AppScope::class)
class RealNetPDisabledNotificationBuilder @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    @NetpBreakageCategories private val breakageCategories: List<AppBreakageCategory>,
) : NetPDisabledNotificationBuilder {

    private fun registerChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    override fun buildDisabledNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_netp_notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_netp_disabled))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(enableNetpNotificationAction(context))
            .addAction(reportIssueNotificationAction(context))
            .setAutoCancel(false)
            .build()
    }

    override fun buildDisabledByVpnNotification(context: Context): Notification {
        registerChannel(context)

        return NotificationCompat.Builder(context, NETP_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_netp_notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(getPendingIntent(context))
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_netp_disabled_by_vpn))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(enableNetpNotificationAction(context))
            .addAction(reportIssueNotificationAction(context))
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

    private fun enableNetpNotificationAction(context: Context): NotificationCompat.Action {
        val launchIntent = Intent(context, NetPEnableReceiver::class.java).let { intent ->
            intent.action = ACTION_NETP_DISABLED_RESTART
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Action(
            R.drawable.ic_vpn_notification_24,
            context.getString(R.string.netpNotificationCTAEnableNetp),
            launchIntent,
        )
    }

    private fun reportIssueNotificationAction(context: Context): NotificationCompat.Action {
        val launchIntent = globalActivityStarter.startIntent(
            context,
            OpenVpnBreakageCategoryWithBrokenApp(
                launchFrom = "netp",
                appName = "",
                appPackageId = "",
                breakageCategories = breakageCategories,
            ),
        )
        return NotificationCompat.Action(
            R.drawable.ic_baseline_feedback_24,
            context.getString(R.string.netpNotificationCTAReportIssue),
            PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE),
        )
    }
}
