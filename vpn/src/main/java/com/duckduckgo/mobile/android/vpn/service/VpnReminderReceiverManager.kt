/*
 * Copyright (c) 2021 DuckDuckGo
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
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldAlertNotificationBuilder
import com.squareup.anvil.annotations.ContributesBinding
import dummy.ui.VpnPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface VpnReminderReceiverManager {
    fun showReminderNotificationIfVpnDisabled(context: Context)
}

@ContributesBinding(AppObjectGraph::class)
@Singleton
class AndroidVpnReminderReceiverManager @Inject constructor(val deviceShieldPixels: DeviceShieldPixels, val notificationManager: NotificationManagerCompat) : VpnReminderReceiverManager {

    override fun showReminderNotificationIfVpnDisabled(context: Context) {
        if (TrackerBlockingVpnService.isServiceRunning(context)) {
            Timber.v("Vpn is already running, nothing to show")
        } else {
            Timber.v("Vpn is not running, showing reminder notification")
            val notification = if (wasReminderNotificationShown(context)) {
                DeviceShieldAlertNotificationBuilder.buildReminderNotification(context, true)
            } else {
                notificationWasShown(context)
                DeviceShieldAlertNotificationBuilder.buildReminderNotification(context, false)
            }

            deviceShieldPixels.didShowReminderNotification()
            notificationManager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
        }
    }

    private fun wasReminderNotificationShown(context: Context): Boolean {
        return prefs(context).getBoolean(VpnPreferences.PREFS_KEY_REMINDER_NOTIFICATION_SHOWN, false)
    }

    private fun notificationWasShown(context: Context) {
        prefs(context).edit { putBoolean(VpnPreferences.PREFS_KEY_REMINDER_NOTIFICATION_SHOWN, true) }
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(VpnPreferences.PREFS_FILENAME, Context.MODE_PRIVATE)
    }

}
