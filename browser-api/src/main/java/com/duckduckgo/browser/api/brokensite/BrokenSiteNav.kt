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

package com.duckduckgo.browser.api.brokensite

import android.net.Uri
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.common.utils.baseHost

data class BrokenSiteData(
    val url: String,
    val blockedTrackers: String,
    val surrogates: String,
    val upgradedToHttps: Boolean,
    val urlParametersRemoved: Boolean,
    val consentManaged: Boolean,
    val consentOptOutFailed: Boolean,
    val consentSelfTestFailed: Boolean,
    val errorCodes: List<String>,
    val httpErrorCodes: String,
    val isDesktopMode: Boolean,
    val reportFlow: ReportFlow,
    val userRefreshCount: Int,
    val openerContext: BrokenSiteOpenerContext?,
    val jsPerformance: DoubleArray?,
) {
    enum class ReportFlow { MENU, DASHBOARD, TOGGLE_DASHBOARD, TOGGLE_MENU, RELOAD_THREE_TIMES_WITHIN_20_SECONDS }

    companion object {
        fun fromSite(site: Site?, reportFlow: ReportFlow): BrokenSiteData {
            val events = site?.trackingEvents
            val blockedTrackers = events?.filter { it.status == TrackerStatus.BLOCKED }
                ?.map { Uri.parse(it.trackerUrl).baseHost.orEmpty() }
                .orEmpty().distinct().joinToString(",")
            val errorCodes = site?.errorCodeEvents.orEmpty()
            val httErrorCodes = site?.httpErrorCodeEvents.orEmpty().distinct().joinToString(",")
            val upgradedHttps = site?.upgradedHttps ?: false
            val surrogates = site?.surrogates?.map { Uri.parse(it.name).baseHost }.orEmpty().distinct().joinToString(",")
            val url = site?.url.orEmpty()
            val urlParametersRemoved = site?.urlParametersRemoved ?: false
            val consentManaged = site?.consentManaged ?: false
            val consentOptOutFailed = site?.consentOptOutFailed ?: false
            val consentSelfTestFailed = site?.consentSelfTestFailed ?: false
            val isDesktopMode = site?.isDesktopMode ?: false
            val userRefreshCount = site?.realBrokenSiteContext?.userRefreshCount ?: 0
            val openerContext = site?.realBrokenSiteContext?.openerContext
            val jsPerformance = site?.realBrokenSiteContext?.jsPerformance
            return BrokenSiteData(
                url = url,
                blockedTrackers = blockedTrackers,
                surrogates = surrogates,
                upgradedToHttps = upgradedHttps,
                urlParametersRemoved = urlParametersRemoved,
                consentManaged = consentManaged,
                consentOptOutFailed = consentOptOutFailed,
                consentSelfTestFailed = consentSelfTestFailed,
                errorCodes = errorCodes,
                httpErrorCodes = httErrorCodes,
                isDesktopMode = isDesktopMode,
                reportFlow = reportFlow,
                userRefreshCount = userRefreshCount,
                openerContext = openerContext,
                jsPerformance = jsPerformance,
            )
        }
    }
}
