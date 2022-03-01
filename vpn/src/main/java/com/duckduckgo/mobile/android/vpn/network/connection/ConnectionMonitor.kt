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

import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors

class ConnectionMonitor @AssistedInject constructor(
    private val connectivityManager: ConnectivityManager?,
    private val networkRequestHandlerFactory: NetworkRequestHandler.Factory,
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val networkConnectionListener: NetworkConnectionListener,
) : NetworkCallback() {

    @AssistedFactory
    interface Factory {
        fun create(coroutineScope: CoroutineScope, networkConnectionListener: NetworkConnectionListener): ConnectionMonitor
    }

    // single thread dispatcher because we want all operations to be sync'ed and in-order
    private val handlerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var networkHandler: NetworkRequestHandler? = null

    private val networkRequest: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    override fun onAvailable(network: Network) {
        Timber.d("onAvailable $network")
        handleNetworkChange()
    }

    override fun onLost(network: Network) {
        Timber.d("onLost $network")
        handleNetworkChange()
    }

    fun onStartMonitoring() {
        Timber.d("onStartMonitoring")
        connectivityManager?.registerNetworkCallback(networkRequest, this)
        // just in case it is called twice without calling onStopMonitoring()
        tearDownNetworkHandler()

        networkHandler = networkRequestHandlerFactory.create(networkConnectionListener)
    }

    fun onSopMonitoring() {
        Timber.d("onSopMonitoring")
        connectivityManager?.safeUnregisterNetworkCallback(this)
        tearDownNetworkHandler()
    }

    private fun tearDownNetworkHandler() {
        networkHandler = null
    }

    private fun handleNetworkChange() {
        coroutineScope.launch(handlerDispatcher) { networkHandler?.updateAllNetworks() }
    }

    private fun ConnectivityManager.safeUnregisterNetworkCallback(networkCallback: NetworkCallback) {
        kotlin.runCatching {
            unregisterNetworkCallback(networkCallback)
        }.onFailure {
            Timber.e(it, "Error unregistering the network callback")
        }
    }

}

interface NetworkConnectionListener {
    fun onNetworkDisconnected()
    fun onNetworkConnected(networks: LinkedHashSet<Network>)
}
