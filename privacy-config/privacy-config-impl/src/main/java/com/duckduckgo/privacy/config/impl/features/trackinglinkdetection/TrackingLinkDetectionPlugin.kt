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

package com.duckduckgo.privacy.config.impl.features.trackinglinkdetection

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.plugins.PrivacyFeaturePlugin
import com.duckduckgo.privacy.config.store.*
import com.duckduckgo.privacy.config.store.features.trackinglinkdetection.TrackingLinkDetectionRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

@ContributesMultibinding(AppObjectGraph::class)
class TrackingLinkDetectionPlugin @Inject constructor(
    private val trackingLinkDetectionRepository: TrackingLinkDetectionRepository,
    private val privacyFeatureTogglesRepository: PrivacyFeatureTogglesRepository
) : PrivacyFeaturePlugin {

    override fun store(name: String, jsonString: String): Boolean {
        if (name == featureName.value) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<TrackingLinkDetectionFeature> =
                moshi.adapter(TrackingLinkDetectionFeature::class.java)

            val exceptions = mutableListOf<TrackingLinkExceptionEntity>()
            val ampLinkFormats = mutableListOf<AmpLinkFormatEntity>()
            val ampKeywords = mutableListOf<AmpKeywordEntity>()
            val trackingParameters = mutableListOf<TrackingParameterEntity>()

            val trackingLinkDetectionFeature: TrackingLinkDetectionFeature? = jsonAdapter.fromJson(jsonString)

            trackingLinkDetectionFeature?.exceptions?.map {
                exceptions.add(TrackingLinkExceptionEntity(it.domain, it.reason))
            }

            trackingLinkDetectionFeature?.settings?.ampLinkFormats?.map {
                ampLinkFormats.add(AmpLinkFormatEntity(it))
            }

            trackingLinkDetectionFeature?.settings?.ampKeywords?.map {
                ampKeywords.add(AmpKeywordEntity(it))
            }

            trackingLinkDetectionFeature?.settings?.trackingParameters?.map {
                trackingParameters.add(TrackingParameterEntity(it))
            }

            trackingLinkDetectionRepository.updateAll(exceptions, ampLinkFormats, ampKeywords, trackingParameters)
            val isEnabled = trackingLinkDetectionFeature?.state == "enabled"
            privacyFeatureTogglesRepository.insert(PrivacyFeatureToggles(name, isEnabled))
            return true
        }
        return false
    }

    override val featureName: PrivacyFeatureName = PrivacyFeatureName.TrackingLinkDetectionFeatureName()
}
