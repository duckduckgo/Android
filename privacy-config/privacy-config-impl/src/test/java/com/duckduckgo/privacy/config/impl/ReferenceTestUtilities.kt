/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl

import com.duckduckgo.privacy.config.impl.features.contentblocking.ContentBlockingPlugin
import com.duckduckgo.privacy.config.impl.features.drm.DrmPlugin
import com.duckduckgo.privacy.config.impl.features.gpc.GpcPlugin
import com.duckduckgo.privacy.config.impl.features.https.HttpsPlugin
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.TrackerAllowlistPlugin
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.nhaarman.mockitokotlin2.mock
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

object ReferenceTestUtilities {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    fun getPrivacyFeaturePlugins(privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository): List<PrivacyFeaturePlugin> {
        // Add your plugin to this list in order for it to be tested against some basic reference tests
        return listOf(
            HttpsPlugin(mock(), privacyFeatureTogglesRepository),
            ContentBlockingPlugin(mock(), privacyFeatureTogglesRepository),
            DrmPlugin(mock(), privacyFeatureTogglesRepository),
            GpcPlugin(mock(), privacyFeatureTogglesRepository),
            TrackerAllowlistPlugin(mock(), privacyFeatureTogglesRepository)
        )
    }

    fun getJsonPrivacyConfig(jsonFileName: String): JsonPrivacyConfig {
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(
            JsonPrivacyConfig::class.java)
        val config: JsonPrivacyConfig? = jsonAdapter.fromJson(FileUtilities.loadText(jsonFileName))
        return config!!
    }
}
