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

package com.duckduckgo.mobile.android.vpn.service

import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.prefs.PREFS_FILENAME
import com.duckduckgo.mobile.android.vpn.prefs.PREFS_KEY_REMINDER_NOTIFICATION_SHOWN
import com.duckduckgo.mobile.android.vpn.service.VpnReminderNotificationContentPlugin.Type.DISABLED
import com.duckduckgo.mobile.android.vpn.service.notification.getHighestPriorityPluginForType
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnReminderNotificationBuilder
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface VpnReminderReceiverManager {
    fun showReminderNotificationIfVpnDisabled(context: Context)
}

@ContributesBinding(AppScope::class)
class AndroidVpnReminderReceiverManager @Inject constructor(
    private val deviceShieldPixels: DeviceShieldPixels,
    private val notificationManager: NotificationManagerCompat,
    private val vpnReminderNotificationBuilder: VpnReminderNotificationBuilder,
    private val vpnReminderNotificationContentPluginPoint: PluginPoint<VpnReminderNotificationContentPlugin>,
) : VpnReminderReceiverManager {

    override fun showReminderNotificationIfVpnDisabled(context: Context) {
        if (TrackerBlockingVpnService.isServiceRunning(context)) {
            logcat { "Vpn is already running, nothing to show" }
        } else {
            logcat { "Vpn is not running, showing reminder notification" }
            val notification = vpnReminderNotificationContentPluginPoint.getHighestPriorityPluginForType(DISABLED)?.getContent()?.let { content ->
                val actualContent = if (wasReminderNotificationShown(context)) {
                    content.copy(true)
                } else {
                    notificationWasShown(context)
                    content.copy(false)
                }
                vpnReminderNotificationBuilder.buildReminderNotification(actualContent)
            }
            if (notification != null) {
                deviceShieldPixels.didShowReminderNotification()
                notificationManager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun wasReminderNotificationShown(context: Context): Boolean {
        return prefs(context).getBoolean(PREFS_KEY_REMINDER_NOTIFICATION_SHOWN, false)
    }

    private fun notificationWasShown(context: Context) {
        prefs(context).edit { putBoolean(PREFS_KEY_REMINDER_NOTIFICATION_SHOWN, true) }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }
}
