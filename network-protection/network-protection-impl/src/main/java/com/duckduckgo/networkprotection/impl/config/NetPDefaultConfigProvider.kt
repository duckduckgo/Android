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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.squareup.anvil.annotations.ContributesBinding
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface NetPDefaultConfigProvider {
    fun mtu(): Int = 1280

    fun exclusionList(): Set<String> = emptySet()

    fun fallbackDns(): Set<InetAddress> = emptySet()

    suspend fun routes(): Map<String, Int> = emptyMap()

    fun pcapConfig(): PcapConfig? = null
}

data class PcapConfig(val filename: String, val snapLen: Int, val fileSize: Int)

@ContributesBinding(VpnScope::class)
class RealNetPDefaultConfigProvider @Inject constructor(
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
) : NetPDefaultConfigProvider {
    override fun exclusionList(): Set<String> {
        return netPExclusionListRepository.getExcludedAppPackages().toSet()
    }

    override suspend fun routes(): Map<String, Int> = withContext(dispatcherProvider.io()) {
        val defaultImpl = object : NetPDefaultConfigProvider {}

        return@withContext if (netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled()) {
            WgVpnRoutes.wgVpnDefaultRoutes.toMutableMap().apply {
                fallbackDns().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.forEach { ip ->
                    this[ip] = 32
                }
            }
        } else {
            WgVpnRoutes.wgVpnRoutesIncludingLocal.toMutableMap().apply {
                fallbackDns().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.forEach { ip ->
                    this[ip] = 32
                }
            }
        }
    }
}
