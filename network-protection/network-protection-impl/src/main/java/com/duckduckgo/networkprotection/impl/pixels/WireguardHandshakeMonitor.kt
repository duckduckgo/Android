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

package com.duckduckgo.networkprotection.impl.pixels

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.WgProtocol
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import org.threeten.bp.Instant

@ContributesMultibinding(VpnScope::class)
class WireguardHandshakeMonitor @Inject constructor(
    private val wgProtocol: WgProtocol,
    private val pixels: NetworkProtectionPixels,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val currentNetworkState: CurrentNetworkState,
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    private val failureReported = AtomicBoolean(false)

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch {
            startHandShakeMonitoring()
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch {
            startHandShakeMonitoring()
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        currentNetworkState.stop()
        job.cancel()
    }

    private suspend fun startHandShakeMonitoring() = withContext(dispatcherProvider.io()) {
        if (networkProtectionState.isEnabled()) {
            failureReported.set(false)
            currentNetworkState.start()

            while (isActive && networkProtectionState.isEnabled()) {
                val nowSeconds = Instant.now().epochSecond
                val lastHandshakeEpocSeconds = wgProtocol.getStatistics().lastHandshakeEpochSeconds
                if (lastHandshakeEpocSeconds > 0) {
                    val diff = nowSeconds - lastHandshakeEpocSeconds
                    if (diff.seconds.inWholeMinutes > REPORT_TUNNEL_FAILURE_IN_THRESHOLD_MINUTES && currentNetworkState.isConnected()) {
                        logcat(WARN) { "Last handshake was more than 5 minutes ago" }
                        // skip if previously reported
                        if (!failureReported.getAndSet(true)) {
                            pixels.reportTunnelFailure()
                        } else {
                            logcat { "Last handshake was already reported, skipping" }
                        }
                    } else if (diff.seconds.inWholeMinutes <= REPORT_TUNNEL_FAILURE_RECOVERY_THRESHOLD_MINUTES) {
                        if (failureReported.getAndSet(false)) {
                            logcat(WARN) { "Recovered from tunnel failure" }
                            pixels.reportTunnelFailureRecovered()
                        }
                    }
                }
                delay(1.minutes.inWholeMilliseconds)
            }
        }
    }

    companion object {
        // WG handshakes happen every 2min, this means we'd miss 2+ handshakes
        private const val REPORT_TUNNEL_FAILURE_IN_THRESHOLD_MINUTES = 5

        // WG handshakes happen every 2min
        private const val REPORT_TUNNEL_FAILURE_RECOVERY_THRESHOLD_MINUTES = 2
    }
}

// Open for testing only
open class CurrentNetworkState @Inject constructor(
    context: Context,
) {
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val isWifiAvailable = AtomicBoolean(true)
    private val isCellAvailable = AtomicBoolean(true)

    private val wifiNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            isWifiAvailable.set(true)
        }

        override fun onLost(network: Network) {
            isWifiAvailable.set(false)
        }
    }

    private val cellularNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            isCellAvailable.set(true)
        }

        override fun onLost(network: Network) {
            isCellAvailable.set(false)
        }
    }

    internal fun start() {
        runCatching {
            val wifiNetworkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            val cellularNetworkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()

            connectivityManager.registerNetworkCallback(wifiNetworkRequest, wifiNetworkCallback)
            connectivityManager.registerNetworkCallback(cellularNetworkRequest, cellularNetworkCallback)
        }
    }

    internal fun stop() {
        runCatching {
            connectivityManager.unregisterNetworkCallback(wifiNetworkCallback)
            connectivityManager.unregisterNetworkCallback(cellularNetworkCallback)
        }
    }
    internal fun isConnected(): Boolean {
        return isWifiAvailable.get() || isCellAvailable.get()
    }
}
