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

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.TrackerStatus

interface BrokenSiteNav {
    fun navigate(context: Context, data: BrokenSiteData): Intent
}

data class BrokenSiteData(
    val url: String,
    val blockedTrackers: String,
    val surrogates: String,
    val upgradedToHttps: Boolean,
    val urlParametersRemoved: Boolean,
    val consentManaged: Boolean,
    val consentOptOutFailed: Boolean,
    val consentSelfTestFailed: Boolean,
    val params: List<String>,
    val errorCodes: String,
    val httpErrorCodes: String,
) {
    companion object {
        fun fromSite(site: Site?, params: List<String> = emptyList()): BrokenSiteData {
            val events = site?.trackingEvents
            val blockedTrackers = events?.filter { it.status == TrackerStatus.BLOCKED }
                ?.map { Uri.parse(it.trackerUrl).baseHost.orEmpty() }
                .orEmpty().distinct().joinToString(",")
            val errorCodes = site?.errorCodeEvents.orEmpty().distinct().joinToString(",")
            val httErrorCodes = site?.httpErrorCodeEvents.orEmpty().distinct().joinToString(",")
            val upgradedHttps = site?.upgradedHttps ?: false
            val surrogates = site?.surrogates?.map { Uri.parse(it.name).baseHost }.orEmpty().distinct().joinToString(",")
            val url = site?.url.orEmpty()
            val urlParametersRemoved = site?.urlParametersRemoved ?: false
            val consentManaged = site?.consentManaged ?: false
            val consentOptOutFailed = site?.consentOptOutFailed ?: false
            val consentSelfTestFailed = site?.consentSelfTestFailed ?: false
            return BrokenSiteData(
                url = url,
                blockedTrackers = blockedTrackers,
                surrogates = surrogates,
                upgradedToHttps = upgradedHttps,
                urlParametersRemoved = urlParametersRemoved,
                consentManaged = consentManaged,
                consentOptOutFailed = consentOptOutFailed,
                consentSelfTestFailed = consentSelfTestFailed,
                params = params,
                errorCodes = errorCodes,
                httpErrorCodes = httErrorCodes,
            )
        }
    }
}
