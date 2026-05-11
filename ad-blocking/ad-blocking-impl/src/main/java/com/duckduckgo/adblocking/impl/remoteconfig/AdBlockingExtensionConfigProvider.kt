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

import com.duckduckgo.app.browser.Domain
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

data class AdBlockingExtensionSettings(
    val version: String,
    val scriptlets: Map<String, ScriptletEntry>,
    val domains: List<Domain> = emptyList(),
)

data class ScriptletEntry(
    val url: String,
    val signature: String,
)

interface AdBlockingExtensionConfigProvider {
    val settings: StateFlow<AdBlockingExtensionSettings?>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = AdBlockingExtensionConfigProvider::class)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class RealAdBlockingExtensionConfigProvider @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val settingsAdapter: JsonAdapter<AdBlockingExtensionSettings>,
) : AdBlockingExtensionConfigProvider, PrivacyConfigCallbackPlugin {

    private val settingsFlow = MutableStateFlow<AdBlockingExtensionSettings?>(null)

    init {
        settingsFlow.value = computeSettings()
    }

    override val settings: StateFlow<AdBlockingExtensionSettings?> = settingsFlow.asStateFlow()

    override fun onPrivacyConfigDownloaded() {
        settingsFlow.value = computeSettings()
    }

    private fun computeSettings(): AdBlockingExtensionSettings? {
        val settingsJson = feature.self().getSettings() ?: return null
        return runCatching { settingsAdapter.fromJson(settingsJson) }
            .onFailure { logcat(WARN) { "AdBlockingExtensionConfigProvider: failed to parse settings: ${it.asLog()}" } }
            .getOrNull()
            ?.takeIf { it.version.isNotEmpty() }
    }
}

internal class DomainJsonAdapter : JsonAdapter<Domain>() {
    override fun fromJson(reader: JsonReader): Domain = Domain(reader.nextString())
    override fun toJson(writer: JsonWriter, value: Domain?) {
        writer.value(value?.value)
    }
}
