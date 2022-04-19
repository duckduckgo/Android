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
import android.net.ConnectivityManager
import com.duckduckgo.app.global.extensions.isAirplaneModeOn
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SocketChannel
import javax.inject.Inject
import javax.inject.Provider

private const val WWW_DUCKDUCKGO_COM = "www.duckduckgo.com"

@ContributesMultibinding(VpnScope::class)
class NetworkConnectivityHealthHandler @Inject constructor(
    private val context: Context,
    private val pixel: Pixel,
    private val healthMetricCounter: HealthMetricCounter,
    private val appTpFeatureConfig: AppTpFeatureConfig,
    private val trackerBlockingVpnService: Provider<TrackerBlockingVpnService>,
) : VpnServiceCallbacks {
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val job = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (!appTpFeatureConfig.isEnabled(AppTpSetting.ConnectivityChecks)) {
            Timber.d("AppTpSetting.ConnectivityChecks is disabled")
            return
        }

        job += coroutineScope.launch {
            while (isActive) {
                delay(10_000)
                if (!hasVpnConnectivity() && !context.isAirplaneModeOn()) {
                    if (hasDeviceConnectivity()) {
                        Timber.d("Active VPN network does not have connectivity")
                        pixel.enqueueFire(PixelName { "m_atp_report_no_vpn_connectivity_c" })
                        healthMetricCounter.onNoNetworkConnectivity()
                    } else {
                        Timber.d("Device doesn't have connectivity either")
                        pixel.enqueueFire(PixelName { "m_atp_report_no_device_connectivity_c" })
                    }
                }
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        job.cancel()
    }

    private fun hasVpnConnectivity(): Boolean {
        connectivityManager.activeNetwork?.let { activeNetwork ->
            var socket: Socket? = null
            try {
                socket = activeNetwork.socketFactory.createSocket()
                socket.connect(InetSocketAddress(WWW_DUCKDUCKGO_COM, 443), 5000)
                Timber.v("Validated $activeNetwork VPN network has connectivity to $WWW_DUCKDUCKGO_COM")
                return true
            } catch (t: Throwable) {
                Timber.e(t, "No connectivity for network $activeNetwork")
                return false
            } finally {
                runCatching { socket?.close() }
            }
        }

        // default to "has connectivity"
        return true
    }

    private fun hasDeviceConnectivity(): Boolean {
        connectivityManager.activeNetwork?.let { activeNetwork ->
            var socket: Socket? = null
            try {
                socket = SocketChannel.open().socket()
                trackerBlockingVpnService.get().protect(socket)
                socket.connect(InetSocketAddress(WWW_DUCKDUCKGO_COM, 443), 5000)
                Timber.v("Validated $activeNetwork device network has connectivity to $WWW_DUCKDUCKGO_COM")
                return true
            } catch (t: Throwable) {
                Timber.e(t, "No connectivity for network $activeNetwork")
                return false
            } finally {
                runCatching { socket?.close() }
            }
        }

        // default to "has connectivity"
        return true
    }

    private fun PixelName(block: () -> String): Pixel.PixelName {
        return object : Pixel.PixelName {
            override val pixelName: String
                get() = block()
        }
    }
}
