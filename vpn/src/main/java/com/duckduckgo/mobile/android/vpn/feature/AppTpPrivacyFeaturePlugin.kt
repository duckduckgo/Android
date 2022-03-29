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

package com.duckduckgo.mobile.android.vpn.feature

import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureName
import com.duckduckgo.mobile.android.vpn.feature.settings.SettingName
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AppTpPrivacyFeaturePlugin @Inject constructor(
    private val settings: DaggerSet<AppTpSettingPlugin>,
) : PrivacyFeaturePlugin {

    override fun store(name: FeatureName, jsonString: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = appTpFeatureValueOf(name.value)
        if (name == featureName) {
            val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
            val adapter: JsonAdapter<JsonAppTpFeatureConfig> = moshi.adapter(JsonAppTpFeatureConfig::class.java)

            val config = kotlin.runCatching { adapter.fromJson(jsonString) }.getOrNull() ?: return false

            config.settings.forEach { setting ->
                setting.value?.let { jsonObject ->
                    settings.firstOrNull { setting.key == it.settingName.value }?.let { settingPlugin ->
                        settingPlugin.store(SettingName { setting.key }, jsonObject.toString())
                    }
                }
            }

            return true
        }

        return false
    }

    override val featureName: FeatureName = AppTpFeatureName.AppTrackerProtection
}

interface AppTpSettingPlugin {
    fun store(
        name: SettingName,
        jsonString: String
    ): Boolean

    val settingName: SettingName
}
