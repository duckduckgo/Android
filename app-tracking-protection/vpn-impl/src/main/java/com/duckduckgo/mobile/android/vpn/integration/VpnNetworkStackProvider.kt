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

package com.duckduckgo.mobile.android.vpn.integration

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(VpnScope::class)
class VpnNetworkStackProviderImpl @Inject constructor(
    private val vpnNetworkStacks: PluginPoint<VpnNetworkStack>,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : VpnNetworkStackProvider {
    override suspend fun provideNetworkStack(): VpnNetworkStack {
        val features = vpnFeaturesRegistry.getRegisteredFeatures()
        val feature = features.firstOrNull { it.featureName == AppTpVpnFeature.APPTP_VPN.featureName }

        return vpnNetworkStacks.getPlugins().firstOrNull { it.name == feature?.featureName }
            ?: VpnNetworkStack.EmptyVpnNetworkStack
    }
}

@ContributesPluginPoint(
    scope = VpnScope::class,
    boundType = VpnNetworkStack::class,
)
interface VpnNetworkStackPluginPoint
