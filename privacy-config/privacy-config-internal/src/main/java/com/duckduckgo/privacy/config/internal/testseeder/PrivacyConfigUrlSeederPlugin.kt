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

package com.duckduckgo.privacy.config.internal.testseeder

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import dev.zacsweers.metro.ContributesIntoSet
import java.net.URI
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class PrivacyConfigUrlSeederPlugin @Inject constructor(
    private val store: DevPrivacyConfigSettingsDataStore,
    private val privacyConfigRepository: PrivacyConfigRepository,
    private val downloader: PrivacyConfigDownloader,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.PRIVACY_CONFIG_URL.key)

    override suspend fun apply(key: String, value: String) {
        val url = value.trim()
        // ApiDevPrivacyConfigInterceptor silently falls back to the production config for an invalid URL,
        // which would let the flow run against the wrong config, so fail here instead.
        val uri = runCatching { URI(url) }.getOrNull()
        require(uri?.scheme in setOf("http", "https") && !uri?.host.isNullOrEmpty()) {
            "PrivacyConfigUrlSeederPlugin: invalid URL '$value' for key '$key'"
        }

        store.remotePrivacyConfigUrl = url
        store.useCustomPrivacyConfigUrl = true

        // The persister only stores a config newer than the current one; resetting the version, as the
        // dev settings "Load" button does, makes it accept the custom config whatever its version.
        privacyConfigRepository.insert(PrivacyConfig(version = -1, readme = "", eTag = "", timestamp = ""))
        val result = downloader.download()
        check(result is ConfigDownloadResult.Success) {
            "PrivacyConfigUrlSeederPlugin: download from '$url' failed: ${(result as? ConfigDownloadResult.Error)?.error}"
        }
    }
}
