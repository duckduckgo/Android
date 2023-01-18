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
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.CookiePromptManagementState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoconsentStatusViewStateMapper {
    fun mapFromSite(site: Site): CookiePromptManagementState
}

@ContributesBinding(AppScope::class)
class CookiePromptManagementStatusViewStateMapper @Inject constructor() : AutoconsentStatusViewStateMapper {

    override fun mapFromSite(site: Site): CookiePromptManagementState {
        return CookiePromptManagementState(
            site.consentManaged,
            site.consentOptOutFailed,
            true,
            site.consentCosmeticHide,
        )
    }
}
