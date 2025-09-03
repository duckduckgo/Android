/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ReceiverScope
import dagger.android.AndroidInjection
import javax.inject.Inject
import logcat.LogPriority.WARN
import logcat.logcat

@InjectWith(ReceiverScope::class)
class PersistentNotificationDismissedReceiver : BroadcastReceiver() {
    @Inject
    lateinit var vpnNotificationStore: VpnNotificationStore

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        logcat { "PersistentNotificationDismissedReceiver onReceive ${intent.action}" }
        val pendingResult = goAsync()

        if (intent.action == ACTION_VPN_PERSISTENT_NOTIF_DISMISSED) {
            // handle dismissed notif
            com.duckduckgo.mobile.android.vpn.service.goAsync(pendingResult) {
                logcat { "PersistentNotificationDismissedReceiver dismissed notification received" }
                vpnNotificationStore.persistentNotifDimissedTimestamp = System.currentTimeMillis()
            }
        } else {
            logcat(WARN) { "PersistentNotificationDismissedReceiver: unknown action ${intent.action}" }
            pendingResult?.finish()
        }
    }

    companion object {
        internal const val ACTION_VPN_PERSISTENT_NOTIF_DISMISSED = "com.duckduckgo.vpn.ACTION_VPN_PERSISTENT_NOTIF_DISMISSED"
    }
}
