/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.brokensite

import android.net.Uri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.model.Site

data class BrokenSiteData(
    val url: String,
    val blockedTrackers: String,
    val surrogates: String,
    val upgradedToHttps: Boolean,
    val urlParametersRemoved: Boolean
) {

    companion object {
        fun fromSite(site: Site?): BrokenSiteData {
            val events = site?.trackingEvents
            val blockedTrackers = events?.map { Uri.parse(it.trackerUrl).host }.orEmpty().distinct().joinToString(",")
            val upgradedHttps = site?.upgradedHttps ?: false
            val surrogates = site?.surrogates?.map { Uri.parse(it.name).baseHost }.orEmpty().distinct().joinToString(",")
            val url = site?.url.orEmpty()
            val urlParametersRemoved = site?.urlParametersRemoved ?: false
            return BrokenSiteData(url, blockedTrackers, surrogates, upgradedHttps, urlParametersRemoved)
        }
    }
}
