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

package com.duckduckgo.privacy.config.impl.features.trackingparameters

import android.net.Uri
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.replaceQueryParameters
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTrackingParameters @Inject constructor(
    private val trackingParametersRepository: TrackingParametersRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary
) : TrackingParameters {

    override fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return trackingParametersRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun cleanTrackingParameters(url: String): String? {
        if (featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingParametersFeatureName()) == false) return null
        if (isAnException(url)) return null

        val trackingParameters = trackingParametersRepository.parameters

        val uri = Uri.parse(url)
        val queryParameters = uri.queryParameterNames

        if (queryParameters.isEmpty()) {
            return null
        }
        val preservedParameters = getPreservedParameters(queryParameters, trackingParameters)
        if (preservedParameters.size == queryParameters.size) {
            return null
        }
        val cleanedUri = uri.replaceQueryParameters(preservedParameters)
        return cleanedUri.toString()
    }

    private fun getPreservedParameters(
        queryParameters: MutableSet<String>,
        trackingParameters: List<Regex>
    ) =
        queryParameters.filter { parameter ->
            var match = false
            for (trackingParameter in trackingParameters) {
                match = parameter.matches(trackingParameter)
                if (match) break
            }
            !match
        }
}
