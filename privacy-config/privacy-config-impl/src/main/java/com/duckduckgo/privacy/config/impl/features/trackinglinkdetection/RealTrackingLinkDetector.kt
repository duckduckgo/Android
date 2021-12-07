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

import com.duckduckgo.app.global.UriString
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingLinkDetector
import com.duckduckgo.privacy.config.api.TrackingLinkInfo
import com.duckduckgo.privacy.config.api.TrackingLinkType
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackinglinkdetection.TrackingLinkDetectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealTrackingLinkDetector @Inject constructor(
    private val trackingLinkDetectionRepository: TrackingLinkDetectionRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary
) : TrackingLinkDetector {

    private var lastExtractedUrl: String? = null

    override var lastTrackingLinkInfo: TrackingLinkInfo? = null

    override fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return trackingLinkDetectionRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun extractCanonicalFromTrackingLink(url: String): TrackingLinkType? {
        if (featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingLinkDetectionFeatureName()) == false) return null
        if (isAnException(url)) return null
        if (url == lastExtractedUrl) return null

        val extractedUrl = trackingLinkDetectionRepository.extractCanonicalFromTrackingLink(url)

        lastExtractedUrl = extractedUrl

        extractedUrl?.let {
            lastTrackingLinkInfo = TrackingLinkInfo(trackingLink = url)
            return TrackingLinkType.ExtractedTrackingLink(extractedUrl = extractedUrl)
        }

        if (urlContainsTrackingKeyword(url)) {
            return TrackingLinkType.CloakedTrackingLink(trackingUrl = url)
        }
        return null
    }

    private fun urlContainsTrackingKeyword(url: String): Boolean {
        val ampKeywords = trackingLinkDetectionRepository.ampKeywords

        ampKeywords.forEach { keyword ->
            if (url.contains(keyword)) {
                return true
            }
        }
        return false
    }
}
