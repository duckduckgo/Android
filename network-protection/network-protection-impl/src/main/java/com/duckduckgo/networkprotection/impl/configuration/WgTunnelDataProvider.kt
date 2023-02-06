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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider.WgTunnelData
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface WgTunnelDataProvider {
    suspend fun get(): WgTunnelData?
    data class WgTunnelData(
        val userSpaceConfig: String,
        val serverLocation: String?,
        val serverIP: String?,
        val tunnelAddress: Map<InetAddress, Int>,
    )
}

@ContributesBinding(VpnScope::class)
class RealWgTunnelDataProvider @Inject constructor(
    private val deviceKeys: DeviceKeys,
    private val wgServerDataProvider: WgServerDataProvider,
) : WgTunnelDataProvider {

    override suspend fun get(): WgTunnelData? {
        return try {
            // ensure we always return null on error
            val serverData = wgServerDataProvider.get(deviceKeys.publicKey) ?: return null
            Config.Builder()
                .setInterface(
                    Interface.Builder()
                        .parsePrivateKey(deviceKeys.privateKey)
                        .parseAddresses(serverData.address)
                        .build(),
                )
                .addPeer(
                    Peer.Builder()
                        .parsePublicKey(serverData.publicKey)
                        .parseAllowedIPs(serverData.allowedIPs)
                        .parseEndpoint(serverData.publicEndpoint)
                        .build(),
                )
                .build().run {
                    WgTunnelData(
                        userSpaceConfig = this.toWgUserspaceString(),
                        serverLocation = serverData.location,
                        serverIP = kotlin.runCatching { InetAddress.getByName(peers[0].endpoint?.host).hostAddress }.getOrNull(),
                        tunnelAddress = getInterface().addresses.associate { Pair(it.address, it.mask) },
                    )
                }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Error getting WgTunnelData: ${e.asLog()}" }
            null
        }
    }
}
