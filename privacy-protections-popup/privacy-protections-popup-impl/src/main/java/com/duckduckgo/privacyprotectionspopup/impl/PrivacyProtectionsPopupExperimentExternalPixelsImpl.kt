/*
 * Copyright (c) 2024 DuckDuckGo
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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class PrivacyProtectionsPopupExperimentExternalPixelsImpl @Inject constructor() : PrivacyProtectionsPopupExperimentExternalPixels {

    override suspend fun getPixelParams(): Map<String, String> {
        return emptyMap()
    }

    override fun tryReportPrivacyDashboardOpened() {
    }

    override fun tryReportProtectionsToggledFromPrivacyDashboard(protectionsEnabled: Boolean) {
    }

    override fun tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled: Boolean) {
    }

    override fun tryReportProtectionsToggledFromBrokenSiteReport(protectionsEnabled: Boolean) {
    }
}
