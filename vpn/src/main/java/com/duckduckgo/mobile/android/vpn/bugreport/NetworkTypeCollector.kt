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
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.mobile.android.vpn.feature.isPrivateDnsSupportEnabled
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
    private val featureToggle: FeatureToggle,
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

    private val wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(NetworkType.WIFI)
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(NetworkType.NO_NETWORK)
        }
    }

    private val cellularNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(NetworkType.CELLULAR)
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(NetworkType.NO_NETWORK)
        }
    }

    private val privateDnsCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)

            if (featureToggle.isPrivateDnsSupportEnabled()) {
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

    private fun updateNetworkInfo(networkType: NetworkType) {
        coroutineScope.launch(databaseDispatcher) {
            try {
                val previousNetworkInfo: NetworkInfo? = currentNetworkInfo?.let { adapter.fromJson(it) }

                // Calculate timestamp for when the network type last switched
                val previousNetworkType = previousNetworkInfo?.let { NetworkType.valueOf(it.currentNetwork) }
                val didSwitch = previousNetworkType != networkType
                val lastSwitchTimestampMillis = if (didSwitch) {
                    SystemClock.elapsedRealtime()
                } else {
                    previousNetworkInfo?.lastSwitchTimestampMillis ?: SystemClock.elapsedRealtime()
                }

                // Calculate how long ago the network type last switched
                val previousNetwork: String? = previousNetworkInfo?.currentNetwork
                val secondsSinceLastSwitch = TimeUnit.MILLISECONDS.toSeconds(SystemClock.elapsedRealtime() - lastSwitchTimestampMillis)
                val jsonInfo =
                    adapter.toJson(
                        NetworkInfo(
                            currentNetwork = networkType.toString(),
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
        val info = currentNetworkInfo?.let { adapter.toJson(adapter.fromJson(it)?.copy(lastSwitchTimestampMillis = -999)) } ?: return JSONObject()

        return JSONObject(info)
    }

    private fun ConnectivityManager.safeUnregisterNetworkCallback(networkCallback: NetworkCallback) {
        kotlin.runCatching {
            unregisterNetworkCallback(networkCallback)
        }.onFailure {
            Timber.e(it, "Error unregistering the network callback")
        }
    }

    internal data class NetworkInfo(
        val currentNetwork: String,
        val previousNetwork: String? = null,
        val lastSwitchTimestampMillis: Long,
        val secondsSinceLastSwitch: Long
    )

    internal enum class NetworkType {
        WIFI,
        CELLULAR,
        NO_NETWORK,
    }

    companion object {
        private const val FILENAME = "network.type.collector.file"
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
