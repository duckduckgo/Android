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

package com.duckduckgo.privacyprotectionspopup.api

interface PrivacyProtectionsPopupExperimentExternalPixels {
    /**
     * Returns parameters to annotate pixels with the popup experiment variant.
     */
    suspend fun getPixelParams(): Map<String, String>

    /**
     * This method should be invoked whenever the user enters the Privacy Dashboard screen.
     *
     * If the user is enrolled in the popup experiment, calling this method will fire a unique pixel.
     */
    fun tryReportPrivacyDashboardOpened()

    /**
     * This method should be invoked whenever the user toggles privacy protections on the Privacy Dashboard screen.
     *
     * If the user is enrolled in the popup experiment, calling this method will fire a unique pixel.
     */
    fun tryReportProtectionsToggledFromPrivacyDashboard(protectionsEnabled: Boolean)

    /**
     * This method should be invoked whenever the user toggles privacy protections using the options menu on the Browser screen.
     *
     * If the user is enrolled in the popup experiment, calling this method will fire a unique pixel.
     */
    fun tryReportProtectionsToggledFromBrowserMenu(protectionsEnabled: Boolean)

    /**
     * This method should be invoked whenever the user toggles privacy protections on the Broken Site screen.
     *
     * If the user is enrolled in the popup experiment, calling this method will fire a unique pixel.
     */
    fun tryReportProtectionsToggledFromBrokenSiteReport(protectionsEnabled: Boolean)
}
