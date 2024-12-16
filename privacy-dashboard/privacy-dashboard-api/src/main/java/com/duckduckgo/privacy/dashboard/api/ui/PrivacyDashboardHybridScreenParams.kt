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

package com.duckduckgo.privacy.dashboard.api.ui

import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.privacy.dashboard.api.ui.DashboardOpener.NONE

enum class DashboardOpener(val value: String) {
    MENU("menu"),
    DASHBOARD("dashboard"),
    NONE(""),
}

sealed class PrivacyDashboardHybridScreenParams : GlobalActivityStarter.ActivityParams {

    abstract val tabId: String
    abstract val opener: DashboardOpener

    /**
     * Use this parameter to launch the privacy dashboard hybrid activity with the given tabId
     * @param tabId The tab ID
     */
    data class PrivacyDashboardPrimaryScreen(
        override val tabId: String,
        override val opener: DashboardOpener = NONE,
    ) : PrivacyDashboardHybridScreenParams()

    /**
     * Use this parameter to launch the site breakage reporting form.
     * @param tabId The tab ID
     */
    data class BrokenSiteForm(
        override val tabId: String,
        override val opener: DashboardOpener = NONE,
        val reportFlow: BrokenSiteFormReportFlow,
    ) : PrivacyDashboardHybridScreenParams() {
        enum class BrokenSiteFormReportFlow {
            MENU,
            RELOAD_THREE_TIMES_WITHIN_20_SECONDS,
        }
    }

    /**
     * Use this parameter to launch the toggle report form.
     * @param tabId The tab ID
     */
    data class PrivacyDashboardToggleReportScreen(
        override val tabId: String,
        override val opener: DashboardOpener = NONE,
    ) : PrivacyDashboardHybridScreenParams()
}

object PrivacyDashboardHybridScreenResult {
    const val REPORT_SUBMITTED = 1
}
