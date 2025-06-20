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

package com.duckduckgo.brokensite.api

import com.duckduckgo.feature.toggles.api.Toggle

interface BrokenSiteSender {
    fun submitBrokenSiteFeedback(brokenSite: BrokenSite, toggle: Boolean)
}

data class BrokenSite(
    val category: String?,
    val description: String?,
    val siteUrl: String,
    val upgradeHttps: Boolean,
    val blockedTrackers: String,
    val surrogates: String,
    val siteType: String,
    val urlParametersRemoved: Boolean,
    val consentManaged: Boolean,
    val consentOptOutFailed: Boolean,
    val consentSelfTestFailed: Boolean,
    val errorCodes: String,
    val httpErrorCodes: String,
    val loginSite: String?,
    val reportFlow: ReportFlow?,
    val userRefreshCount: Int,
    val openerContext: String?,
    val jsPerformance: List<Double>?,
    val contentScopeExperiments: List<Toggle>?,
    val debugFlags: List<String>?,
) {
    companion object {
        const val SITE_TYPE_DESKTOP = "desktop"
        const val SITE_TYPE_MOBILE = "mobile"
    }
}

enum class ReportFlow { DASHBOARD, MENU, TOGGLE_DASHBOARD, TOGGLE_MENU, RELOAD_THREE_TIMES_WITHIN_20_SECONDS }
