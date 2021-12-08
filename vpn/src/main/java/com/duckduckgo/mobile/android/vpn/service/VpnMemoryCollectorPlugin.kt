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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds

/**
 * Implement this plugin interface if you want the [TrackerBlockingVpnService] to notify you when it's
 * about to send memory metrics to the backend.
 *
 * The [VpnMemoryCollectorPlugin.collectMemoryMetrics] method will be called to give you the chance
 * to collect and return a map with the metrics that are interesting for you.
 */
interface VpnMemoryCollectorPlugin {
    fun collectMemoryMetrics(): Map<String, String>
}

private class VpnMemoryCollectorPluginPoint(
    private val plugins: Set<@JvmSuppressWildcards VpnMemoryCollectorPlugin>
) : PluginPoint<VpnMemoryCollectorPlugin> {
    override fun getPlugins(): Collection<VpnMemoryCollectorPlugin> {
        return plugins
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class VpnMemoryCollectorProviderModule {

    @Multibinds
    @VpnScope
    abstract fun bindVpnMemoryCollectorPlugins(): Set<@JvmSuppressWildcards VpnMemoryCollectorPlugin>

    @Module
    @ContributesTo(VpnObjectGraph::class)
    class VpnMemoryCollectorProviderModuleExt {
        @Provides
        @VpnScope
        fun bindVpnMemoryCollectorPluginPoint(
            plugins: Set<@JvmSuppressWildcards VpnMemoryCollectorPlugin>
        ): PluginPoint<VpnMemoryCollectorPlugin> {
            return VpnMemoryCollectorPluginPoint(plugins)
        }
    }
}
