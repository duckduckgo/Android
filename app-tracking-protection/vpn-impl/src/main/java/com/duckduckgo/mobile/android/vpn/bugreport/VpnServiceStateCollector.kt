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
import com.duckduckgo.common.utils.extensions.getPrivateDnsServerName
import com.duckduckgo.common.utils.extensions.isPrivateDnsActive
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
class VpnServiceStateCollector @Inject constructor(
    private val context: Context,
) : VpnStateCollectorPlugin {

    override val collectorName: String = "vpnServiceState"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            // VPN on/off state
            put("enabled", TrackerBlockingVpnService.isServiceRunning(context).toString())
            put("privateDns", runCatching { getPrivateDnsServerName() }.getOrDefault(""))
        }
    }

    private fun getPrivateDnsServerName(): String {
        // return "unknown" when private DNS is enabled but we can't get the server name, otherwise ""
        val default = if (context.isPrivateDnsActive()) "unknown" else ""
        return context.getPrivateDnsServerName() ?: default
    }
}
