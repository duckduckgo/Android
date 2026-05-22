/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.remoteconfig

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

data class AdBlockingExtensionSettings(
    @field:Json(name = "version")
    val version: String,
    @field:Json(name = "scriptlets")
    val scriptlets: Map<String, ScriptletEntry>,
)

data class ScriptletEntry(
    @field:Json(name = "url")
    val url: String,
    @field:Json(name = "signature")
    val signature: String,
)

interface AdBlockingExtensionConfigProvider {
    val scriptletsSettings: StateFlow<AdBlockingExtensionSettings?>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = AdBlockingExtensionConfigProvider::class)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class RealAdBlockingExtensionConfigProvider @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
) : AdBlockingExtensionConfigProvider, PrivacyConfigCallbackPlugin {

    private val settingsAdapter by lazy { buildJsonAdapter() }
    private val scriptletsFlow = MutableStateFlow<AdBlockingExtensionSettings?>(null)

    init {
        refresh()
    }

    override val scriptletsSettings: StateFlow<AdBlockingExtensionSettings?> = scriptletsFlow.asStateFlow()

    override fun onPrivacyConfigDownloaded() {
        logcat { "onPrivacyConfigDownloaded" }
        refresh()
    }

    private fun refresh() {
        scriptletsFlow.value = parseSettings()
    }

    private fun parseSettings(): AdBlockingExtensionSettings? {
        val settingsJson = feature.self().getSettings() ?: return null
        return runCatching { settingsAdapter.fromJson(settingsJson) }
            .onFailure { logcat(WARN) { "failed to parse settings: ${it.asLog()}" } }
            .getOrNull()
            ?.takeIf { it.version.isNotEmpty() }
    }

    private fun buildJsonAdapter(): JsonAdapter<AdBlockingExtensionSettings> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(AdBlockingExtensionSettings::class.java)
    }
}
