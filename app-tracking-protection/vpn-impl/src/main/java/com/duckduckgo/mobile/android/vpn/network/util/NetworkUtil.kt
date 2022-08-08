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

package com.duckduckgo.mobile.android.vpn.network.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService

fun Context.getSystemActiveNetworkDefaultDns(): List<String> {
    val dnsList = mutableListOf<String>()

    val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    connectivityManager.activeNetwork?.let { activeNetwork ->
        connectivityManager.getLinkProperties(activeNetwork)?.let { linkProperties ->
            linkProperties.dnsServers.forEach { dns ->
                dns.hostAddress?.let {
                    dnsList.add(it)
                }
            }
        }
    }

    return dnsList.toList()
}

fun Context.getActiveNetwork(): Network? {
    return (getSystemService(VpnService.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork

}
