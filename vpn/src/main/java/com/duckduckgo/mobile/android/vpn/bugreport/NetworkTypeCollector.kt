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

package com.duckduckgo.mobile.android.vpn.bugreport

import android.content.Context
import android.content.SharedPreferences
import android.net.*
import android.net.ConnectivityManager.NetworkCallback
import android.os.SystemClock
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.extensions.getPrivateDnsServerName
import com.duckduckgo.app.global.extensions.isPrivateDnsActive
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import dummy.ui.VpnPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnStateCollectorPlugin::class
)
@SingleInstanceIn(VpnScope::class)
class NetworkTypeCollector @Inject constructor(
    private val context: Context,
    private val vpnPreferences: VpnPreferences,
    private val appTpFeatureConfig: AppTpFeatureConfig,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : VpnStateCollectorPlugin, VpnServiceCallbacks {

    private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val adapter = Moshi.Builder().build().adapter(NetworkInfo::class.java)

    private val preferences: SharedPreferences
        get() = context.getSharedPreferences(FILENAME, Context.MODE_MULTI_PROCESS)

    private var currentNetworkInfo: String?
        get() = preferences.getString(NETWORK_INFO_KEY, null)
        set(value) = preferences.edit { putString(NETWORK_INFO_KEY, value) }

    private val wifiNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    private val cellularNetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .build()

    private val privateDnsRequest = NetworkRequest.Builder().build()

    private val wifiNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(Connection(network.networkHandle, NetworkType.WIFI, NetworkState.AVAILABLE))
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(Connection(network.networkHandle, NetworkType.WIFI, NetworkState.LOST))
        }
    }

    private val cellularNetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(
                Connection(network.networkHandle, NetworkType.CELLULAR, NetworkState.AVAILABLE, context.mobileNetworkCode(NetworkType.CELLULAR))
            )
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(
                Connection(network.networkHandle, NetworkType.CELLULAR, NetworkState.LOST, context.mobileNetworkCode(NetworkType.CELLULAR))
            )
        }
    }

    private val privateDnsCallback = object : NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)

            if (appTpFeatureConfig.isEnabled(AppTpSetting.PrivateDnsSupport)) {
                Timber.v(
                    "isPrivateDnsActive = %s, server = %s (%s)",
                    context.isPrivateDnsActive(),
                    context.getPrivateDnsServerName(),
                    runCatching { InetAddress.getAllByName(context.getPrivateDnsServerName()) }.getOrNull()?.map { it.hostAddress }
                )

                vpnPreferences.privateDns = if (context.isPrivateDnsActive()) {
                    context.getPrivateDnsServerName()
                } else {
                    null
                }
            } else {
                Timber.d("Private DNS support is disabled...skip")
            }
        }
    }

    override val collectorName = "networkInfo"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject = withContext(databaseDispatcher) {
        return@withContext getNetworkInfoJsonObject()
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            it.registerNetworkCallback(wifiNetworkRequest, wifiNetworkCallback)
            it.registerNetworkCallback(cellularNetworkRequest, cellularNetworkCallback)
            it.registerNetworkCallback(privateDnsRequest, privateDnsCallback)
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            it.safeUnregisterNetworkCallback(wifiNetworkCallback)
            it.safeUnregisterNetworkCallback(cellularNetworkCallback)
            it.safeUnregisterNetworkCallback(privateDnsCallback)
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
                    Timber.d("Lost of previously switched connection: $connection, current: ${previousNetworkInfo.currentNetwork}")
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
                            secondsSinceLastSwitch = secondsSinceLastSwitch
                        )
                    )
                currentNetworkInfo = jsonInfo
                Timber.v("New network info $jsonInfo")
            } catch (t: Throwable) {
                Timber.w(t, "Error updating the network info")
            }
        }
    }

    private fun updateSecondsSinceLastSwitch() {
        val networkInfo: NetworkInfo = currentNetworkInfo?.let { adapter.fromJson(it) } ?: return
        networkInfo.copy(
            secondsSinceLastSwitch = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - networkInfo.lastSwitchTimestampMillis)
        ).run {
            currentNetworkInfo = adapter.toJson(this)
        }
    }

    private fun getNetworkInfoJsonObject(): JSONObject {
        updateSecondsSinceLastSwitch()
        // redact the lastSwitchTimestampMillis from the report
        val info = currentNetworkInfo?.let {
            // Redact some values (set to -999) as they could be static values
            val temp = adapter.fromJson(it)
            adapter.toJson(
                temp?.copy(
                    lastSwitchTimestampMillis = -999,
                    currentNetwork = temp.currentNetwork.copy(netId = -999),
                    previousNetwork = temp.previousNetwork?.copy(netId = -999)
                )
            )
        } ?: return JSONObject()

        return JSONObject(info)
    }

    private fun ConnectivityManager.safeUnregisterNetworkCallback(networkCallback: NetworkCallback) {
        kotlin.runCatching {
            unregisterNetworkCallback(networkCallback)
        }.onFailure {
            Timber.e(it, "Error unregistering the network callback")
        }
    }

    private fun Context.mobileNetworkCode(networkType: NetworkType): Int? {
        return if (networkType == NetworkType.WIFI) {
            null
        } else {
            return kotlin.runCatching { resources.configuration.mnc }.getOrNull()
        }
    }

    internal data class NetworkInfo(
        val currentNetwork: Connection,
        val previousNetwork: Connection? = null,
        val lastSwitchTimestampMillis: Long,
        val secondsSinceLastSwitch: Long,
    )

    internal data class Connection(val netId: Long, val type: NetworkType, val state: NetworkState, val mnc: Int? = null)

    internal enum class NetworkType {
        WIFI,
        CELLULAR,
    }

    internal enum class NetworkState {
        AVAILABLE,
        LOST,
    }

    companion object {
        private const val FILENAME = "network.type.collector.file.v1"
        private const val NETWORK_INFO_KEY = "network.info.key"
    }
}

@Module
@ContributesTo(VpnScope::class)
abstract class NetworkTypeCollectorModule {
    @Binds
    @IntoSet
    @SingleInstanceIn(VpnScope::class)
    abstract fun NetworkTypeCollector.bind(): VpnServiceCallbacks
}
