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
import com.duckduckgo.mobile.android.vpn.model.HealthTriggerEntity
import com.duckduckgo.mobile.android.vpn.store.AppHealthTriggersRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import logcat.logcat
import org.json.JSONObject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AppTpSettingPlugin::class,
)
class BadHealthMitigationSettingPlugin @Inject constructor(
    private val appTpFeatureConfig: AppTpFeatureConfig,
    private val healthTriggersRepository: AppHealthTriggersRepository,
) : AppTpSettingPlugin {
    private val jsonAdapter = Moshi.Builder().add(JSONObjectAdapter()).build().adapter(JsonConfigModel::class.java)

    override fun store(name: SettingName, jsonString: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val name = appTpSettingValueOf(name.value)
        if (name == settingName) {
            logcat { "Received configuration: $jsonString" }
            jsonAdapter.fromJson(jsonString)?.let { config ->
                appTpFeatureConfig.edit { setEnabled(settingName, config.state == "enabled") }
                config.settings?.let { handleSettings(it) }
            }
            return true
        }

        return false
    }

    override val settingName: SettingName = AppTpSetting.BadHealthMitigation

    private fun handleSettings(settings: BadHealthMitigationFeatureSettings) {
        val healthTriggers = mutableSetOf<HealthTrigger>()
        val jsonAdapter = Moshi.Builder().build().adapter(JsonHealthTrigger::class.java)
        settings.triggers?.let { triggers ->
            triggers.forEach { (trigger, jsonObject) ->
                jsonAdapter.fromJson(jsonObject.toString())?.let { config ->
                    healthTriggers.add(config.toHealthTrigger(trigger))
                }
            }
        }
        healthTriggersRepository.insertAll(healthTriggers.map { it.toEntity() })
    }

    private data class JsonConfigModel(
        val state: String,
        val settings: BadHealthMitigationFeatureSettings?,
    )

    private data class BadHealthMitigationFeatureSettings(
        val triggers: Map<String, JSONObject?>?,
    )

    private data class JsonHealthTrigger(
        val state: String,
        val threshold: Int?,
    ) {
        fun toHealthTrigger(name: String): HealthTrigger {
            return HealthTrigger(name, state, threshold)
        }
    }

    private data class HealthTrigger(
        val name: String,
        val state: String,
        val threshold: Int?,
    ) {
        fun toEntity(): HealthTriggerEntity {
            return HealthTriggerEntity(name, state.lowercase() == "enabled", threshold)
        }
    }
}
