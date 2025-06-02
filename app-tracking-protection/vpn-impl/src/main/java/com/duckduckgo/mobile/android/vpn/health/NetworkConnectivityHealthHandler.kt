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

package com.duckduckgo.mobile.android.vpn.health

import android.content.Context
import android.os.PowerManager
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.extensions.isAirplaneModeOn
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.util.getActiveNetwork
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.connectivity.VpnConnectivityLossListenerPlugin
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SocketChannel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

// We use an IP address instead of a domain name to skip DNS resolution
private const val PROBED_ADDRESS = "1.1.1.1"

@ContributesMultibinding(VpnScope::class)
class NetworkConnectivityHealthHandler @Inject constructor(
    private val context: Context,
    private val pixel: DeviceShieldPixels,
    private val trackerBlockingVpnService: Provider<TrackerBlockingVpnService>,
    private val vpnConnectivityLossListenerPluginPoint: PluginPoint<VpnConnectivityLossListenerPlugin>,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {
    private val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                delay(15_000)
                if (powerManager.isInteractive && !context.isAirplaneModeOn() && !hasVpnConnectivity()) {
                    if (hasDeviceConnectivity()) {
                        vpnConnectivityLossListenerPluginPoint.getPlugins().forEach {
                            logcat { "Calling onVpnConnectivityLoss on $it" }
                            it.onVpnConnectivityLoss(coroutineScope)
                        }
                        logcat { "Active VPN network does not have connectivity" }
                        pixel.reportVpnConnectivityError()
                    } else {
                        logcat { "Device doesn't have connectivity either" }
                        pixel.reportDeviceConnectivityError()
                    }
                } else {
                    vpnConnectivityLossListenerPluginPoint.getPlugins().forEach {
                        logcat { "Calling onVpnConnected on $it" }
                        it.onVpnConnected(coroutineScope)
                    }
                }
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStateMonitor.VpnStopReason,
    ) {
        job.cancel()
    }

    private fun hasVpnConnectivity(): Boolean {
        context.getActiveNetwork()?.let { activeNetwork ->
            var socket: Socket? = null
            try {
                socket = activeNetwork.socketFactory.createSocket()
                socket.connect(InetSocketAddress(PROBED_ADDRESS, 443), 5000)
                logcat { "Validated $activeNetwork VPN network has connectivity to $PROBED_ADDRESS" }
                return true
            } catch (t: Throwable) {
                logcat(ERROR) { t.asLog() }
                return false
            } finally {
                runCatching { socket?.close() }
            }
        }

        // default to "has connectivity"
        return true
    }

    private fun hasDeviceConnectivity(): Boolean {
        context.getActiveNetwork()?.let { activeNetwork ->
            var socket: Socket? = null
            try {
                socket = SocketChannel.open().socket()
                trackerBlockingVpnService.get().protect(socket)
                socket.connect(InetSocketAddress(PROBED_ADDRESS, 443), 5000)
                logcat { "Validated $activeNetwork device network has connectivity to $PROBED_ADDRESS" }
                return true
            } catch (t: Throwable) {
                logcat(ERROR) { t.asLog() }
                return false
            } finally {
                runCatching { socket?.close() }
            }
        }

        // default to "has connectivity"
        return true
    }
}
