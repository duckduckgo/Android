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
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.replaceQueryParameters
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.TrackingParameters
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import dagger.WrongScope
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.lang.UnsupportedOperationException
import javax.inject.Inject

@WrongScope("This should be one instance per BrowserTabFragment", FragmentScope::class)
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTrackingParameters @Inject constructor(
    private val trackingParametersRepository: TrackingParametersRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val userAllowListRepository: UserAllowListRepository,
) : TrackingParameters {

    override var lastCleanedUrl: String? = null

    @VisibleForTesting
    fun isAnException(initiatingUrl: String?, url: String): Boolean {
        return matches(initiatingUrl) || matches(url) || unprotectedTemporary.isAnException(url) || userAllowListRepository.isUrlInUserAllowList(url)
    }

    private fun matches(url: String?): Boolean {
        if (url == null) return false
        return trackingParametersRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun cleanTrackingParameters(initiatingUrl: String?, url: String): String? {
        if (!featureToggle.isFeatureEnabled(PrivacyFeatureName.TrackingParametersFeatureName.value)) return null
        if (isAnException(initiatingUrl, url)) return null

        val parsedUri = Uri.parse(url)

        // In some instances, particularly with ads, the query may represent a different URL (without encoding),
        // making it difficult to detect accurately.
        val query = parsedUri.query
        val queryUri = query?.toUri()

        return if (queryUri?.isValid() == true) {
            cleanQueryUriParameters(url, query, queryUri)
        } else {
            cleanParsedUriParameters(parsedUri)
        }
    }

    private fun cleanParsedUriParameters(uri: Uri): String? {
        return cleanUri(uri) { cleanedUrl ->
            cleanedUrl
        }
    }

    private fun cleanQueryUriParameters(url: String, query: String, queryUri: Uri): String? {
        return cleanUri(queryUri) { interimCleanedUrl ->
            url.replace(query, interimCleanedUrl)
        }
    }

    private fun cleanUri(uri: Uri, buildCleanedUrl: (String) -> String): String? {
        val trackingParameters = trackingParametersRepository.parameters

        return try {
            val preservedParameters = getPreservedParameters(uri, trackingParameters) ?: return null
            val interimCleanedUrl = uri.replaceQueryParameters(preservedParameters).toString()
            val cleanedUrl = buildCleanedUrl(interimCleanedUrl)

            lastCleanedUrl = cleanedUrl
            cleanedUrl
        } catch (exception: UnsupportedOperationException) {
            logcat(ERROR) { "Tracking Parameter Removal: ${exception.asLog()}" }
            null
        }
    }

    private fun getPreservedParameters(uri: Uri, trackingParameters: List<String>): List<String>? {
        val queryParameters = uri.queryParameterNames
        if (queryParameters.isEmpty()) {
            return null
        }
        val preservedParameters = filterNonTrackingParameters(queryParameters, trackingParameters)
        return if (preservedParameters.size == queryParameters.size) {
            null
        } else {
            preservedParameters
        }
    }

    private fun Uri?.isValid(): Boolean {
        return this?.isAbsolute == true && this.isHierarchical
    }

    private fun filterNonTrackingParameters(
        queryParameters: MutableSet<String>,
        trackingParameters: List<String>,
    ) =
        queryParameters.filter { parameter ->
            var match = false
            for (trackingParameter in trackingParameters) {
                match = parameter == trackingParameter
                if (match) break
            }
            !match
        }
}
