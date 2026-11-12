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
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader.ConfigDownloadResult
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class PrivacyConfigUrlSeederPlugin @Inject constructor(
    private val store: DevPrivacyConfigSettingsDataStore,
    private val privacyConfigRepository: PrivacyConfigRepository,
    private val loader: PrivacyConfigInternalLoader,
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

        // LocalPrivacyConfigObserver persists the bundled config asynchronously at launch, outside the downloader. Its version
        // is far higher than any test config's, so if it lands after the forced load below it replaces the custom config.
        val bundledConfigLoaded = withTimeoutOrNull(BUNDLED_CONFIG_TIMEOUT_MS) {
            while (privacyConfigRepository.get() == null) delay(BUNDLED_CONFIG_POLL_MS)
        } != null
        check(bundledConfigLoaded) { "PrivacyConfigUrlSeederPlugin: bundled privacy config not loaded after ${BUNDLED_CONFIG_TIMEOUT_MS}ms" }

        val result = loader.load()
        check(result is ConfigDownloadResult.Success) {
            "PrivacyConfigUrlSeederPlugin: download from '$url' failed: ${(result as? ConfigDownloadResult.Error)?.error}"
        }
    }

    private companion object {
        const val BUNDLED_CONFIG_TIMEOUT_MS = 10_000L
        const val BUNDLED_CONFIG_POLL_MS = 100L
    }
}
