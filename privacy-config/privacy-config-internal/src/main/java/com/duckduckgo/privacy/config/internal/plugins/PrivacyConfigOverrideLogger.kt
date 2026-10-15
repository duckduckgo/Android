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

package com.duckduckgo.privacy.config.internal.plugins

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader
import com.duckduckgo.privacy.config.internal.PrivacyConfigOverrideMarker
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

/**
 * Logs one APPLIED line per config persisted while an override is set.
 *
 * The persisted callback also fires when persistence is skipped, so a persist is detected by the stored config changing
 * since the last callback. The previous config is kept in preferences because a cold-started worker process has no memory of it.
 */
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class PrivacyConfigOverrideLogger @Inject constructor(
    private val store: DevPrivacyConfigSettingsDataStore,
    private val repository: PrivacyConfigRepository,
    private val marker: PrivacyConfigOverrideMarker,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() = Unit

    override fun onPrivacyConfigPersisted() {
        val config = repository.get() ?: return
        val current = "${config.version}|${config.eTag.orEmpty()}|${config.timestamp}"
        val previous = store.lastSeenConfig
        store.lastSeenConfig = current

        val overrideUrl = store.remotePrivacyConfigUrl
        if (previous == null || previous == current) return
        if (!store.useCustomPrivacyConfigUrl || !PrivacyConfigInternalLoader.isValidUrl(overrideUrl)) return
        marker.emit("CONFIG_OVERRIDE_APPLIED stage=config version=${config.version} etag=${config.eTag.orEmpty()} source=$overrideUrl")
    }
}
