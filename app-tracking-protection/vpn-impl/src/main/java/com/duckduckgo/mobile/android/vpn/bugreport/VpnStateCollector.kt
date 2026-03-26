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

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollector
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

@ContributesBinding(ActivityScope::class)
@SingleInstanceIn(ActivityScope::class)
class RealVpnStateCollector @Inject constructor(
    private val vpnStateCollectors: PluginPoint<VpnStateCollectorPlugin>,
    private val dispatcherProvider: DispatcherProvider,
) : VpnStateCollector {

    override suspend fun collectVpnState(appPackageId: String?): JSONObject {
        return withContext(dispatcherProvider.io()) {
            val vpnState = JSONObject()
            // other VPN metrics
            vpnStateCollectors.getPlugins().forEach {
                logcat { "collectVpnState from ${it.collectorName}" }
                vpnState.put(it.collectorName, it.collectVpnRelatedState(appPackageId))
            }

            return@withContext vpnState
        }
    }
}

@ContributesPluginPoint(
    scope = ActivityScope::class,
    boundType = VpnStateCollectorPlugin::class,
)
@Suppress("unused")
interface VpnStateCollectorPluginPoint
