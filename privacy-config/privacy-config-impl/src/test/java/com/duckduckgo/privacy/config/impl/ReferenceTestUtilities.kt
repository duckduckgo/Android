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

import com.duckduckgo.common.test.FileUtilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.impl.RealPrivacyConfigPersisterTest.FakePrivacyVariantManagerPlugin
import com.duckduckgo.privacy.config.impl.features.contentblocking.ContentBlockingPlugin
import com.duckduckgo.privacy.config.impl.features.drm.DrmPlugin
import com.duckduckgo.privacy.config.impl.features.gpc.GpcPlugin
import com.duckduckgo.privacy.config.impl.features.https.HttpsPlugin
import com.duckduckgo.privacy.config.impl.features.trackerallowlist.TrackerAllowlistPlugin
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.RealPrivacyConfigRepository
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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.TestScope
import org.mockito.kotlin.mock

class ReferenceTestUtilities(
    db: PrivacyConfigDatabase,
    dispatcherProvider: DispatcherProvider,
) {
    private val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()

    var privacyRepository: PrivacyConfigRepository = RealPrivacyConfigRepository(db)
    var privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository = mock()
    var unprotectedTemporaryRepository: UnprotectedTemporaryRepository = RealUnprotectedTemporaryRepository(db, TestScope(), dispatcherProvider, true)
    var contentBlockingRepository: ContentBlockingRepository = RealContentBlockingRepository(db, TestScope(), dispatcherProvider, true)
    var httpsRepository: HttpsRepository = RealHttpsRepository(db, TestScope(), dispatcherProvider, true)
    var drmRepository: DrmRepository = RealDrmRepository(db, TestScope(), dispatcherProvider, true)
    var gpcRepository: GpcRepository = RealGpcRepository(mock(), db, TestScope(), dispatcherProvider, true)
    var trackerAllowlistRepository: TrackerAllowlistRepository = RealTrackerAllowlistRepository(db, TestScope(), dispatcherProvider, true)

    // Add your plugin to this list in order for it to be tested against some basic reference tests
    private fun getPrivacyFeaturePlugins(): List<PrivacyFeaturePlugin> {
        return listOf(
            HttpsPlugin(httpsRepository, privacyFeatureTogglesRepository),
            ContentBlockingPlugin(contentBlockingRepository, privacyFeatureTogglesRepository),
            DrmPlugin(drmRepository, privacyFeatureTogglesRepository),
            GpcPlugin(gpcRepository, privacyFeatureTogglesRepository),
            TrackerAllowlistPlugin(trackerAllowlistRepository, privacyFeatureTogglesRepository),
        )
    }

    fun getJsonPrivacyConfig(jsonFileName: String): JsonPrivacyConfig {
        val jsonAdapter: JsonAdapter<JsonPrivacyConfig> = moshi.adapter(
            JsonPrivacyConfig::class.java,
        )
        val config: JsonPrivacyConfig? = jsonAdapter.fromJson(FileUtilities.loadText(javaClass.classLoader!!, jsonFileName))
        return config!!
    }

    fun getPrivacyFeaturePluginPoint(): PluginPoint<PrivacyFeaturePlugin> {
        return FakePrivacyFeaturePluginPoint(getPrivacyFeaturePlugins())
    }

    fun getVariantManagerPlugin(): PrivacyFeaturePlugin {
        return FakePrivacyVariantManagerPlugin()
    }

    internal class FakePrivacyFeaturePluginPoint(private val plugins: Collection<PrivacyFeaturePlugin>) :
        PluginPoint<PrivacyFeaturePlugin> {
        override fun getPlugins(): Collection<PrivacyFeaturePlugin> {
            return plugins
        }
    }
}
