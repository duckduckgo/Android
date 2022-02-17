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
import androidx.core.net.toUri
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.replaceQueryParameters
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import java.lang.UnsupportedOperationException
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealTrackingParameters @Inject constructor(
    private val trackingParametersRepository: TrackingParametersRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary,
    val userWhiteListRepository: UserWhiteListRepository
) : TrackingParameters {

    override var lastCleanedUrl: String? = null

    override fun isAnException(url: String): Boolean {
        return matches(url) ||
            unprotectedTemporary.isAnException(url) ||
            userWhiteListRepository.userWhiteList.contains(url.toUri().domain())
    }

    private fun matches(url: String): Boolean {
        return trackingParametersRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun cleanTrackingParameters(url: String): String? {
        if (featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingParametersFeatureName()) == false) return null
        if (isAnException(url)) return null

        val trackingParameters = trackingParametersRepository.parameters

        val uri = Uri.parse(url)

        try {
            val queryParameters = uri.queryParameterNames

            if (queryParameters.isEmpty()) {
                return null
            }
            val preservedParameters = getPreservedParameters(queryParameters, trackingParameters)
            if (preservedParameters.size == queryParameters.size) {
                return null
            }
            val cleanedUrl = uri.replaceQueryParameters(preservedParameters).toString()

            lastCleanedUrl = cleanedUrl

            return cleanedUrl
        } catch (exception: UnsupportedOperationException) {
            Timber.e("Tracking Parameter Removal: ${exception.message}")
            return null
        }
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
