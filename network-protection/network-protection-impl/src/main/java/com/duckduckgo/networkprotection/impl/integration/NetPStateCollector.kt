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

package com.duckduckgo.networkprotection.impl.integration

import com.duckduckgo.common.utils.network.isCGNATed
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.asServerDetails
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQualityStore
import com.duckduckgo.networkprotection.impl.connectionclass.asConnectionQuality
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.isActive
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import org.json.JSONObject

@ContributesMultibinding(ActivityScope::class)
class NetPStateCollector @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val wgTunnelConfig: WgTunnelConfig,
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val connectionQualityStore: ConnectionQualityStore,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
    private val netpSubscriptionManager: NetpSubscriptionManager,
    private val systemAppsExclusionRepository: SystemAppsExclusionRepository,
    private val netpVpnSettingsDataStore: NetpVpnSettingsDataStore,
) : VpnStateCollectorPlugin {

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        val isNetpRunning = vpnFeaturesRegistry.isFeatureRunning(NetPVpnFeature.NETP_VPN)
        return JSONObject().apply {
            put("enabled", isNetpRunning)
            put("CGNATed", isCGNATed())
            put("subscriptionActive", netpSubscriptionManager.getVpnStatus().isActive())
            if (isNetpRunning) {
                appPackageId?.let {
                    put("reportedAppProtected", !netPExclusionListRepository.getExcludedAppPackages().contains(it))
                }
                val serverDetails = wgTunnelConfig.getWgConfig()?.asServerDetails()
                put("connectedServer", serverDetails?.location ?: "Unknown")
                put("connectedServerIP", serverDetails?.ipAddress ?: "Unknown")
                put("connectionQuality", connectionQualityStore.getConnectionLatency().asConnectionQuality())
                put("customServerSelection", netPGeoswitchingRepository.getUserPreferredLocation().countryCode != null)
                put("excludeLocalNetworks", netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled())
                put("excludedSystemAppCategories", systemAppsExclusionRepository.getExcludedCategories().map { it.name })
                put("pauseDuringWifiCallsEnabled", netPSettingsLocalConfig.vpnPauseDuringCalls().isEnabled())
                put("customDNS", !netpVpnSettingsDataStore.customDns.isNullOrEmpty())
            }
        }
    }

    override val collectorName: String = "netPState"

    private fun isCGNATed(): Boolean {
        NetworkInterface.getNetworkInterfaces().asSequence().filter { !it.name.contains("tun") }.map { it.interfaceAddresses }.forEach { addrs ->
            addrs.filter { it.address is Inet4Address && !it.address.isLoopbackAddress }.forEach { interfaceAddress ->
                if (interfaceAddress.address.isCGNATed()) {
                    return true
                }
            }
        }

        return false
    }
}
