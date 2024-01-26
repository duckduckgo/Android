/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.store

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ClientInterface
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ServerDetails
import com.duckduckgo.networkprotection.store.NetworkProtectionPrefs
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import javax.inject.Inject

interface NetworkProtectionRepository {
    var wireguardConfig: Config?
    val privateKey: String?
    val lastPrivateKeyUpdateTimeInMillis: Long
    var enabledTimeInMillis: Long
    val serverDetails: ServerDetails?
    val clientInterface: ClientInterface?
    var vpnAccessRevoked: Boolean

    data class ServerDetails(
        val serverName: String?,
        val ipAddress: String?,
        val location: String?,
    )

    data class ClientInterface(
        val tunnelCidrSet: Set<String>,
    )
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = NetworkProtectionRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = NetPFeatureRemover.NetPStoreRemovalPlugin::class,
)
class RealNetworkProtectionRepository @Inject constructor(
    private val networkProtectionPrefs: NetworkProtectionPrefs,
) : NetworkProtectionRepository, NetPFeatureRemover.NetPStoreRemovalPlugin {

    override var wireguardConfig: Config?
        get() {
            val wgQuickString = networkProtectionPrefs.getString(KEY_WG_CONFIG, null)
            return wgQuickString?.let {
                Config.parse(BufferedReader(StringReader(it)))
            }
        }
        set(value) {
            val oldConfig = networkProtectionPrefs.getString(KEY_WG_CONFIG, null)?.let {
                Config.parse(BufferedReader(StringReader(it)))
            }
            // nothing to update
            if (oldConfig == value) return

            networkProtectionPrefs.putString(KEY_WG_CONFIG, value?.toWgQuickString())
            if (value == null) {
                networkProtectionPrefs.putLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, -1L)
            } else {
                networkProtectionPrefs.putLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, System.currentTimeMillis())
            }
        }

    override val privateKey: String?
        get() {
            return wireguardConfig?.`interface`?.keyPair?.privateKey?.toBase64()
        }

    override val lastPrivateKeyUpdateTimeInMillis: Long
        get() = networkProtectionPrefs.getLong(KEY_WG_PRIVATE_KEY_LAST_UPDATE, -1L)

    override var enabledTimeInMillis: Long
        get() = networkProtectionPrefs.getLong(KEY_WG_SERVER_ENABLE_TIME, -1)
        set(value) {
            networkProtectionPrefs.putLong(KEY_WG_SERVER_ENABLE_TIME, value)
        }

    override val serverDetails: ServerDetails?
        get() {
            return wireguardConfig?.let {
                ServerDetails(
                    serverName = it.peers[0].name,
                    ipAddress = it.peers[0].endpoint?.getResolved()?.host,
                    location = it.peers[0].location,
                )
            }
        }

    override val clientInterface: ClientInterface?
        get() {
            return wireguardConfig?.let {
                ClientInterface(it.`interface`.addresses.map { it.toString() }.toSet())
            }
        }

    override fun clearStore() {
        networkProtectionPrefs.clear()
    }

    override var vpnAccessRevoked: Boolean
        get() = networkProtectionPrefs.getBoolean(KEY_VPN_ACCESS_REVOKED, false)
        set(value) {
            networkProtectionPrefs.putBoolean(KEY_VPN_ACCESS_REVOKED, value)
        }

    companion object {
        private const val KEY_WG_CONFIG = "wg_config_key"
        private const val KEY_WG_PRIVATE_KEY_LAST_UPDATE = "wg_private_key_last_update"
        private const val KEY_WG_SERVER_ENABLE_TIME = "wg_server_enable_time"
        private const val KEY_VPN_ACCESS_REVOKED = "key_vpn_access_revoked"
    }
}
