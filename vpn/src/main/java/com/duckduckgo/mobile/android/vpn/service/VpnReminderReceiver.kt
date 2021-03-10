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

package com.duckduckgo.mobile.android.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService.Companion.ACTION_VPN_REMINDER_RESTART
import com.duckduckgo.mobile.android.vpn.ui.notification.DeviceShieldAlertNotificationBuilder
import dummy.ui.VpnPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

class VpnReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        Timber.i("VpnReminderReceiver onReceive ${intent.action}")
        if (intent.action == "android.intent.action.BOOT_COMPLETED" || intent.action == TrackerBlockingVpnService.ACTION_VPN_REMINDER) {
            Timber.v("Checking if VPN is running")

            goAsync {
                val manager = NotificationManagerCompat.from(context)
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

                    manager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, notification)
                }
            }
        }

        if (intent.action == ACTION_VPN_REMINDER_RESTART) {
            Timber.v("Vpn will restart because the user asked it")
            goAsync {
                TrackerBlockingVpnService.startIntent(context).also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(it)
                    } else {
                        context.startService(it)
                    }
                }
            }
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

fun BroadcastReceiver.goAsync(
    coroutineScope: CoroutineScope = GlobalScope,
    block: suspend () -> Unit
) {
    val result = goAsync()
    coroutineScope.launch {
        try {
            block()
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            result.finish()
        }
    }
}
