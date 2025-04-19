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
import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldNotificationFactory.DeviceShieldNotification
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(AppScope::class)
object DeviceShieldAlertNotificationBuilderModule {

    @Provides
    fun providesDeviceShieldAlertNotificationBuilder(): DeviceShieldAlertNotificationBuilder {
        return AndroidDeviceShieldAlertNotificationBuilder()
    }
}

interface DeviceShieldAlertNotificationBuilder {

    fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification

    fun buildAlwaysOnLockdownNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotification,
        contentNextIntent: Intent,
    ): Notification
}

class AndroidDeviceShieldAlertNotificationBuilder : DeviceShieldAlertNotificationBuilder {

    private fun registerAlertChannel(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (notificationManager.getNotificationChannel(VPN_ALERTS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(VPN_ALERTS_CHANNEL_ID, VPN_ALERTS_CHANNEL_NAME, IMPORTANCE_DEFAULT)
            channel.description = VPN_ALERTS_CHANNEL_DESCRIPTION
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver,
    ): Notification {
        registerAlertChannel(context)

        val vpnControllerIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = onNotificationPressedCallback)

        return buildNotification(context, deviceShieldNotification, addReportIssueAction = true, contentNextIntent = vpnControllerIntent)
    }

    override fun buildAlwaysOnLockdownNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotification,
        contentNextIntent: Intent,
    ): Notification {
        registerAlertChannel(context)

        return buildNotification(context, deviceShieldNotification, addReportIssueAction = false, contentNextIntent = contentNextIntent)
    }

    private fun buildNotification(
        context: Context,
        content: DeviceShieldNotification,
        addReportIssueAction: Boolean,
        contentNextIntent: Intent,
    ): Notification {
        registerAlertChannel(context)

        val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(contentNextIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
            .setSmallIcon(com.duckduckgo.mobile.android.R.drawable.notification_logo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentTitle(context.getString(R.string.atp_name))
            .setContentIntent(vpnControllerPendingIntent)
            .setSilent(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply {
                if (addReportIssueAction) {
                    addAction(NotificationActionReportIssue.reportIssueNotificationAction(context))
                }
            }
            .build()
    }

    companion object {
        const val VPN_ALERTS_CHANNEL_ID = "com.duckduckgo.mobile.android.vpn.notification.alerts"
        private const val VPN_ALERTS_CHANNEL_NAME = "App Tracking Protection Alerts"
        private const val VPN_ALERTS_CHANNEL_DESCRIPTION = "Alerts from App Tracking Protection"
    }
}
