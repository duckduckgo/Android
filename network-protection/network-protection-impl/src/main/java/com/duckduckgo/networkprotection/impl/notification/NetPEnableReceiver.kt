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

package com.duckduckgo.networkprotection.impl.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.Vpn
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import dagger.android.AndroidInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

@InjectWith(ReceiverScope::class)
class NetPEnableReceiver : BroadcastReceiver() {

    @Inject lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Inject lateinit var vpn: Vpn

    @Inject lateinit var pixels: NetworkProtectionPixels

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)

        logcat { "NetPEnableReceiver onReceive ${intent.action}" }
        val pendingResult = goAsync()

        if (intent.action == ACTION_NETP_ENABLE) {
            logcat { "NetP will restart because the user asked it" }
            goAsync(pendingResult) {
                vpnFeaturesRegistry.registerFeature(NetPVpnFeature.NETP_VPN)
            }
        } else if (intent.action == ACTION_VPN_SNOOZE_CANCEL) {
            logcat { "Entire VPN will be enabled because the user asked it" }
            goAsync(pendingResult) {
                pixels.reportVpnSnoozedCanceled()
                vpn.start()
            }
        } else {
            logcat(LogPriority.WARN) { "NetPEnableReceiver: unknown action" }
            pendingResult?.finish()
        }
    }

    companion object {
        internal const val ACTION_NETP_ENABLE = "com.duckduckgo.networkprotection.notification.ACTION_NETP_ENABLE"
        internal const val ACTION_VPN_SNOOZE_CANCEL = "com.duckduckgo.vpn.ACTION_VPN_SNOOZE_CANCEL"
    }
}

@Suppress("NoHardcodedCoroutineDispatcher")
fun goAsync(
    pendingResult: BroadcastReceiver.PendingResult?,
    coroutineScope: CoroutineScope = GlobalScope,
    block: suspend () -> Unit,
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
