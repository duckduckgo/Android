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

package com.duckduckgo.mobile.android.vpn.bugreport

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
class AppTPStateCollector @Inject constructor(
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : VpnStateCollectorPlugin {
    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            put("enabled", vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN))
        }
    }

    override val collectorName: String = "appTpState"
}
