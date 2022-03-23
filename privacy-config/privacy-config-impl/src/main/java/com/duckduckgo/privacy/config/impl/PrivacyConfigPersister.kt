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

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.privacyFeatureValueOf
import com.duckduckgo.privacy.config.impl.models.JsonPrivacyConfig
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.PrivacyConfig
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.PrivacyConfigRepository
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import dagger.SingleInstanceIn

interface PrivacyConfigPersister {
    suspend fun persistPrivacyConfig(jsonPrivacyConfig: JsonPrivacyConfig)
}

@WorkerThread
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPrivacyConfigPersister @Inject constructor(
    private val privacyFeaturePluginPoint: PluginPoint<PrivacyFeaturePlugin>,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository,
    private val unprotectedTemporaryRepository: UnprotectedTemporaryRepository,
    private val privacyConfigRepository: PrivacyConfigRepository,
    private val database: PrivacyConfigDatabase
) : PrivacyConfigPersister {

    override suspend fun persistPrivacyConfig(jsonPrivacyConfig: JsonPrivacyConfig) {
        val privacyConfig = privacyConfigRepository.get()
        val newVersion = jsonPrivacyConfig.version
        val previousVersion = privacyConfig?.version ?: 0

        if (newVersion > previousVersion) {
            database.runInTransaction {
                privacyFeatureTogglesRepository.deleteAll()
                privacyConfigRepository.insert(PrivacyConfig(version = jsonPrivacyConfig.version, readme = jsonPrivacyConfig.readme))
                unprotectedTemporaryRepository.updateAll(jsonPrivacyConfig.unprotectedTemporary)
                jsonPrivacyConfig.features.forEach { feature ->
                    feature.value?.let { jsonObject ->
                        privacyFeaturePluginPoint.getPlugins().forEach { plugin ->
                            privacyFeatureValueOf(feature.key)?.let {
                                plugin.store(it, jsonObject.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
