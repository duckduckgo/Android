/*
 * Copyright (c) 2023 DuckDuckGo
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
import com.duckduckgo.privacy.config.internal.store.DevPrivacyConfigSettingsDataStore
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.LogPriority.INFO
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class PrivacyConfigOverrideLogger @Inject constructor(
    private val store: DevPrivacyConfigSettingsDataStore,
) : PrivacyConfigCallbackPlugin {
    override fun onPrivacyConfigDownloaded() = Unit

    override fun onPrivacyConfigPersisted(version: Long, eTag: String?, source: String?) {
        val overrideUrl = store.remotePrivacyConfigUrl
        if (store.useCustomPrivacyConfigUrl && PrivacyConfigInternalLoader.isValidUrl(overrideUrl) &&
            source != null && source.toHttpUrlOrNull() == overrideUrl?.toHttpUrlOrNull()
        ) {
            logcat(INFO) { "CONFIG_OVERRIDE_APPLIED stage=config version=$version etag=${eTag.orEmpty()} source=$overrideUrl" }
        }
    }
}
