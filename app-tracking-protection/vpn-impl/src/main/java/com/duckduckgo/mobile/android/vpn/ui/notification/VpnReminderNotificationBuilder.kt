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

package com.duckduckgo.mobile.android.vpn.ui.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationContentPlugin
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface VpnReminderNotificationBuilder {
    fun buildReminderNotification(
        vpnNotification: VpnReminderNotificationContentPlugin.NotificationContent,
    ): Notification
}

@ContributesBinding(AppScope::class)
class RealVpnReminderNotificationBuilder @Inject constructor(
    private val context: Context,
) : VpnReminderNotificationBuilder {

    private fun registerAlertChannel(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getNotificationChannel(AndroidDeviceShieldAlertNotificationBuilder.VPN_ALERTS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                VPN_ALERTS_CHANNEL_ID,
                VPN_ALERTS_CHANNEL_NAME,
                IMPORTANCE_DEFAULT,
            )
            channel.description = VPN_ALERTS_CHANNEL_DESCRIPTION
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun buildReminderNotification(
        vpnNotification: VpnReminderNotificationContentPlugin.NotificationContent,
    ): Notification {
        registerAlertChannel(context)

        val builder = NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(vpnNotification.title))
            .setContentTitle(context.getString(R.string.atp_name))
            .setContentIntent(vpnNotification.onNotificationPressIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setSilent(vpnNotification.isSilent)

        vpnNotification.notificationAction.forEach {
            builder.addAction(it)
        }

        vpnNotification.shouldAutoCancel?.let {
            builder.setAutoCancel(it)
        }

        return builder.build()
    }

    companion object {
        private const val VPN_ALERTS_CHANNEL_DESCRIPTION = "Alerts from App Tracking Protection"
        private const val VPN_ALERTS_CHANNEL_ID = "com.duckduckgo.mobile.android.vpn.notification.alerts"
        private const val VPN_ALERTS_CHANNEL_NAME = "App Tracking Protection Alerts"
    }
}
