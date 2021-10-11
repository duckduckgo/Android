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

package com.duckduckgo.privacy.config.impl.features.trackerallowlist

import com.duckduckgo.app.global.UriString
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealTrackerAllowlist @Inject constructor(private val trackerAllowlistRepository: TrackerAllowlistRepository, private val featureToggle: FeatureToggle) : TrackerAllowlist {

    override fun isAnException(documentURL: String, url: String): Boolean {
        return if (featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackerAllowlistFeatureName(), true) == true) {
            trackerAllowlistRepository.exceptions
                .filter { UriString.sameOrSubdomain(url, it.domain) }
                .map { matches(url, documentURL, it) }
                .firstOrNull() ?: false
        } else {
            false
        }
    }

    private fun matches(url: String, documentUrl: String, trackerAllowlist: TrackerAllowlistEntity): Boolean {
        val cleanedUrl = removePortFromUrl(URI.create(url))
        return trackerAllowlist.rules.any {
            val regex = ".*${it.rule}.*".toRegex()
            cleanedUrl.matches(regex) && (it.domains.contains("<all>") || it.domains.any { domain -> UriString.sameOrSubdomain(documentUrl, domain) })
        }
    }

    private fun removePortFromUrl(uri: URI): String {
        return URI(uri.scheme, uri.host, uri.path, uri.fragment).toString()
    }
}
