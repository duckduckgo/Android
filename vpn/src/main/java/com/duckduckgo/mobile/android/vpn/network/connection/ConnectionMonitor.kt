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
import android.os.HandlerThread
import android.os.Message
import com.duckduckgo.mobile.android.vpn.network.connection.Messages.MSG_ADD_ALL_NETWORKS
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import timber.log.Timber

class ConnectionMonitor @AssistedInject constructor(
    private val connectivityManager: ConnectivityManager?,
    private val networkRequestHandlerFactory: NetworkRequestHandler.Factory,
    @Assisted private val networkConnectionListener: NetworkConnectionListener,
) : NetworkCallback() {

    @AssistedFactory
    interface Factory {
        fun create(networkConnectionListener: NetworkConnectionListener): ConnectionMonitor
    }

    private var handlerThread: HandlerThread? = null
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

        val handler = HandlerThread(NetworkRequestHandler::class.java.simpleName).apply { start() }
        handlerThread = handler
        networkHandler = networkRequestHandlerFactory.create(handler.looper, networkConnectionListener)
    }

    fun onSopMonitoring() {
        Timber.d("onSopMonitoring")
        connectivityManager?.safeUnregisterNetworkCallback(this)
        tearDownNetworkHandler()
    }

    private fun tearDownNetworkHandler() {
        handlerThread?.quitSafely()
        handlerThread = null
        networkHandler?.removeCallbacksAndMessages(null)
        networkHandler = null
    }

    private fun handleNetworkChange() {
        buildMessage(MSG_ADD_ALL_NETWORKS).run {
            networkHandler?.removeMessages(this.what, null)
            networkHandler?.sendMessage(this)
        }
    }

    private fun buildMessage(what: Messages): Message {
        val message = Message.obtain()
        message.what = what.ordinal
        return message
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
