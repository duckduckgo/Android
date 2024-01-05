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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.connectionclass.ConnectionQualityStore
import com.duckduckgo.networkprotection.impl.connectionclass.asConnectionQuality
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import org.json.JSONObject

@ContributesMultibinding(ActivityScope::class)
class NetPStateCollector @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val netpRepository: NetworkProtectionRepository,
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val connectionQualityStore: ConnectionQualityStore,
    private val netPSettingsLocalConfig: NetPSettingsLocalConfig,
    private val netPGeoswitchingRepository: NetPGeoswitchingRepository,
) : VpnStateCollectorPlugin {
    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        val isNetpRunning = vpnFeaturesRegistry.isFeatureRunning(NetPVpnFeature.NETP_VPN)
        return JSONObject().apply {
            put("enabled", isNetpRunning)
            if (isNetpRunning) {
                appPackageId?.let {
                    put("reportedAppProtected", !netPExclusionListRepository.getExcludedAppPackages().contains(it))
                }
                put("connectedServer", netpRepository.serverDetails?.location ?: "Unknown")
                put("connectedServerIP", netpRepository.serverDetails?.ipAddress ?: "Unknown")
                put("connectionQuality", connectionQualityStore.getConnectionLatency().asConnectionQuality())
                put("customServerSelection", netPGeoswitchingRepository.getUserPreferredLocation().countryCode != null)
                put("excludeLocalNetworks", netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().isEnabled())
            }
        }
    }

    override val collectorName: String = "netPState"
}
