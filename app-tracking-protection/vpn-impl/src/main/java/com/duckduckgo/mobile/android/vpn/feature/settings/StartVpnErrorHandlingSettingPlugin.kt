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
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.AppTpSettingPlugin
import com.duckduckgo.mobile.android.vpn.feature.SettingName
import com.duckduckgo.mobile.android.vpn.feature.edit
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import logcat.LogPriority
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AppTpSettingPlugin::class,
)
class StartVpnErrorHandlingSettingPlugin @Inject constructor(
    private val appTpFeatureConfig: AppTpFeatureConfig,
) : AppTpSettingPlugin {
    private val jsonAdapter = Moshi.Builder().build().adapter(JsonConfigModel::class.java)

    override fun store(name: SettingName, jsonString: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = appTpSettingValueOf(name.value)
        if (name == settingName) {
            logcat { "Received configuration: $jsonString" }
            runCatching {
                jsonAdapter.fromJson(jsonString)?.state?.let { state ->
                    appTpFeatureConfig.edit { setEnabled(settingName, state == "enabled") }
                }
            }.onFailure {
                logcat(LogPriority.WARN) { "Invalid JSON remote configuration for $settingName" }
            }
            return true
        }

        return false
    }

    override val settingName: SettingName = AppTpSetting.StartVpnErrorHandling

    private data class JsonConfigModel(val state: String?)
}
