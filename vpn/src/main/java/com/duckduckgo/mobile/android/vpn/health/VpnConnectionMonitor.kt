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

package com.duckduckgo.mobile.android.vpn.health

import android.content.Context
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@VpnScope
@ContributesMultibinding(VpnObjectGraph::class)
class VpnConnectionMonitor @Inject constructor(
    private val context: Context,
    private val deviceShieldPixels: DeviceShieldPixels
) : VpnServiceCallbacks {
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            while (isActive) {
                if (!isTunInterfaceUp()) {
                    Timber.e("TUN interface seems to be down!...restarting VPN")
                    deviceShieldPixels.vpnTunInterfaceIsDown()
                    TrackerBlockingVpnService.restartVpnService(context)
                }
                delay(TimeUnit.SECONDS.toMillis(10))
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        // noop
    }

    private fun isTunInterfaceUp(): Boolean {
        try {
            for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
                if (networkInterface.isUp && networkInterface.name.contains("tun")) {
                    Timber.d("${networkInterface.name} interface is UP")
                    return true
                }
            }
        } catch (t: Throwable) {
            return false
        }

        return false
    }
}
