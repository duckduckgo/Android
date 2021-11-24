/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.bugreport

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollector
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleIn
import dagger.multibindings.Multibinds
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(VpnObjectGraph::class)
@SingleIn(VpnObjectGraph::class)
class RealVpnStateCollector @Inject constructor(
    private val vpnStateCollectors: PluginPoint<VpnStateCollectorPlugin>,
    private val dispatcherProvider: DispatcherProvider,
) : VpnStateCollector {

    override suspend fun collectVpnState(appPackageId: String?): JSONObject {
        return withContext(dispatcherProvider.io()) {
            val vpnState = JSONObject()
            // other VPN metrics
            vpnStateCollectors.getPlugins().forEach {
                Timber.v("collectVpnState from ${it.collectorName}")
                vpnState.put(it.collectorName, it.collectVpnRelatedState(appPackageId))
            }

            return@withContext vpnState
        }
    }
}

private class VpnStateCollectorPluginPoint(
    private val plugins: Set<@JvmSuppressWildcards VpnStateCollectorPlugin>
) : PluginPoint<VpnStateCollectorPlugin> {
    override fun getPlugins(): Collection<VpnStateCollectorPlugin> {
        return plugins.sortedBy { it.collectorName }
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class VpnStateCollectorProviderModule {

    @Multibinds
    @SingleIn(VpnObjectGraph::class)
    abstract fun bindVpnStateCollectorEmptyPlugins(): Set<@JvmSuppressWildcards VpnStateCollectorPlugin>

    @Module
    @ContributesTo(VpnObjectGraph::class)
    class VpnStateCollectorProviderModuleExt {
        @Provides
        @SingleIn(VpnObjectGraph::class)
        fun bindVpnMemoryCollectorPluginPoint(
            plugins: Set<@JvmSuppressWildcards VpnStateCollectorPlugin>
        ): PluginPoint<VpnStateCollectorPlugin> {
            return VpnStateCollectorPluginPoint(plugins)
        }
    }
}
