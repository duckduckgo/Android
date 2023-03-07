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

package com.duckduckgo.networkprotection.store

import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ClientInterface
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails

interface NetworkProtectionRepository {
    var reconnectStatus: ReconnectStatus
    var reconnectAttemptCount: Int
    var privateKey: String?
    var lastPrivateKeyUpdateTimeInMillis: Long
    var enabledTimeInMillis: Long
    var serverDetails: ServerDetails?
    var clientInterface: ClientInterface?

    enum class ReconnectStatus {
        NotReconnecting,
        Reconnecting,
        ReconnectingFailed,
    }

    data class ServerDetails(
        val ipAddress: String?,
        val location: String?,
    )

    data class ClientInterface(
        val tunnelCidrSet: Set<String>,
    )
}

class RealNetworkProtectionRepository constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetworkProtectionRepository {

    override var privateKey: String?
        get() = networkProtectionPrefs.getString(KEY_WG_PRIVATE_KEY, null)
        set(value) {
            networkProtectionPrefs.putString(KEY_WG_PRIVATE_KEY, value)
        }

    override var lastPrivateKeyUpdateTimeInMillis: Long
        get() = networkProtectionPrefs.getLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, -1L)
        set(value) {
            networkProtectionPrefs.putLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, value)
        }

    override var enabledTimeInMillis: Long
        get() = networkProtectionPrefs.getLong(KEY_WG_SERVER_ENABLE_TIME, -1)
        set(value) {
            networkProtectionPrefs.putLong(KEY_WG_SERVER_ENABLE_TIME, value)
        }

    override var serverDetails: ServerDetails?
        get() {
            val ip = networkProtectionPrefs.getString(KEY_WG_SERVER_IP, null)
            val location = networkProtectionPrefs.getString(KEY_WG_SERVER_LOCATION, null)

            return if (ip.isNullOrBlank() && location.isNullOrBlank()) {
                null
            } else {
                ServerDetails(
                    ipAddress = ip,
                    location = location,
                )
            }
        }
        set(value) {
            if (value == null) {
                networkProtectionPrefs.putString(KEY_WG_SERVER_IP, null)
                networkProtectionPrefs.putString(KEY_WG_SERVER_LOCATION, null)
            } else {
                networkProtectionPrefs.putString(KEY_WG_SERVER_IP, value.ipAddress)
                networkProtectionPrefs.putString(KEY_WG_SERVER_LOCATION, value.location)
            }
        }

    override var clientInterface: ClientInterface?
        get() {
            val tunnelIp = networkProtectionPrefs.getStringSet(KEY_WG_CLIENT_IFACE_TUNNEL_IP)

            return ClientInterface(tunnelIp)
        }
        set(value) {
            networkProtectionPrefs.setStringSet(KEY_WG_CLIENT_IFACE_TUNNEL_IP, value?.tunnelCidrSet ?: emptySet())
        }

    override var reconnectStatus: ReconnectStatus
        get() = when (networkProtectionPrefs.getInt(KEY_WG_RECONNECT_STATUS, 0)) {
            -1 -> ReconnectingFailed
            1 -> Reconnecting
            else -> NotReconnecting
        }
        set(value) {
            when (value) {
                Reconnecting -> 1
                ReconnectingFailed -> -1
                NotReconnecting -> 0
            }.also {
                networkProtectionPrefs.putInt(KEY_WG_RECONNECT_STATUS, it)
            }
        }

    override var reconnectAttemptCount: Int
        get() = networkProtectionPrefs.getInt(KEY_WG_RECONNECT_COUNT, 0)
        set(value) {
            networkProtectionPrefs.putInt(KEY_WG_RECONNECT_COUNT, value)
        }

    companion object {
        private const val KEY_WG_PRIVATE_KEY = "wg_private_key"
        private const val KEY_WG_PRIVATE_KEY_LAST_UPDATE = "wg_private_key_last_update"
        private const val KEY_WG_SERVER_IP = "wg_server_ip"
        private const val KEY_WG_SERVER_LOCATION = "wg_server_location"
        private const val KEY_WG_SERVER_ENABLE_TIME = "wg_server_enable_time"
        private const val KEY_WG_RECONNECT_STATUS = "wg_reconnect_status"
        private const val KEY_WG_RECONNECT_COUNT = "wg_reconnect_count"
        private const val KEY_WG_CLIENT_IFACE_TUNNEL_IP = "wg_client_iface_tunnel_ip"
    }
}
