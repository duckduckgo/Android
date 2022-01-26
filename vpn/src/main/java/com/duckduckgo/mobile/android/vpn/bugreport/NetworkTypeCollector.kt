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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnStateCollectorPlugin::class
)
@SingleInstanceIn(VpnScope::class)
class NetworkTypeCollector @Inject constructor(
    private val context: Context,
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

    private val wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(NetworkType.WIFI)
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(null)
        }
    }

    private val cellularNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkInfo(NetworkType.CELLULAR)
        }

        override fun onLost(network: Network) {
            updateNetworkInfo(null)
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
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        (context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?)?.let {
            it.unregisterNetworkCallback(wifiNetworkCallback)
            it.unregisterNetworkCallback(cellularNetworkCallback)
        }
    }

    private fun updateNetworkInfo(networkType: NetworkType?) {
        coroutineScope.launch(databaseDispatcher) {
            try {
                val previousNetwork: String? = currentNetworkInfo?.let { adapter.fromJson(it)?.currentNetwork }
                val jsonInfo =
                    adapter.toJson(
                        NetworkInfo(
                            currentNetwork = networkType.toString(),
                            previousNetwork = previousNetwork,
                            secondsSinceLastSwitch = SystemClock.elapsedRealtime()
                        )
                    )
                currentNetworkInfo = jsonInfo
                Timber.v("New network info $jsonInfo")
            } catch (t: Throwable) {
                Timber.w(t, "Error updating the network info")
            }
        }
    }

    private fun getNetworkInfoJsonObject(): JSONObject {
        val info = currentNetworkInfo ?: return JSONObject()

        return JSONObject(info)
    }

    internal data class NetworkInfo(
        val currentNetwork: String,
        val previousNetwork: String? = null,
        val secondsSinceLastSwitch: Long
    )

    internal enum class NetworkType {
        WIFI,
        CELLULAR
    }

    companion object {
        private const val FILENAME = "network.type.collector.file"
        private const val NETWORK_INFO_KEY = "NETWORK_INFO_KEY"
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
