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

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.privacy.config.impl.features.contentblocking.ContentBlockingPlugin
import com.duckduckgo.privacy.config.impl.features.drm.DrmPlugin
import com.duckduckgo.privacy.config.impl.features.gpc.GpcPlugin
import com.duckduckgo.privacy.config.impl.features.https.HttpsPlugin
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.TrackerAllowlistPlugin
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesDataStore
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
import com.duckduckgo.privacy.config.store.RealPrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.RealContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.drm.DrmRepository
import com.duckduckgo.privacy.config.store.features.drm.RealDrmRepository
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.duckduckgo.privacy.config.store.features.gpc.RealGpcRepository
import com.duckduckgo.privacy.config.store.features.https.HttpsRepository
import com.duckduckgo.privacy.config.store.features.https.RealHttpsRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.RealTrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.nhaarman.mockitokotlin2.mock
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope

@ExperimentalCoroutinesApi
class ReferenceTestUtilities(db: PrivacyConfigDatabase, val dispatcherProvider: DispatcherProvider) {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    var privacyRepository: PrivacyConfigRepository = RealPrivacyConfigRepository(db)
    var privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    var unprotectedTemporaryRepository: UnprotectedTemporaryRepository = RealUnprotectedTemporaryRepository(db, TestCoroutineScope(), dispatcherProvider)
    var contentBlockingRepository: ContentBlockingRepository = RealContentBlockingRepository(db, TestCoroutineScope(), dispatcherProvider)
    var httpsRepository: HttpsRepository = RealHttpsRepository(db, TestCoroutineScope(), dispatcherProvider)
    var drmRepository: DrmRepository = RealDrmRepository(db, TestCoroutineScope(), dispatcherProvider)
    var gpcRepository: GpcRepository = RealGpcRepository(mock(), db, TestCoroutineScope(), dispatcherProvider)
    var trackerAllowlistRepository: TrackerAllowlistRepository = RealTrackerAllowlistRepository(db, TestCoroutineScope(), dispatcherProvider)

    init {

    }

    // Add your plugin to this list in order for it to be tested against some basic reference tests
    private fun getPrivacyFeaturePlugins(): List<PrivacyFeaturePlugin> {
        return listOf(
            HttpsPlugin(httpsRepository, privacyFeatureTogglesRepository),
            ContentBlockingPlugin(contentBlockingRepository, privacyFeatureTogglesRepository),
            DrmPlugin(drmRepository, privacyFeatureTogglesRepository),
            GpcPlugin(gpcRepository, privacyFeatureTogglesRepository),
            TrackerAllowlistPlugin(trackerAllowlistRepository, privacyFeatureTogglesRepository)
        )
    }

    fun getJsonPrivacyConfig(jsonFileName: String): JsonPrivacyConfig {
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(
            JsonPrivacyConfig::class.java)
        val config: JsonPrivacyConfig? = jsonAdapter.fromJson(FileUtilities.loadText(jsonFileName))
        return config!!
    }

    fun getPrivacyFeaturePluginPoint(): PluginPoint<PrivacyFeaturePlugin> {
        return FakePrivacyFeaturePluginPoint(getPrivacyFeaturePlugins())
    }

    internal class FakePrivacyFeaturePluginPoint(private val plugins: Collection<PrivacyFeaturePlugin>):
        PluginPoint<PrivacyFeaturePlugin> {
        override fun getPlugins(): Collection<PrivacyFeaturePlugin> {
            return plugins
        }
    }

}
