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

package com.duckduckgo.networkprotection.impl.config

import com.duckduckgo.di.scopes.VpnScope
import com.squareup.anvil.annotations.ContributesBinding
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

interface NetPConfigProvider {
    fun mtu(): Int = 1280

    fun exclusionList(): Set<String> = setOf("com.google.android.gms")

    fun dns(): Set<InetAddress> = InetAddress.getAllByName("10.11.12.1").toSet()

    fun routes(): Map<String, Int> {
        // ensure the set DNS are in the routes
        // We're only setting routes for IPv4 atm
        return WgVpnRoutes.wgVpnRoutes.toMutableMap().apply {
            dns().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.forEach { ip ->
                this[ip] = 32
            }
        }
    }

    fun pcapConfig(): PcapConfig? = null
}

data class PcapConfig(val filename: String, val snapLen: Int, val fileSize: Int)

@ContributesBinding(VpnScope::class)
class RealNetPConfigProvider @Inject constructor() : NetPConfigProvider
