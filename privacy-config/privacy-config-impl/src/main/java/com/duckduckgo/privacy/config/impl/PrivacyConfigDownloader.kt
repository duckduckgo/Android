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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.PrivacyConfigDownloader
import com.duckduckgo.privacy.config.impl.network.PrivacyConfigService
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

@ContributesBinding(AppObjectGraph::class)
class RealPrivacyConfigDownloader @Inject constructor(private val privacyConfigService: PrivacyConfigService, private val privacyFeaturePluginPoint: PluginPoint<PrivacyFeaturePlugin>, privacyConfigDatabase: PrivacyConfigDatabase) : PrivacyConfigDownloader {

    private val privacyFeatureTogglesDao = privacyConfigDatabase.privacyFeatureTogglesDao()

    override suspend fun download(): Boolean {
        Timber.d("Downloading privacy config")
        val response = runCatching {
            privacyConfigService.privacyConfig()
        }.onSuccess {
            privacyFeatureTogglesDao.deleteAll()
            it.features.forEach { feature ->
                privacyFeaturePluginPoint.getPlugins().forEach { plugin ->
                    plugin.store(feature.key, feature.value)
                }
            }
        }.onFailure {
            Timber.w(it.localizedMessage)
        }
        return response.isSuccess
    }
}
