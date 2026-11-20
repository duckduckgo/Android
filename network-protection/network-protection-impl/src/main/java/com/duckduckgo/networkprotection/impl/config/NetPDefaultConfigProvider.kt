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
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.configuration.NetPNetworkGate
import com.duckduckgo.networkprotection.impl.configuration.VPNExcludedDomainsFallback
import com.duckduckgo.networkprotection.impl.exclusion.NetPExclusionListRepository
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface NetPDefaultConfigProvider {
    fun mtu(): Int = 1280

    suspend fun exclusionList(): Set<String> = emptySet()

    fun fallbackDns(): Set<InetAddress> = emptySet()

    suspend fun routes(): Map<String, Int> = emptyMap()
}

@ContributesBinding(VpnScope::class)
@SingleIn(VpnScope::class)
class RealNetPDefaultConfigProvider @Inject constructor(
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val systemAppsExclusionRepository: SystemAppsExclusionRepository,
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
    private val vpnRemoteFeatures: VpnRemoteFeatures,
    dns: Dns,
    networkGate: NetPNetworkGate,
    private val excludedDomainsFallback: VPNExcludedDomainsFallback,
    private val currentTimeProvider: CurrentTimeProvider,
) : NetPDefaultConfigProvider {
    private val dns = networkGate.gate(dns)

    // An in-place restart prepares with NetP's network calls blocked and takes the slower null-route path when it needs
    // one, so the excluded hosts are reused across restarts. They expire so that a changed host IP, or a hardcoded
    // fallback IP cached after a failed lookup, is resolved again.
    @Volatile
    private var resolvedHosts: ResolvedHosts? = null

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
                val excludedDomainIps = excludedDomainIps()

                val excludedRanges = WgVpnRoutes.vpnDefaultExcludedRoutes + excludedDomainIps
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
                val excludedDomainIps = excludedDomainIps()

                val excludedRanges = WgVpnRoutes.vpnExcludedSpecialRoutes + excludedDomainIps
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

    private fun excludedDomainIps(): Map<String, Int> {
        freshExcludedDomainIps()?.let { return it }

        val resolvedAtMillis = currentTimeProvider.getTimeInMillis()
        return excludedDomainsFallback.getFallback().keys.map {
            dns.lookup(it)
        }.flatten().filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .associateWith { 32 }
            .also { resolvedHosts = ResolvedHosts(it, resolvedAtMillis) }
    }

    private fun freshExcludedDomainIps(): Map<String, Int>? {
        val hosts = resolvedHosts ?: return null
        val ageMillis = currentTimeProvider.getTimeInMillis() - hosts.resolvedAtMillis
        return hosts.ips.takeIf { ageMillis in 0 until RESOLVED_HOSTS_TTL_MILLIS }
    }

    override fun fallbackDns(): Set<InetAddress> {
        return netpVpnSettingsDataStore.customDns?.run {
            runCatching {
                InetAddress.getAllByName(this).toSet()
            }.getOrDefault(emptySet())
        } ?: emptySet()
    }

    private class ResolvedHosts(val ips: Map<String, Int>, val resolvedAtMillis: Long)

    private companion object {
        val RESOLVED_HOSTS_TTL_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}
