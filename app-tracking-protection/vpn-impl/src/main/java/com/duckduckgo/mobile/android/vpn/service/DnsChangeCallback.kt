/*
 * Copyright (c) 2024 DuckDuckGo
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
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.network.util.getActiveNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.net.InetAddress
import javax.inject.Inject

class DnsChangeCallback @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : NetworkCallback() {

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
    }
    private var lastDns: List<InetAddress>? = null

    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        logcat { "onLinkPropertiesChanged: $linkProperties" }
        coroutineScope.launch(dispatcherProvider.io()) {
            val dns = linkProperties.dnsServers
            val activeNetwork = context.getActiveNetwork()
            // we only care about changes in the active network
            if (activeNetwork != null && activeNetwork != network) return@launch

            if (!same(lastDns, dns)) {
                logcat {
                    """
                    onLinkPropertiesChanged: DNS changed
                      DNS cur=$dns
                      DNS prv=$lastDns
                    """.trimIndent()
                }
                lastDns = dns
                TrackerBlockingVpnService.restartVpnService(context)
            }
        }
    }

    internal fun register() {
        kotlin.runCatching {
            val request = NetworkRequest.Builder().apply {
                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            }.build()
            connectivityManager?.registerNetworkCallback(request, this)
        }.onFailure {
            logcat(ERROR) { it.asLog() }
        }
    }

    internal fun unregister() {
        kotlin.runCatching {
            connectivityManager?.unregisterNetworkCallback(this)
        }.onFailure {
            logcat(ERROR) { it.asLog() }
        }
    }

    private fun same(last: List<InetAddress>?, current: List<InetAddress>?): Boolean {
        if (last == null || current == null) return false
        if (last.size != current.size) return false
        if (current.containsAll(last)) return true
        // for (i in current.indices) if (last[i] != current[i]) return false
        return false
    }
}
