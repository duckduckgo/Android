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
import android.os.ResultReceiver
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnReminderReceiver
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(AppScope::class)
class DeviceShieldAlertNotificationBuilderModule {

    @Provides
    fun providesDeviceShieldAlertNotificationBuilder(): DeviceShieldAlertNotificationBuilder {
        return AndroidDeviceShieldAlertNotificationBuilder()
    }
}

interface DeviceShieldAlertNotificationBuilder {

    fun buildReminderNotification(
        context: Context,
        silent: Boolean
    ): Notification

    fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver
    ): Notification
}

class AndroidDeviceShieldAlertNotificationBuilder : DeviceShieldAlertNotificationBuilder {

    private fun registerAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(VPN_ALERTS_CHANNEL_ID, "App Tracking Protection Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun registerStatusChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(VPN_STATUS_CHANNEL_ID, "App Tracking Status Channel", NotificationManager.IMPORTANCE_MIN)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun buildReminderNotification(
        context: Context,
        silent: Boolean
    ): Notification {
        registerAlertChannel(context)

        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_disabled)

        val onNotificationTapPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(
                Intent(
                    context,
                    Class.forName("com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity")
                )
            )
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val restartVpnIntent = Intent(context, VpnReminderReceiver::class.java).let { intent ->
            intent.action = TrackerBlockingVpnService.ACTION_VPN_REMINDER_RESTART
            PendingIntent.getBroadcast(context, 0, intent, 0)
        }

        return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(onNotificationTapPendingIntent)
            .setCustomContentView(notificationLayout)
            .setPriority(NotificationCompat.DEFAULT_ALL)
            .setSilent(silent)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_vpn_notification_24,
                    context.getString(R.string.atp_EnableCTA),
                    restartVpnIntent
                )
            )
            .addAction(NotificationActionReportIssue.reportIssueNotificationAction(context))
            .setChannelId(VPN_ALERTS_CHANNEL_ID)
            .build()
    }

    override fun buildStatusNotification(
        context: Context,
        deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification,
        onNotificationPressedCallback: ResultReceiver
    ): Notification {
        registerStatusChannel(context)

        val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_report)

        val notificationImage = getNotificationImage(deviceShieldNotification)
        notificationLayout.setImageViewResource(R.id.deviceShieldNotificationStatusIcon, notificationImage)
        notificationLayout.setTextViewText(R.id.deviceShieldNotificationText, deviceShieldNotification.title)

        return buildNotification(context, notificationLayout, onNotificationPressedCallback)
    }

    private fun getNotificationImage(deviceShieldNotification: DeviceShieldNotificationFactory.DeviceShieldNotification): Int {
        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_GOOGLE)) {
            return R.drawable.ic_apptb_google
        }

        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_AMAZON)) {
            return R.drawable.ic_apptb_amazon
        }

        if (deviceShieldNotification.title.contains(TRACKER_COMPANY_FACEBOOK)) {
            return R.drawable.ic_apptb_facebook
        }

        return R.drawable.ic_apptb_default
    }

    private fun buildNotification(
        context: Context,
        content: RemoteViews,
        resultReceiver: ResultReceiver? = null
    ): Notification {
        registerAlertChannel(context)

        val vpnControllerIntent = DeviceShieldTrackerActivity.intent(context = context, onLaunchCallback = resultReceiver)
        val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(vpnControllerIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(vpnControllerPendingIntent)
            .setSilent(true)
            .setCustomContentView(content)
            .setAutoCancel(true)
            .addAction(NotificationActionReportIssue.reportIssueNotificationAction(context))
            .setChannelId(VPN_ALERTS_CHANNEL_ID)
            .build()
    }

    companion object {

        private const val VPN_ALERTS_CHANNEL_ID = "DeviceShieldAlertChannel"
        private const val VPN_STATUS_CHANNEL_ID = "AppTpStatusChannel"
        private const val TRACKER_COMPANY_GOOGLE = "Google"
        private const val TRACKER_COMPANY_FACEBOOK = "Facebook"
        private const val TRACKER_COMPANY_AMAZON = "Amazon"
    }
}
