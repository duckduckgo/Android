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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.ContentBlockingExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyFeatureToggles
import com.duckduckgo.privacy.config.store.PrivacyFeatureTogglesRepository
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ContentBlockingPlugin
@Inject
constructor(
    private val contentBlockingRepository: ContentBlockingRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(name: String, jsonString: String): Boolean {
        if (name == featureName.value) {
            val contentBlockingExceptions = mutableListOf<ContentBlockingExceptionEntity>()
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<ContentBlockingFeature> =
                moshi.adapter(ContentBlockingFeature::class.java)

            val contentBlockingFeature: ContentBlockingFeature? = jsonAdapter.fromJson(jsonString)
            contentBlockingFeature?.exceptions?.map {
                contentBlockingExceptions.add(ContentBlockingExceptionEntity(it.domain, it.reason))
            }
            contentBlockingRepository.updateAll(contentBlockingExceptions)
            val isEnabled = contentBlockingFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.ContentBlockingFeatureName()
}
