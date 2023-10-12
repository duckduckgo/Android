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

import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel.WgTunnelData
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface WgTunnel {
    suspend fun establish(): WgTunnelData?
    data class WgTunnelData(
        val serverName: String,
        val userSpaceConfig: String,
        val serverLocation: String?,
        val serverIP: String?,
        val gateway: String,
        val tunnelAddress: Map<InetAddress, Int>,
    )
}

fun Map<InetAddress, Int>.toCidrString(): Set<String> {
    return this.map { "${it.key.hostAddress}/${it.value}" }.toSet()
}

@ContributesBinding(VpnScope::class)
class RealWgTunnel @Inject constructor(
    private val deviceKeys: DeviceKeys,
    private val wgServerApi: WgServerApi,
    private val crashLogger: CrashLogger,
) : WgTunnel {

    override suspend fun establish(): WgTunnelData? {
        return try {
            // ensure we always return null on error
            val serverData = wgServerApi.registerPublicKey(deviceKeys.publicKey) ?: return null
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
                        serverName = serverData.serverName,
                        userSpaceConfig = this.toWgUserspaceString(),
                        serverLocation = serverData.location,
                        serverIP = kotlin.runCatching { InetAddress.getByName(peers[0].endpoint?.host).hostAddress }.getOrNull(),
                        gateway = serverData.gateway,
                        tunnelAddress = getInterface().addresses.associate { Pair(it.address, it.mask) },
                    )
                }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Error getting WgTunnelData: ${e.asLog()}" }
            crashLogger.logCrash(CrashLogger.Crash(shortName = "m_netp_ev_key_registration_error", t = e))
            null
        }
    }
}
