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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupExperimentExternalPixels
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.CONTROL
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.TEST
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupPixelName.*
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
class PrivacyProtectionsPopupExperimentExternalPixelsImpl @Inject constructor(
    private val dataStore: PrivacyProtectionsPopupDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val pixelSender: Pixel,
) : PrivacyProtectionsPopupExperimentExternalPixels {

    override suspend fun getPixelParams(): Map<String, String> {
        val experimentVariant = dataStore.getExperimentVariant()

        return if (experimentVariant != null) {
            val paramValue = when (experimentVariant) {
                CONTROL -> "control"
                TEST -> "test"
            }
            mapOf(PARAM_EXPERIMENT_VARIANT to paramValue)
        } else {
            emptyMap()
        }
    }

    override fun tryReportPrivacyDashboardOpened() {
        fireIfInExperiment(PRIVACY_DASHBOARD_LAUNCHED_UNIQUE)
    }

    override fun tryReportProtectionsToggledFromPrivacyDashboard(protectionsEnabled: Boolean) {
        fireIfInExperiment(
            if (protectionsEnabled) PRIVACY_DASHBOARD_ALLOWLIST_REMOVE_UNIQUE else PRIVACY_DASHBOARD_ALLOWLIST_ADD_UNIQUE,
        )
    }

    override fun tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled: Boolean) {
        fireIfInExperiment(
            if (protectionsEnabled) BROWSER_MENU_ALLOWLIST_REMOVE_UNIQUE else BROWSER_MENU_ALLOWLIST_ADD_UNIQUE,
        )
    }

    override fun tryReportProtectionsToggledFromBrokenSiteReport(protectionsEnabled: Boolean) {
        fireIfInExperiment(
            if (protectionsEnabled) BROKEN_SITE_ALLOWLIST_REMOVE_UNIQUE else BROKEN_SITE_ALLOWLIST_ADD_UNIQUE,
        )
    }

    private fun fireIfInExperiment(pixel: PrivacyProtectionsPopupPixelName) {
        appCoroutineScope.launch {
            if (dataStore.getExperimentVariant() != null) {
                pixelSender.fire(
                    pixel = pixel,
                    parameters = getPixelParams(),
                    type = pixel.type,
                )
            }
        }
    }

    private companion object {
        const val PARAM_EXPERIMENT_VARIANT = "privacy_protections_popup_experiment_variant"
    }
}
