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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentPixelParamsProvider
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.DO_NOT_SHOW_AGAIN_CLICKED
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.EXPERIMENT_VARIANT_ASSIGNED
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.POPUP_DISMISSED_VIA_BUTTON
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.POPUP_DISMISSED_VIA_CLICK_OUTSIDE
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.POPUP_TRIGGERED
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.PRIVACY_DASHBOARD_OPENED
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.PRIVACY_DASHBOARD_OPENED_UNIQUE
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.PROTECTIONS_DISABLED
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.PROTECTIONS_DISABLED_UNIQUE
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.Params.PARAM_POPUP_TRIGGER_COUNT
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface PrivacyProtectionsPopupPixels {
    fun reportExperimentVariantAssigned()
    fun reportPopupTriggered()
    fun reportProtectionsDisabled()
    fun reportPrivacyDashboardOpened()
    fun reportPopupDismissedViaButton()
    fun reportPopupDismissedViaClickOutside()
    fun reportDoNotShowAgainClicked()
}

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupPixelsImpl @Inject constructor(
    private val pixelSender: Pixel,
    private val paramsProvider: PrivacyProtectionsPopupExperimentPixelParamsProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dataStore: PrivacyProtectionsPopupDataStore,
) : PrivacyProtectionsPopupPixels {

    override fun reportExperimentVariantAssigned() {
        appCoroutineScope.launch {
            fire(EXPERIMENT_VARIANT_ASSIGNED)
        }
    }

    override fun reportPopupTriggered() {
        appCoroutineScope.launch {
            fire(POPUP_TRIGGERED)
        }
    }

    override fun reportProtectionsDisabled() {
        appCoroutineScope.launch {
            fire(PROTECTIONS_DISABLED)
            fire(PROTECTIONS_DISABLED_UNIQUE)
        }
    }

    override fun reportPrivacyDashboardOpened() {
        appCoroutineScope.launch {
            fire(PRIVACY_DASHBOARD_OPENED)
            fire(PRIVACY_DASHBOARD_OPENED_UNIQUE)
        }
    }

    override fun reportPopupDismissedViaButton() {
        appCoroutineScope.launch {
            fire(POPUP_DISMISSED_VIA_BUTTON)
        }
    }

    override fun reportPopupDismissedViaClickOutside() {
        appCoroutineScope.launch {
            fire(POPUP_DISMISSED_VIA_CLICK_OUTSIDE)
        }
    }

    override fun reportDoNotShowAgainClicked() {
        appCoroutineScope.launch {
            val params = mapOf(PARAM_POPUP_TRIGGER_COUNT to dataStore.getPopupTriggerCount().toString())
            fire(DO_NOT_SHOW_AGAIN_CLICKED, params)
        }
    }

    private suspend fun fire(
        pixel: PrivacyProtectionsPopupPixelName,
        params: Map<String, String> = emptyMap(),
    ) {
        pixelSender.fire(
            pixel = pixel,
            parameters = params + paramsProvider.getPixelParams(),
            type = pixel.type,
        )
    }
}
