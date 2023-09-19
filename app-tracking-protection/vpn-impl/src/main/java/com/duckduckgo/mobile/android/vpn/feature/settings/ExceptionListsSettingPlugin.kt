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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRule
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExcludedPackage
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerSystemAppOverridePackage
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AppTpSettingPlugin::class,
)
class ExceptionListsSettingPlugin @Inject constructor(
    private val vpnDatabase: VpnDatabase,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
) : AppTpSettingPlugin {
    private val jsonAdapter = Moshi.Builder().build().adapter(JsonConfigModel::class.java)

    override fun store(name: SettingName, jsonString: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = appTpSettingValueOf(name.value)
        if (name == settingName) {
            logcat { "Received configuration: $jsonString" }
            runCatching {
                jsonAdapter.fromJson(jsonString)?.let { exceptionLists ->

                    val appTrackerExceptionRuleList = exceptionLists.appTrackerAllowList.map { appTrackerAllowRule ->
                        AppTrackerExceptionRule(
                            appTrackerAllowRule.domain,
                            appTrackerAllowRule.packageNames.map { it.packageName },
                        )
                    }

                    vpnDatabase.vpnAppTrackerBlockingDao().updateTrackerExceptionRules(appTrackerExceptionRuleList)
                    vpnDatabase.vpnAppTrackerBlockingDao().updateExclusionList(exceptionLists.unprotectedApps)
                    vpnDatabase.vpnSystemAppsOverridesDao().upsertSystemAppOverrides(
                        exceptionLists.unhideSystemApps.map { AppTrackerSystemAppOverridePackage(it) },
                    )

                    // Restart VPN now that the lists were updated
                    appCoroutineScope.launch {
                        vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
                    }
                }
            }.onFailure {
                logcat(LogPriority.WARN) { it.asLog() }
            }
            return true
        }

        return false
    }

    override val settingName: SettingName = AppTpSetting.ExceptionLists

    private data class JsonConfigModel(
        val appTrackerAllowList: List<AppTrackerAllowRuleModel>,
        val unprotectedApps: List<AppTrackerExcludedPackage>,
        val unhideSystemApps: List<String>,
    )

    private data class AppTrackerAllowRuleModel(
        val domain: String,
        val packageNames: List<AllowedPackageModel>,
    )

    private data class AllowedPackageModel(
        val packageName: String,
    )
}
