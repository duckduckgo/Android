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

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build.VERSION_CODES
import androidx.annotation.WorkerThread
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import timber.log.Timber

class NetworkRequestHandler @AssistedInject constructor(
    private val connectivityManager: ConnectivityManager?,
    private val appBuildConfig: AppBuildConfig,
    @Assisted private val networkConnectionListener: NetworkConnectionListener,
) {

    @AssistedFactory
    interface Factory {
        fun create(networkConnectionListener: NetworkConnectionListener): NetworkRequestHandler
    }

    var currentNetworks: LinkedHashSet<Network> = linkedSetOf()

    @SuppressLint("NewApi")
    @WorkerThread
    internal fun updateAllNetworks() {
        val newActiveNetwork = if (appBuildConfig.sdkInt >= VERSION_CODES.M) {
            connectivityManager?.activeNetwork
        } else {
            null
        }
        val newNetworks = createNetworksSet(newActiveNetwork)
        val didNetworksChange = !currentNetworks.toTypedArray().contentEquals(newNetworks.toTypedArray())
        currentNetworks = newNetworks

        Timber.d("networks: $newNetworks")

        if (didNetworksChange && newNetworks.isNotEmpty()) {
            networkConnectionListener.onNetworkConnected(newNetworks)
        } else if (newNetworks.isEmpty()) {
            networkConnectionListener.onNetworkDisconnected()
        } else {
            Timber.d("Networks didn't chance...noop")
        }
    }

    private fun createNetworksSet(newActiveNetwork: Network?): LinkedHashSet<Network> {
        return linkedSetOf<Network>().apply {
            // first element of the list must be the active network
            newActiveNetwork?.let { network ->
                if (network.hasInternet() && !network.isVPN()) {
                    add(network)
                }
            }

            connectivityManager?.allNetworks?.forEach { network ->
                if (network.hasInternet() && !network.isVPN()) {
                    add(network)
                }
            }
        }
    }

    private fun Network.hasInternet(): Boolean {
        return connectivityManager?.getNetworkCapabilities(this)?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) ?: false
    }

    private fun Network.isVPN(): Boolean {
        return connectivityManager?.getNetworkCapabilities(this)?.hasTransport(
            NetworkCapabilities.TRANSPORT_VPN
        ) ?: false
    }
}
