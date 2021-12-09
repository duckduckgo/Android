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

import android.content.Context
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnMemoryCollectorPlugin
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import xyz.hexene.localvpn.TCB
import javax.inject.Inject

@ContributesMultibinding(VpnObjectGraph::class)
class VpnServiceStateCollector @Inject constructor(
    private val context: Context,
    private val memoryCollectorPluginPoint: PluginPoint<VpnMemoryCollectorPlugin>,
    private val vpnUptimeRecorder: VpnUptimeRecorder,
) : VpnStateCollectorPlugin {

    override val collectorName: String
        get() = "vpnServiceState"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        val state = JSONObject()

        // VPN on/off state
        state.put("enabled", TrackerBlockingVpnService.isServiceRunning(context).toString())

        // VPN memory resources state
        val memoryState = JSONObject().apply {
            memoryCollectorPluginPoint.getPlugins().forEach {
                it.collectMemoryMetrics().forEach { entry ->
                    put(entry.key, entry.value)
                }
            }
        }
        state.put("memoryUsage", memoryState)

        // VPN open connections per app
        val tcbs = TCB.copyTCBs()
        val tcpConnections = tcbs
            .filterNot { it.requestingAppPackage.isNullOrBlank() }
            .size
        state.put("activeTcpConnections", tcpConnections)

        // VPN up time
        state.put("upTimeInMillis", vpnUptimeRecorder.getVpnUpTime())

        return state
    }
}
