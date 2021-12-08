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
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService.Companion.ACTION_VPN_REMINDER_RESTART
import com.duckduckgo.mobile.android.vpn.ui.notification.ReminderNotificationPressedHandler
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class VpnReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var deviceShieldPixels: DeviceShieldPixels

    @Inject
    lateinit var notificationPressedHandler: ReminderNotificationPressedHandler

    @Inject
    lateinit var reminderManager: VpnReminderReceiverManager

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)

        Timber.i("VpnReminderReceiver onReceive ${intent.action}")
        val pendingResult = goAsync()

        if (intent.action == ACTION_VPN_REMINDER_RESTART) {
            Timber.v("Vpn will restart because the user asked it")
            deviceShieldPixels.enableFromReminderNotification()
            goAsync(pendingResult) {
                TrackerBlockingVpnService.startService(context)
            }
        } else {
            Timber.w("VpnReminderReceiver: unknown action")
            pendingResult?.finish()
        }
    }

}

fun goAsync(
    pendingResult: BroadcastReceiver.PendingResult?,
    coroutineScope: CoroutineScope = GlobalScope,
    block: suspend () -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        try {
            block()
        } finally {
            // Always call finish(), even if the coroutineScope was cancelled
            pendingResult?.finish()
        }
    }
}
