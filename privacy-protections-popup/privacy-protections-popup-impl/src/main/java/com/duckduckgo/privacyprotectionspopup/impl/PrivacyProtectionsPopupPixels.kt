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

import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface PrivacyProtectionsPopupPixels {
    fun reportExperimentVariantAssigned()
    fun reportPopupTriggered()
    fun reportProtectionsDisabled()
    fun reportPrivacyDashboardOpened()
    fun reportPopupDismissedViaButton()
    fun reportPopupDismissedViaClickOutside()
    fun reportDoNotShowAgainClicked()
    fun reportPageRefreshOnPossibleBreakage()
}

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupPixelsImpl @Inject constructor() : PrivacyProtectionsPopupPixels {

    override fun reportExperimentVariantAssigned() {
    }

    override fun reportPopupTriggered() {
    }

    override fun reportProtectionsDisabled() {
    }

    override fun reportPrivacyDashboardOpened() {
    }

    override fun reportPopupDismissedViaButton() {
    }

    override fun reportPopupDismissedViaClickOutside() {
    }

    override fun reportDoNotShowAgainClicked() {
    }

    override fun reportPageRefreshOnPossibleBreakage() {
    }
}
