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
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.ui.notification.VpnNotificationBuilder
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
                val vpnDatabase = VpnDatabase.getInstance(context)
                if (vpnDatabase.vpnStateDao().getOneOff().isRunning) {
                    Timber.v("Vpn is already running, nothing to show")
                } else {
                    Timber.v("Vpn is not running, showing reminder notification")
                    val reminder = VpnNotificationBuilder.buildReminderNotification(context)
                    val manager = NotificationManagerCompat.from(context)
                    manager.notify(TrackerBlockingVpnService.VPN_REMINDER_NOTIFICATION_ID, reminder)
                }
            }
        }
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
