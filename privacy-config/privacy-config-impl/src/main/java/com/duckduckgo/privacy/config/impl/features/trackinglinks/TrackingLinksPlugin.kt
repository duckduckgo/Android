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

package com.duckduckgo.privacy.config.impl.features.trackinglinks

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.trackinglinks.TrackingLinksRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppObjectGraph::class)
class TrackingLinksPlugin @Inject constructor(
    private val trackingLinksRepository: TrackingLinksRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(name: String, jsonString: String): Boolean {
        if (name == featureName.value) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<TrackingLinksFeature> =
                moshi.adapter(TrackingLinksFeature::class.java)

            val exceptions = mutableListOf<TrackingLinksExceptionEntity>()
            val ampLinkFormats = mutableListOf<AmpLinkFormatEntity>()
            val ampKeywords = mutableListOf<AmpKeywordEntity>()
            val trackingParameters = mutableListOf<TrackingParameterEntity>()

            val trackingLinksFeature: TrackingLinksFeature? = jsonAdapter.fromJson(jsonString)

            trackingLinksFeature?.exceptions?.map {
                exceptions.add(TrackingLinksExceptionEntity(it.domain, it.reason))
            }

            trackingLinksFeature?.settings?.ampLinkFormats?.map {
                ampLinkFormats.add(AmpLinkFormatEntity(it))
            }

            trackingLinksFeature?.settings?.ampKeywords?.map {
                ampKeywords.add(AmpKeywordEntity(it))
            }

            trackingLinksFeature?.settings?.trackingParameters?.map {
                trackingParameters.add(TrackingParameterEntity(it))
            }

            trackingLinksRepository.updateAll(exceptions, ampLinkFormats, ampKeywords, trackingParameters)
            val isEnabled = trackingLinksFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.TrackingLinksFeatureName()
}
