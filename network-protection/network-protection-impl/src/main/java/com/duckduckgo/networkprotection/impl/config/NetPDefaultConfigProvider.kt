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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.configuration.CONTROLLER_NETP_DUCKDUCKGO_COM
import com.duckduckgo.networkprotection.impl.configuration.VpnLocalDns
import com.duckduckgo.networkprotection.impl.exclusion.NetPExclusionListRepository
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

interface NetPDefaultConfigProvider {
    fun mtu(): Int = 1280

    suspend fun exclusionList(): Set<String> = emptySet()

    fun fallbackDns(): Set<InetAddress> = emptySet()

    suspend fun routes(): Map<String, Int> = emptyMap()

    fun pcapConfig(): PcapConfig? = null
}

data class PcapConfig(
    val filename: String,
    val snapLen: Int,
    val fileSize: Int,
)

@ContributesBinding(VpnScope::class)
class RealNetPDefaultConfigProvider @Inject constructor(
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val systemAppsExclusionRepository: SystemAppsExclusionRepository,
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    private val vpnLocalDns: VpnLocalDns,
) : NetPDefaultConfigProvider {
    override suspend fun exclusionList(): Set<String> {
        return mutableSetOf<String>().apply {
            addAll(netPExclusionListRepository.getExcludedAppPackages())
            addAll(systemAppsExclusionRepository.getAllExcludedSystemApps())
        }.toSet()
    }

    override suspend fun routes(): Map<String, Int> = withContext(dispatcherProvider.io()) {
        val isLocalDnsEnabled = vpnRemoteFeatures.localVpnControllerDns().isEnabled()

        return@withContext if (netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled()) {
            val routes = if (isLocalDnsEnabled) {
                // get the controller IPs
                val controllerIPs = vpnLocalDns.lookup(CONTROLLER_NETP_DUCKDUCKGO_COM)
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .associateWith { 32 }

                val excludedRanges = WgVpnRoutes.vpnDefaultExcludedRoutes + controllerIPs
                logcat { "Generating VPN routes dynamically, excluded ranges: $excludedRanges" }

                WgVpnRoutes().generateVpnRoutes(excludedRanges)
            } else {
                WgVpnRoutes.wgVpnDefaultRoutes
            }
            routes.toMutableMap().apply {
                fallbackDns().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.forEach { ip ->
                    this[ip] = 32
                }
            }
        } else {
            val routes = if (isLocalDnsEnabled) {
                // get the controller IPs
                val controllerIPs = vpnLocalDns.lookup(CONTROLLER_NETP_DUCKDUCKGO_COM)
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .associateWith { 32 }

                val excludedRanges = WgVpnRoutes.vpnExcludedSpecialRoutes + controllerIPs
                logcat { "Generating VPN routes dynamically, excluded ranges: $excludedRanges" }

                WgVpnRoutes().generateVpnRoutes(excludedRanges)
            } else {
                WgVpnRoutes.wgVpnRoutesIncludingLocal
            }

            routes.toMutableMap().apply {
                fallbackDns().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.forEach { ip ->
                    this[ip] = 32
                }
            }
        }
    }

    override fun fallbackDns(): Set<InetAddress> {
        return netpVpnSettingsDataStore.customDns?.run {
            runCatching {
                InetAddress.getAllByName(this).toSet()
            }.getOrDefault(emptySet())
        } ?: emptySet()
    }
}
