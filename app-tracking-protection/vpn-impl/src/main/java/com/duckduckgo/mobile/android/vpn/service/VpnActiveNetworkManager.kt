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

package com.duckduckgo.mobile.android.vpn.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import androidx.core.os.postDelayed
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.network.util.getActiveNetwork
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

/**
 * This class is bound to the VPN service lifetime.
 * It watches for changes in the underlying networks (WIFI and CELL) and decides
 * whether to restart the VPN service or not
 */
@ContributesMultibinding(VpnScope::class)
class VpnActiveNetworkManager @Inject constructor(
    private val appTpFeatureConfig: AppTpFeatureConfig,
    private val service: Provider<TrackerBlockingVpnService>,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val context: Context,
) : VpnServiceCallbacks {

    private val isInterceptDnsRequestsEnabled: Boolean
        get() = appTpFeatureConfig.isEnabled(AppTpSetting.InterceptDnsRequests)

    private var currentNetwork: AtomicReference<String?> = AtomicReference(null)

    private val cellularNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private val wifiNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        if (runBlocking { !shouldHandleNetworkChanges() }) {
            logcat { "Do not handle network changes" }
            return
        }

        if (isInterceptDnsRequestsEnabled) {
            service.get().configureUnderlyingNetworks()

            (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
                it.safeRegisterNetworkCallback(wifiNetworkRequest, wifiNetworkCallback)
                it.safeRegisterNetworkCallback(cellularNetworkRequest, cellularNetworkCallback)
            }
        } else {
            logcat { "DNS based blocking is disabled, noop" }
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        // both when VPN is first started and reconfigured we check if we need to handle network changes
        onVpnStarted(coroutineScope)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            it.safeUnregisterNetworkCallback(wifiNetworkCallback)
            it.safeUnregisterNetworkCallback(cellularNetworkCallback)
        }
    }

    private suspend fun restartIfActiveNetworkChanged() {
        val service = service.get()
        if (ignoreRestartEvent()) {
            service.configureUnderlyingNetworks()
            logcat { "AppTP (only) is not enabled, ignoring restartIfActiveNetworkChanged() event" }
            return
        }

        val activeNetwork = service.getActiveNetwork()
        if (activeNetwork == null) {
            logcat { "Current active network is NULL, ignore restart" }
            return
        }
        val previousNetwork = currentNetwork.getAndSet(activeNetwork.toString())
        if (previousNetwork == null) {
            logcat { "Previous network is NULL, ignore restart" }
            return
        }
        if (currentNetwork.get() == null) {
            logcat { "Current network is NULL, ignore restart" }
            return
        }

        if (previousNetwork == currentNetwork.get()) {
            logcat { "Current network DID NOT change, ignore restart" }
            return
        }

        logcat { "Current network changed, restart service" }
        vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
    }

    private suspend fun ignoreRestartEvent(): Boolean {
        return vpnFeaturesRegistry.getRegisteredFeatures().size > 1 || !vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)
    }

    private suspend fun shouldHandleNetworkChanges(): Boolean {
        return vpnFeaturesRegistry.getRegisteredFeatures().size == 1 && vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)
    }

    private val wifiNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            logcat { "WIFI available" }
            if (isInterceptDnsRequestsEnabled) {
                // The WIFI available events triggers very quickly, and by then the default network configs haven't yet propagated
                // give it some time before we notify the VPN service
                Handler().postDelayed(4000) {
                    coroutineScope.launch {
                        restartIfActiveNetworkChanged()
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            logcat { "WIFI lost" }
            if (isInterceptDnsRequestsEnabled) {
                coroutineScope.launch {
                    restartIfActiveNetworkChanged()
                }
            }
        }
    }

    private val cellularNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            logcat { "CELL available" }
            if (isInterceptDnsRequestsEnabled) {
                coroutineScope.launch {
                    restartIfActiveNetworkChanged()
                }
            }
        }

        override fun onLost(network: Network) {
            logcat { "CELL lost" }
            if (isInterceptDnsRequestsEnabled) {
                coroutineScope.launch {
                    restartIfActiveNetworkChanged()
                }
            }
        }
    }

    private fun ConnectivityManager.safeRegisterNetworkCallback(networkRequest: NetworkRequest, networkCallback: NetworkCallback) {
        kotlin.runCatching {
            registerNetworkCallback(networkRequest, networkCallback)
        }.onFailure {
            logcat(LogPriority.ERROR) { it.asLog() }
        }
    }

    private fun ConnectivityManager.safeUnregisterNetworkCallback(networkCallback: NetworkCallback) {
        kotlin.runCatching {
            unregisterNetworkCallback(networkCallback)
        }.onFailure {
            logcat(LogPriority.ERROR) { it.asLog() }
        }
    }
}
