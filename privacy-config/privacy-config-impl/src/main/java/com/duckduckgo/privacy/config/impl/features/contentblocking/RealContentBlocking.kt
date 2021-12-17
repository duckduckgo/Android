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

package com.duckduckgo.privacy.config.impl.features.contentblocking

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.impl.features.unprotectedtemporary.UnprotectedTemporary
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingRepository
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealContentBlocking
@Inject
constructor(
    private val contentBlockingRepository: ContentBlockingRepository,
    private val featureToggle: FeatureToggle,
    private val unprotectedTemporary: UnprotectedTemporary
) : ContentBlocking {

    override fun isAnException(url: String): Boolean {
        return if (featureToggle.isFeatureEnabled(
            PrivacyFeatureName.ContentBlockingFeatureName(), true) == true) {
            unprotectedTemporary.isAnException(url) || matches(url)
        } else {
            false
        }
    }

    private fun matches(url: String): Boolean {
        return contentBlockingRepository.exceptions.any { sameOrSubdomain(url, it.domain) }
    }
}
