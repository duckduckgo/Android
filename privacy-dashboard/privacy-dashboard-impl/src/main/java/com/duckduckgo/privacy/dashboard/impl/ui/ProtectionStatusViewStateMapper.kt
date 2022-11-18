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

package com.duckduckgo.privacy.dashboard.impl.ui

import com.duckduckgo.app.global.model.Site
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName.ContentBlockingFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ProtectionStatusViewState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface ProtectionStatusViewStateMapper {
    fun mapFromSite(site: Site): ProtectionStatusViewState
}

@ContributesBinding(AppScope::class)
class AppProtectionStatusViewStateMapper @Inject constructor(
    private val contentBlocking: ContentBlocking,
    private val unprotectedTemporary: UnprotectedTemporary,
) : ProtectionStatusViewStateMapper {

    override fun mapFromSite(site: Site): ProtectionStatusViewState {
        // List of enabled features that are supported by the privacy dashboard
        // docs: https://duckduckgo.github.io/privacy-dashboard/example/docs/interfaces/Generated_Schema_Definitions.ProtectionsStatus.html#enabledFeatures
        // if too many privacy features are required as dependencies, extract them via plugins
        val enabledFeatures = mutableListOf<String>().apply {
            if (!contentBlocking.isAnException(site.url)) {
                add(ContentBlockingFeatureName.value)
            }
        }

        return ProtectionStatusViewState(
            allowlisted = site.userAllowList,
            denylisted = false,
            enabledFeatures = enabledFeatures,
            unprotectedTemporary = unprotectedTemporary.isAnException(site.url),
        )
    }
}
