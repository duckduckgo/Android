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
import com.duckduckgo.mobile.android.vpn.model.VpnTrackerAndCompany
import com.duckduckgo.mobile.android.vpn.ui.report.PrivacyReportActivity

class VpnNotificationBuilder {

    companion object {

        private const val VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID = "TrackerProtectionVPN"
        private const val VPN_ALERTS_CHANNEL_ID = "DeviceShieldAlertChannel"

        fun buildDeviceShieldEnabled(
            context: Context,
            trackersBlocked: List<VpnTrackerAndCompany>,
            trackerCompaniesTotal: Int,
            trackerCompanies: List<VpnTrackerAndCompany>
        ): Notification {
            registerOngoingNotificationChannel(context)

            val vpnControllerIntent = Intent(context, PrivacyReportActivity::class.java)
            val vpnShowDashboardPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val notificationHeader = generateNotificationHeader(trackersBlocked, context)
            val notificationMessage = generateNotificationMessage(trackersBlocked, trackerCompaniesTotal, trackerCompanies, context)

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_enabled)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationHeader, notificationHeader)
            notificationLayout.setTextViewText(R.id.deviceShieldNotificationMessage, notificationMessage)

            return NotificationCompat.Builder(context, VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnShowDashboardPendingIntent)
                .setCustomContentView(notificationLayout)
                .setOngoing(true)
                .setChannelId(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID)
                .build()
        }

        private fun generateNotificationHeader(
            trackersBlocked: List<VpnTrackerAndCompany>,
            context: Context
        ): String {
            return if (trackersBlocked.isEmpty()) {
                context.getString(R.string.deviceShieldNotificationInitialStateHeader)
            } else {
                context.resources.getQuantityString(R.plurals.deviceShieldNotificationTrackers, trackersBlocked.size, trackersBlocked.size)
            }
        }

        private fun generateNotificationMessage(
            trackersBlocked: List<VpnTrackerAndCompany>,
            trackerCompaniesTotal: Int,
            trackerCompanies: List<VpnTrackerAndCompany>,
            context: Context
        ): String {
            return if (trackersBlocked.isEmpty()) {
                context.getString(R.string.deviceShieldNotificationInitialStateMessage)
            } else {
                when (trackerCompaniesTotal) {
                    1 -> context.getString(R.string.deviceShieldNotificationOneCompanyBlocked, trackerCompanies.first().trackerCompany.company)
                    2 -> context.getString(
                        R.string.deviceShieldNotificationTwoCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company
                    )
                    3 -> context.getString(
                        R.string.deviceShieldNotificationThreeCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company,
                        trackerCompanies[2].trackerCompany.company
                    )
                    else -> context.getString(
                        R.string.deviceShieldNotificationFourCompaniesBlocked,
                        trackerCompanies.first().trackerCompany.company,
                        trackerCompanies[1].trackerCompany.company,
                        trackerCompaniesTotal - 2
                    )
                }
            }
        }

        private fun registerOngoingNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(VPN_FOREGROUND_SERVICE_NOTIFICATION_CHANNEL_ID, "Tracker Protection Running", NotificationManager.IMPORTANCE_MIN)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun registerReminderChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(VPN_ALERTS_CHANNEL_ID, "Device Shield Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun buildReminderNotification(context: Context): Notification {
            registerReminderChannel(context)

            val vpnControllerIntent = Intent(context, PrivacyReportActivity::class.java)
            val vpnControllerPendingIntent: PendingIntent? = TaskStackBuilder.create(context).run {
                addNextIntentWithParentStack(vpnControllerIntent)
                getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
            }

            val notificationLayout = RemoteViews(context.packageName, R.layout.notification_device_shield_disabled)

            return NotificationCompat.Builder(context, VPN_ALERTS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_device_shield_notification_logo)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(vpnControllerPendingIntent)
                .setCustomContentView(notificationLayout)
                .setOngoing(true)
                .setChannelId(VPN_ALERTS_CHANNEL_ID)
                .build()
        }

    }

}
