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

package com.duckduckgo.privacy.config.impl.features.amplinks

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppScope::class)
class AmpLinksPlugin @Inject constructor(
    private val ampLinksRepository: AmpLinksRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(name: PrivacyFeatureName, jsonString: String): Boolean {
        if (name == featureName) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<AmpLinksFeature> =
                moshi.adapter(AmpLinksFeature::class.java)

            val exceptions = mutableListOf<AmpLinkExceptionEntity>()
            val ampLinkFormats = mutableListOf<AmpLinkFormatEntity>()
            val ampKeywords = mutableListOf<AmpKeywordEntity>()

            val ampLinksFeature: AmpLinksFeature? = jsonAdapter.fromJson(jsonString)

            ampLinksFeature?.exceptions?.map {
                exceptions.add(AmpLinkExceptionEntity(it.domain, it.reason))
            }

            ampLinksFeature?.settings?.linkFormats?.map {
                ampLinkFormats.add(AmpLinkFormatEntity(it))
            }

            ampLinksFeature?.settings?.keywords?.map {
                ampKeywords.add(AmpKeywordEntity(it))
            }

            ampLinksRepository.updateAll(exceptions, ampLinkFormats, ampKeywords)
            val isEnabled = ampLinksFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.AmpLinksFeatureName
}
