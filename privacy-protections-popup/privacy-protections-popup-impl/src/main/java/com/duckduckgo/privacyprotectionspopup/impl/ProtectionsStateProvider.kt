/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ProtectionsStateProvider {
    fun areProtectionsEnabled(domain: String): Flow<Boolean>
}

@ContributesBinding(FragmentScope::class)
class ProtectionsStateProviderImpl @Inject constructor(
    private val featureToggle: FeatureToggle,
    private val contentBlocking: ContentBlocking,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val userAllowListRepository: UserAllowListRepository,
) : ProtectionsStateProvider {

    override fun areProtectionsEnabled(domain: String): Flow<Boolean> {
        if (
            !featureToggle.isFeatureEnabled(PrivacyFeatureName.ContentBlockingFeatureName.value) ||
            contentBlocking.isAnException(domain) ||
            unprotectedTemporary.isAnException(domain)
        ) {
            return flowOf(false)
        }

        return userAllowListRepository.domainsInUserAllowListFlow()
            .map { allowlistedDomains -> domain !in allowlistedDomains }
            .distinctUntilChanged()
    }
}
