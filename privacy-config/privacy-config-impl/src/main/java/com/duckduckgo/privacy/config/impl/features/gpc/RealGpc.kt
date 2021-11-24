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

package com.duckduckgo.privacy.config.impl.features.gpc

import android.content.Context
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.R
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.gpc.GpcRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import dagger.SingleIn

@ContributesBinding(AppObjectGraph::class)
@SingleIn(AppObjectGraph::class)
class RealGpc @Inject constructor(context: Context, private val featureToggle: FeatureToggle, val gpcRepository: GpcRepository, private val unprotectedTemporary: UnprotectedTemporary) : Gpc {

    private val gpcJsFunctions: String = context.resources.openRawResource(R.raw.gpc).bufferedReader().use { it.readText() }

    override fun getGpcJs(): String {
        return gpcJsFunctions
    }

    override fun isEnabled(): Boolean {
        return gpcRepository.isGpcEnabled()
    }

    override fun getHeaders(url: String): Map<String, String> {
        return if (canGpcBeUsedByUrl(url)) {
            mapOf(GPC_HEADER to GPC_HEADER_VALUE)
        } else {
            emptyMap()
        }
    }

    override fun canUrlAddHeaders(url: String, existingHeaders: Map<String, String>): Boolean {
        return if (canGpcBeUsedByUrl(url) && !containsGpcHeader(existingHeaders)) {
            headerConsumers.any { sameOrSubdomain(url, it) }
        } else {
            false
        }
    }

    override fun enableGpc() {
        gpcRepository.enableGpc()
    }

    override fun disableGpc() {
        gpcRepository.disableGpc()
    }

    override fun canGpcBeUsedByUrl(url: String): Boolean {
        return isFeatureEnabled() && isEnabled() && !isAnException(url)
    }

    private fun isFeatureEnabled(): Boolean {
        return featureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName()) == true
    }

    private fun containsGpcHeader(headers: Map<String, String>): Boolean {
        return headers.containsKey(GPC_HEADER)
    }

    private fun isAnException(url: String): Boolean {
        return matches(url) || unprotectedTemporary.isAnException(url)
    }

    private fun matches(url: String): Boolean {
        return gpcRepository.exceptions.any { sameOrSubdomain(url, it.domain) }
    }

    companion object {
        const val GPC_HEADER = "sec-gpc"
        const val GPC_HEADER_VALUE = "1"

        private val headerConsumers = listOf(
            "nytimes.com",
            "globalprivacycontrol.org",
            "global-privacy-control.glitch.me"
        )
    }

}
