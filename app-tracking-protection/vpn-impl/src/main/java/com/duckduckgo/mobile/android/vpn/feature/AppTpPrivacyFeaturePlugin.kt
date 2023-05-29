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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class AppTpPrivacyFeaturePlugin @Inject constructor(
    plugins: DaggerSet<AppTpSettingPlugin>,
    private val context: Context,
) : PrivacyFeaturePlugin {

    private val settings = plugins.sortedBy { it.settingName.value }

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override fun store(featureName: String, jsonString: String): Boolean {
        @Suppress("NAME_SHADOWING")
        val appTpFeature = appTpFeatureValueOf(featureName) ?: return false
        if (appTpFeature.value == this.featureName) {
            val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
            val adapter: JsonAdapter<JsonAppTpFeatureConfig> = moshi.adapter(JsonAppTpFeatureConfig::class.java)

            val config = kotlin.runCatching { adapter.fromJson(jsonString) }.getOrNull() ?: return false
            val currentHash = preferences.getSignature() ?: ""
            if (currentHash == config.hash) {
                logcat { "Downloaded appTP feature config has same hash, noop" }
                return true
            }

            config.hash?.let { preferences.setSignature(it) }
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

    private fun SharedPreferences.getSignature(): String? {
        return getString(APPTP_FEATURE_SIGNATURE_KEY, null)
    }

    private fun SharedPreferences.setSignature(value: String) {
        edit {
            putString(APPTP_FEATURE_SIGNATURE_KEY, value)
        }
    }

    override val featureName: String = AppTpFeatureName.AppTrackerProtection.value

    companion object {
        private const val APPTP_FEATURE_SIGNATURE_KEY = "apptp_feature_signature"
        private const val FILENAME = "com.duckduckgo.mobile.vpn.feature.plugin"
    }
}

interface AppTpSettingPlugin {
    fun store(
        name: SettingName,
        jsonString: String,
    ): Boolean

    val settingName: SettingName
}
