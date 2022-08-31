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

import com.duckduckgo.app.global.UriString
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.privacy.config.api.AmpLinkInfo
import com.duckduckgo.privacy.config.api.AmpLinkType
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAmpLinks @Inject constructor(
    private val ampLinksRepository: AmpLinksRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary
) : AmpLinks {

    private var lastExtractedUrl: String? = null

    override var lastAmpLinkInfo: AmpLinkInfo? = null

    override fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return ampLinksRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun extractCanonicalFromAmpLink(url: String): AmpLinkType? {
        if (!featureToggle.isFeatureEnabled(PrivacyFeatureName.AmpLinksFeatureName.value)) return null
        if (url == lastExtractedUrl) return null

        val extractedUrl = extractCanonical(url)

        lastExtractedUrl = extractedUrl

        extractedUrl?.let {
            return if (isAnException(extractedUrl)) {
                null
            } else {
                lastAmpLinkInfo = AmpLinkInfo(ampLink = url)
                AmpLinkType.ExtractedAmpLink(extractedUrl = extractedUrl)
            }
        }

        if (urlContainsAmpKeyword(url)) {
            return if (isAnException(url)) {
                null
            } else {
                AmpLinkType.CloakedAmpLink(ampUrl = url)
            }
        }
        return null
    }

    private fun urlContainsAmpKeyword(url: String): Boolean {
        val ampKeywords = ampLinksRepository.ampKeywords

        ampKeywords.forEach { keyword ->
            if (url.contains(keyword)) {
                return true
            }
        }
        return false
    }

    fun extractCanonical(url: String): String? {
        val ampFormat = urlIsExtractableAmpLink(url) ?: return null
        val matchResult = ampFormat.find(url) ?: return null

        val groups = matchResult.groups
        if (groups.size < 2) return null

        var destinationUrl = groups[1]?.value ?: return null

        if (!destinationUrl.startsWith("http")) {
            destinationUrl = "https://$destinationUrl"
        }
        return destinationUrl
    }

    private fun urlIsExtractableAmpLink(url: String): Regex? {
        val ampLinkFormats = ampLinksRepository.ampLinkFormats

        ampLinkFormats.forEach { format ->
            if (url.matches(format)) {
                return format
            }
        }
        return null
    }
}
