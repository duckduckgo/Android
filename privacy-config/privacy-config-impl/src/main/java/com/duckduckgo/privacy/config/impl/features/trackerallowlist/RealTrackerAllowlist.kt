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

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Singleton

@ContributesBinding(AppObjectGraph::class)
@Singleton
class RealTrackerAllowlist @Inject constructor(private val trackerAllowlistRepository: TrackerAllowlistRepository, private val featureToggle: FeatureToggle) : TrackerAllowlist {

    override fun isAnException(documentURL: String, url: String): Boolean {
        return if (featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackerAllowlistFeatureName(), true) == true) {
            val documentUri = documentURL.toUri()
            val uri = url.toUri()

            trackerAllowlistRepository.exceptions
                .filter { it.domain == uri.baseHost }
                .map { matches(url, documentUri, it) }
                .firstOrNull() ?: false
        } else {
            false
        }
    }

    private fun matches(url: String, documentUri: Uri, trackerAllowlist: TrackerAllowlistEntity): Boolean {
        return trackerAllowlist.rules.any {
            val regex = ".*${it.rule}.*".toRegex()
            url.matches(regex) && (it.domains.contains("<all>") || it.domains.contains(documentUri.baseHost))
        }
    }

}
