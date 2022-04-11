/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.network.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class ConnectivityChangeReceiver @Inject constructor(
    private val vpnConfig: AppTpFeatureConfig,
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : VpnServiceCallbacks {

    private val connectivityChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Filter VPN connectivity changes
            val networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_DUMMY)
            if (networkType == ConnectivityManager.TYPE_VPN) return

            coroutineScope.launch {
                TrackerBlockingVpnService.restartVpnService(context)
            }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (vpnConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)) {
            val intentFilter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            context.registerReceiver(connectivityChangedReceiver, intentFilter)
        } else {
            Timber.v("NetworkSwitchHandling disabled...skip restarting VPN upon network switch")
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        // always try to unregister
        kotlin.runCatching { context.unregisterReceiver(connectivityChangedReceiver) }
    }
}
