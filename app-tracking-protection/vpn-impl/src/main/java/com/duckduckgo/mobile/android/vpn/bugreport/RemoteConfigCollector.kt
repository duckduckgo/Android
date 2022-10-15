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

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.feature.JSONObjectAdapter
import com.duckduckgo.mobile.android.vpn.remote_config.VpnConfigToggle
import com.duckduckgo.mobile.android.vpn.remote_config.VpnRemoteConfigDatabase
import com.duckduckgo.mobile.android.vpn.state.VpnStateCollectorPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.json.JSONObject
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class RemoteConfigCollector @Inject constructor(
    vpnRemoteConfigDatabase: VpnRemoteConfigDatabase,
) : VpnStateCollectorPlugin {

    private val togglesDao = vpnRemoteConfigDatabase.vpnConfigTogglesDao()
    val adapter: JsonAdapter<List<Toggle>> = Moshi
        .Builder()
        .add(JSONObjectAdapter())
        .build()
        .adapter(Types.newParameterizedType(List::class.java, Toggle::class.java))

    override val collectorName: String
        get() = "remoteConfigInfo"

    override suspend fun collectVpnRelatedState(appPackageId: String?): JSONObject {
        return JSONObject().apply {
            togglesDao.getConfigToggles().map { it.toToggle() }.forEach { toggle ->
                put(toggle.name, toggle.value)
            }
        }
    }

    data class Toggle(val name: String, val value: Boolean)
    private fun VpnConfigToggle.toToggle() = Toggle(name, enabled)
}
