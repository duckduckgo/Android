/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl

import com.duckduckgo.adclick.api.AdClickFeatureName
import com.duckduckgo.adclick.store.AdClickAttributionAllowlistEntity
import com.duckduckgo.adclick.store.AdClickAttributionDetectionEntity
import com.duckduckgo.adclick.store.AdClickAttributionExpirationEntity
import com.duckduckgo.adclick.store.AdClickAttributionLinkFormatEntity
import com.duckduckgo.adclick.store.AdClickAttributionRepository
import com.duckduckgo.adclick.store.AdClickFeatureToggleRepository
import com.duckduckgo.adclick.store.AdClickFeatureToggles
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AdClickAttributionPlugin @Inject constructor(
    private val adClickAttributionRepository: AdClickAttributionRepository,
    private val adClickFeatureTogglesRepository: AdClickFeatureToggleRepository
) : PrivacyFeaturePlugin {

    override fun store(featureName: String, jsonString: String): Boolean {
        val adClickFeature = adClickFeatureValueOf(featureName) ?: return false
        if (adClickFeature.value == this.featureName) {
            val moshi = Moshi.Builder().build()
            val jsonAdapter: JsonAdapter<AdClickAttributionFeature> =
                moshi.adapter(AdClickAttributionFeature::class.java)

            val linkFormats = mutableListOf<AdClickAttributionLinkFormatEntity>()
            val allowList = mutableListOf<AdClickAttributionAllowlistEntity>()
            val expirations = mutableListOf<AdClickAttributionExpirationEntity>()
            val detections = mutableListOf<AdClickAttributionDetectionEntity>()

            val adClickAttributionFeature: AdClickAttributionFeature? = jsonAdapter.fromJson(jsonString)

            adClickAttributionFeature?.settings?.linkFormats?.map {
                linkFormats.add(
                    AdClickAttributionLinkFormatEntity(
                        url = it.url,
                        adDomainParameterName = it.adDomainParameterName.orEmpty()
                    )
                )
            }

            adClickAttributionFeature?.settings?.allowlist?.map {
                if (it.blocklistEntry != null && it.host != null) {
                    allowList.add(AdClickAttributionAllowlistEntity(blocklistEntry = it.blocklistEntry, host = it.host))
                }
            }

            adClickAttributionFeature?.settings?.let {
                expirations.add(
                    AdClickAttributionExpirationEntity(
                        navigationExpiration = it.navigationExpiration,
                        totalExpiration = it.totalExpiration
                    )
                )
                detections.add(
                    AdClickAttributionDetectionEntity(
                        heuristicDetection = it.heuristicDetection.orEmpty(),
                        domainDetection = it.domainDetection.orEmpty()
                    )
                )
            }

            adClickAttributionRepository.updateAll(linkFormats, allowList, expirations, detections)
            val isEnabled = adClickAttributionFeature?.state == "enabled"
            adClickFeatureTogglesRepository.insert(AdClickFeatureToggles(adClickFeature, isEnabled, adClickAttributionFeature?.minSupportedVersion))
            return true
        }
        return false
    }

    override val featureName: String = AdClickFeatureName.AdClickAttributionFeatureName.value
}
