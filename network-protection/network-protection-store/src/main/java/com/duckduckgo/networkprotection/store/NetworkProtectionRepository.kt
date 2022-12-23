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

import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails

interface NetworkProtectionRepository {
    var privateKey: String?
    var enabledTimeInMillis: Long
    var serverDetails: ServerDetails?

    data class ServerDetails(
        val ipAddress: String?,
        val location: String?,
    )
}

class RealNetworkProtectionRepository constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetworkProtectionRepository {

    override var privateKey: String?
        get() = networkProtectionPrefs.getString(KEY_WG_PRIVATE_KEY, null)
        set(value) {
            value?.let {
                networkProtectionPrefs.putString(KEY_WG_PRIVATE_KEY, value)
            }
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

            return if (ip.isNullOrEmpty() && location.isNullOrEmpty()) {
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

    companion object {
        private const val KEY_WG_PRIVATE_KEY = "wg_private_key"
        private const val KEY_WG_SERVER_IP = "wg_server_ip"
        private const val KEY_WG_SERVER_LOCATION = "wg_server_location"
        private const val KEY_WG_SERVER_ENABLE_TIME = "wg_server_enable_time"
    }
}
