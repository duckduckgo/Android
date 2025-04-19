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

import android.net.Uri
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionFeature
import com.duckduckgo.adclick.impl.remoteconfig.AdClickAttributionRepository
import com.duckduckgo.adclick.impl.store.AdClickAttributionLinkFormatEntity
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface AdClickAttribution {

    fun isAllowed(url: String): Boolean
    fun isAdClick(url: String): Pair<Boolean, String?>
    fun getNavigationExpirationMillis(): Long
    fun getTotalExpirationMillis(): Long
    fun isHeuristicDetectionEnabled(): Boolean
    fun isDomainDetectionEnabled(): Boolean
}

@ContributesBinding(AppScope::class)
class RealAdClickAttribution @Inject constructor(
    private val adClickAttributionRepository: AdClickAttributionRepository,
    private val adClickAttributionFeature: AdClickAttributionFeature,
) : AdClickAttribution {

    override fun isAllowed(url: String): Boolean {
        if (!adClickAttributionFeature.self().isEnabled()) {
            return false
        }
        if (!isHeuristicDetectionEnabled() && !isDomainDetectionEnabled()) {
            return false
        }
        return adClickAttributionRepository.allowList.any { UriString.sameOrSubdomain(url, it.host) }
    }

    override fun isAdClick(url: String): Pair<Boolean, String?> {
        val noMatch = Pair(false, null)

        if (!adClickAttributionFeature.self().isEnabled()) {
            return noMatch
        }
        if (!isHeuristicDetectionEnabled() && !isDomainDetectionEnabled()) {
            return noMatch
        }
        adClickAttributionRepository.linkFormats.forEach {
            val result = matchesFormat(url, it)
            if (result.first) {
                return result
            }
        }

        return noMatch
    }

    override fun getNavigationExpirationMillis(): Long {
        if (adClickAttributionRepository.expirations.isEmpty()) {
            return 0L
        }
        return TimeUnit.SECONDS.toMillis(adClickAttributionRepository.expirations[0].navigationExpiration)
    }

    override fun getTotalExpirationMillis(): Long {
        if (adClickAttributionRepository.expirations.isEmpty()) {
            return 0L
        }
        return TimeUnit.SECONDS.toMillis(adClickAttributionRepository.expirations[0].totalExpiration)
    }

    override fun isHeuristicDetectionEnabled(): Boolean {
        if (adClickAttributionRepository.detections.isEmpty()) {
            return false
        }
        return adClickAttributionRepository.detections[0].heuristicDetection.equals(STATE_ENABLED, true)
    }

    override fun isDomainDetectionEnabled(): Boolean {
        if (adClickAttributionRepository.detections.isEmpty()) {
            return false
        }
        return adClickAttributionRepository.detections[0].domainDetection.equals(STATE_ENABLED, true)
    }

    private fun matchesFormat(url: String, linkFormat: AdClickAttributionLinkFormatEntity): Pair<Boolean, String?> {
        val noMatch = Pair(false, null)

        if (!url.contains(linkFormat.url, true)) {
            return noMatch
        }

        val uri = Uri.parse(url)
        if (linkFormat.adDomainParameterName.isEmpty()) {
            return Pair(true, null)
        }
        if (linkFormat.adDomainParameterName.isNotEmpty() && uri.queryParameterNames.contains(linkFormat.adDomainParameterName)) {
            return Pair(true, uri.getQueryParameter(linkFormat.adDomainParameterName))
        }

        return noMatch
    }

    companion object {
        private const val STATE_ENABLED = "enabled"
    }
}
