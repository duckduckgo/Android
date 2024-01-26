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
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair
import java.net.InetAddress
import javax.inject.Inject
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

interface WgTunnel {
    suspend fun establish(keyPair: KeyPair? = null): Result<Config>
}

@ContributesBinding(VpnScope::class)
class RealWgTunnel @Inject constructor(
    private val wgServerApi: WgServerApi,
    private val netPDefaultConfigProvider: NetPDefaultConfigProvider,
) : WgTunnel {

    override suspend fun establish(keyPair: KeyPair?): Result<Config> {
        return try {
            @Suppress("NAME_SHADOWING")
            val keyPair = keyPair ?: KeyPair()
            val publicKey = keyPair.publicKey.toBase64()
            val privateKey = keyPair.privateKey.toBase64()

            // ensure we always return null on error
            val serverData = wgServerApi.registerPublicKey(publicKey) ?: return Result.failure(NullPointerException("serverData = null"))
            val config = Config.Builder()
                .setInterface(
                    Interface.Builder()
                        .parsePrivateKey(privateKey)
                        .parseAddresses(serverData.address)
                        .apply {
                            addDnsServer(InetAddress.getByName(serverData.gateway))
                            addDnsServers(netPDefaultConfigProvider.fallbackDns())
                        }
                        .excludeApplications(netPDefaultConfigProvider.exclusionList())
                        .setMtu(netPDefaultConfigProvider.mtu())
                        .build(),
                )
                .addPeer(
                    Peer.Builder()
                        .parsePublicKey(serverData.publicKey)
                        .parseAllowedIPs(serverData.allowedIPs)
                        .parseEndpoint(serverData.publicEndpoint)
                        .setName(serverData.serverName)
                        .setLocation(serverData.location.orEmpty())
                        .addAllowedIps(
                            netPDefaultConfigProvider.routes().map {
                                InetNetwork.parse("${it.key}/${it.value}")
                            },
                        )
                        .apply {
                            // no allowIPs, add the internet
                            if (allowedIps.isEmpty()) {
                                addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
                            }
                        }
                        .build(),
                )
                .build()
            Result.success(config)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR) { "Error getting WgTunnelData: ${e.asLog()}" }
            return Result.failure(e)
        }
    }
}
