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
import com.duckduckgo.networkprotection.impl.configuration.WgServerDataProvider.WgServerData
import com.squareup.anvil.annotations.ContributesBinding
import com.wireguard.config.InetAddresses
import com.wireguard.config.InetNetwork
import timber.log.Timber
import java.net.Inet4Address
import javax.inject.Inject

interface WgServerDataProvider {
    data class WgServerData(
        val ipAddress: String,
        val dns: String,
        val publicKey: String,
        val publicEndpoint: String,
        val allowedIPs: String = "0.0.0.0/0,::0/0,2000::",
    )

    fun get(): WgServerData
}

@ContributesBinding(VpnScope::class)
class MullvadWgServerDataProvider @Inject constructor() : WgServerDataProvider {
    override fun get(): WgServerData = WgServerData(
        ipAddress = "10.66.171.196/32,fc00:bbbb:bbbb:bb01::3:abc3/128",
        dns = "10.64.0.1",
        publicKey = "4nOXEaCDYBV//nsVXk7MrnHpxLV9MbGjt+IGQY//p3k=",
        publicEndpoint = "185.65.135.71:51820"
    )
}
