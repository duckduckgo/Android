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

import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(VpnObjectGraph::class)
class VpnAppTrackerListInfoCollector @Inject constructor(
    private val vpnDatabase: VpnDatabase,
) : VpnStateCollectorPlugin {

    override val collectorName: String
        get() = "vpnTrackerLists"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            put(APP_TRACKER_BLOCKLIST, vpnDatabase.vpnAppTrackerBlockingDao().getTrackerBlocklistMetadata()?.eTag.orEmpty())
            put(APP_EXCLUSION_LIST, vpnDatabase.vpnAppTrackerBlockingDao().getExclusionListMetadata()?.eTag.orEmpty())
            put(APP_EXCEPTION_RULE_LIST, vpnDatabase.vpnAppTrackerBlockingDao().getTrackerExceptionRulesMetadata()?.eTag.orEmpty())
        }
    }

    companion object {
        private const val APP_TRACKER_BLOCKLIST = "appTrackerListEtag"
        private const val APP_EXCLUSION_LIST = "appExclusionListEtag"
        private const val APP_EXCEPTION_RULE_LIST = "appExceptionRuleListEtag"
    }
}
