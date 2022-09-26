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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.statistics.IndexRandomizer
import com.duckduckgo.app.statistics.Probabilistic
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import javax.inject.Provider

interface VpnNetworkStackVariantManager {
    fun getVariant(): String
}

internal class VpnNetworkStackVariantManagerImpl constructor(
    private val vpnNetworkStackPluginPoint: Provider<PluginPoint<VpnNetworkStack>>,
    private val vpnNetworkStackVariantStore: VpnNetworkStackVariantStore,
    private val weightedRandomizer: IndexRandomizer,
) : VpnNetworkStackVariantManager {

    override fun getVariant(): String {
        val variants = vpnNetworkStackPluginPoint.get().variants().map { VpnIntegrationVariant(it, 1.0) }
        // return the stored variant
        vpnNetworkStackVariantStore.variant?.let { key ->
            variants.firstOrNull { it.name == key }
        }?.let { return it.name }

        return runCatching {
            variants[weightedRandomizer.random(variants)].also { vpnNetworkStackVariantStore.variant = it.name }
        }.onFailure {
            variants.first().name
        }.getOrDefault(variants.first()).name
    }

    private fun PluginPoint<VpnNetworkStack>.variants(): List<String> {
        val variants = mutableListOf<String>()
        this.getPlugins().forEach {
            variants.add(it.name)
        }
        return variants.toList()
    }
}

private data class VpnIntegrationVariant(val name: String, override val weight: Double) : Probabilistic

@Module
@ContributesTo(VpnScope::class)
object VpnIntegrationVariantManagerModule {
    @Provides
    fun provideVpnIntegrationVariantManager(
        vpnIntegrations: Provider<PluginPoint<VpnNetworkStack>>,
        vpnNetworkStackVariantStore: VpnNetworkStackVariantStore,
        weightedRandomizer: IndexRandomizer
    ): VpnNetworkStackVariantManager {
        return VpnNetworkStackVariantManagerImpl(vpnIntegrations, vpnNetworkStackVariantStore, weightedRandomizer)
    }
}
