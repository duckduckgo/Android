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
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import dagger.multibindings.Multibinds

private class VpnServiceCallbacksPluginPoint(
    private val plugins: Set<@JvmSuppressWildcards VpnServiceCallbacks>
) : PluginPoint<VpnServiceCallbacks> {
    override fun getPlugins(): Collection<VpnServiceCallbacks> {
        // not that it matters but sorting adds predictability here
        return plugins.sortedBy { it.javaClass.simpleName }
    }
}

@Module
@ContributesTo(VpnObjectGraph::class)
abstract class VpnServiceCallbacksProviderModule {
    @Multibinds
    @SingleInstanceIn(VpnObjectGraph::class)
    abstract fun provideVpnServiceCallbacksPlugins(): Set<@JvmSuppressWildcards VpnServiceCallbacks>

    @Module
    @ContributesTo(VpnObjectGraph::class)
    class VpnServiceCallbacksProviderModuleExt {
        @Provides
        @SingleInstanceIn(VpnObjectGraph::class)
        fun bindVpnServiceCallbacksPluginPoint(
            plugins: Set<@JvmSuppressWildcards VpnServiceCallbacks>
        ): PluginPoint<VpnServiceCallbacks> {
            return VpnServiceCallbacksPluginPoint(plugins)
        }
    }
}
