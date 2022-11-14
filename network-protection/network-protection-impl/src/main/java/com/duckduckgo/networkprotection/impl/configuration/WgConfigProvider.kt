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

package com.duckduckgo.networkprotection.impl

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.configuration.DevicePrivateKey
import com.duckduckgo.networkprotection.impl.configuration.WgServerDataProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import javax.inject.Inject

interface WgConfigProvider {
    fun get(): Config
}
@ContributesBinding(VpnScope::class)
class RealWgConfigProvider @Inject constructor(
    private val devicePrivateKey: DevicePrivateKey,
    wgServerDataProvider: WgServerDataProvider
): WgConfigProvider {
    private val serverData = wgServerDataProvider.get()

    override fun get(): Config  = Config.Builder()
        .setInterface(
            Interface.Builder()
                .parsePrivateKey(devicePrivateKey.get())
                .parseAddresses(serverData.ipAddress)
                .parseDnsServers(serverData.dns)
                .build()
        )
        .addPeer(
            Peer.Builder()
                .parsePublicKey(serverData.publicKey)
                .parseAllowedIPs(serverData.allowedIPs)
                .parseEndpoint(serverData.publicEndpoint)
                .build()
        )
        .build()
}
