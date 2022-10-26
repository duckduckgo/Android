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

package com.duckduckgo.mobile.android.vpn.feature.settings

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExcludedPackage
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExclusionListMetadata
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverridePackage
import com.duckduckgo.mobile.android.vpn.trackers.JsonAppTrackerExclusionList
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AppTpSettingPlugin::class
)
class ExceptionListsSettingPlugin @Inject constructor(
    val vpnDatabase: VpnDatabase
) : AppTpSettingPlugin {
    private val jsonAdapter = Moshi.Builder().build().adapter(JsonConfigModel::class.java)

    override fun store(name: SettingName, jsonString: String): Boolean {
        Timber.d("store called with ${name.value}")

        @Suppress("NAME_SHADOWING")
        val name = appTpSettingValueOf(name.value)
        if (name == settingName) {
            Timber.d("Received configuration: $jsonString")
            runCatching {
                jsonAdapter.fromJson(jsonString)?.let { exceptionLists ->

                    Timber.d("Updating the app tracker exclusion list")
                    vpnDatabase.vpnAppTrackerBlockingDao().updateExclusionList(exceptionLists.unprotectedApps)

                    for (item in exceptionLists.unprotectedApps) {
                        Timber.v("${item.packageId}, ${item.reason}")
                    }
                }
            }.onFailure {
                Timber.w(it, "Invalid JSON remote configuration for $settingName")
            }
            return true
        }

        return false
    }

    override val settingName: SettingName = AppTpSetting.ExceptionLists

    private data class JsonConfigModel(
        //val appTrackerAllowList: List<JSONObject>, // TODO: can't have JSONObject here. Injection breaks. Not correct anyway
        val unprotectedApps: List<AppTrackerExcludedPackage>,
        val unhideSystemApps: List<String>,
    )
}
