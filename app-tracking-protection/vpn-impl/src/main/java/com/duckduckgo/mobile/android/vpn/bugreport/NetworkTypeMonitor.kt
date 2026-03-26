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

package com.duckduckgo.mobile.android.vpn.bugreport

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.Connection
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkInfo
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkState
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkState.AVAILABLE
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkState.LOST
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkType
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkType.CELLULAR
import com.duckduckgo.mobile.android.vpn.bugreport.NetworkTypeCollector.NetworkType.WIFI
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.frybits.harmony.getHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(scope = VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class NetworkTypeMonitor @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : VpnServiceCallbacks {

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val adapter = Moshi.Builder().build().adapter(NetworkInfo::class.java)

    private val preferences: SharedPreferences
        get() = context.getHarmonySharedPreferences(FILENAME)

    private var currentNetworkInfo: String?
        get() = preferences.getString(NETWORK_INFO_KEY, null)
        set(value) = preferences.edit { putString(NETWORK_INFO_KEY, value) }

    private val wifiNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    private val cellularNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private val wifiNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(Connection(network.networkHandle, WIFI, AVAILABLE))
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(Connection(network.networkHandle, WIFI, LOST))
        }
    }

    private val cellularNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(
                Connection(network.networkHandle, CELLULAR, AVAILABLE, context.mobileNetworkCode(CELLULAR)),
            )
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(
                Connection(network.networkHandle, CELLULAR, LOST, context.mobileNetworkCode(CELLULAR)),
            )
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            it.safeRegisterNetworkCallback(wifiNetworkRequest, wifiNetworkCallback)
            it.safeRegisterNetworkCallback(cellularNetworkRequest, cellularNetworkCallback)
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            // always unregistered, even if not previously registered
            it.safeUnregisterNetworkCallback(wifiNetworkCallback)
            it.safeUnregisterNetworkCallback(cellularNetworkCallback)
        }
    }

    private fun ConnectivityManager.safeRegisterNetworkCallback(
        networkRequest: NetworkRequest,
        networkCallback: NetworkCallback,
    ) {
        kotlin.runCatching {
            registerNetworkCallback(networkRequest, networkCallback)
        }.onFailure {
            logcat(ERROR) { it.asLog() }
        }
    }

    private fun ConnectivityManager.safeUnregisterNetworkCallback(networkCallback: NetworkCallback) {
        kotlin.runCatching {
            unregisterNetworkCallback(networkCallback)
        }.onFailure {
            logcat(ERROR) { it.asLog() }
        }
    }

    private fun updateNetworkInfo(connection: Connection) {
        coroutineScope.launch(databaseDispatcher) {
            try {
                val previousNetworkInfo: NetworkInfo? = currentNetworkInfo?.let { adapter.fromJson(it) }

                // If android notifies of a lost connection that is not the current one, just return
                if (connection.state == NetworkState.LOST &&
                    previousNetworkInfo?.currentNetwork?.netId != null &&
                    connection.netId != previousNetworkInfo.currentNetwork.netId
                ) {
                    logcat { "Lost of previously switched connection: $connection, current: ${previousNetworkInfo.currentNetwork}" }
                    return@launch
                }

                // Calculate timestamp for when the network type last switched
                val previousNetworkType = previousNetworkInfo?.currentNetwork
                val didChange = previousNetworkType != connection
                val lastSwitchTimestampMillis = if (didChange) {
                    SystemClock.elapsedRealtime()
                } else {
                    previousNetworkInfo?.lastSwitchTimestampMillis ?: SystemClock.elapsedRealtime()
                }

                // Calculate how long ago the network type last switched
                val previousNetwork = previousNetworkInfo?.currentNetwork
                val secondsSinceLastSwitch = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - lastSwitchTimestampMillis)
                val jsonInfo =
                    adapter.toJson(
                        NetworkInfo(
                            currentNetwork = connection,
                            previousNetwork = previousNetwork,
                            lastSwitchTimestampMillis = lastSwitchTimestampMillis,
                            secondsSinceLastSwitch = secondsSinceLastSwitch,
                        ),
                    )
                currentNetworkInfo = jsonInfo
                logcat { "New network info $jsonInfo" }
            } catch (t: Throwable) {
                logcat(WARN) { t.asLog() }
            }
        }
    }

    private fun Context.mobileNetworkCode(networkType: NetworkType): Int? {
        return if (networkType == NetworkType.WIFI) {
            null
        } else {
            return kotlin.runCatching { resources.configuration.mnc }.getOrNull()
        }
    }

    companion object {
        internal const val FILENAME = "network.type.collector.file.v1"
        internal const val NETWORK_INFO_KEY = "network.info.key"
    }
}
