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

package com.duckduckgo.app.global.model

import android.net.Uri
import android.net.http.SslCertificate
import androidx.core.net.toUri
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.domain

interface Site {

    /*
     * The current url for this site. This is sometimes different to the url originally
     * loaded as the url may change while loading a site
     */
    var url: String

    /*
     * The current uri for this site. This is sometimes different to the url originally
     * loaded as the url may change while loading a site
     */
    val uri: Uri?

    var title: String?
    val https: HttpsStatus
    var hasHttpResources: Boolean
    var upgradedHttps: Boolean
    var userAllowList: Boolean
    var sslError: Boolean
    var isExternalLaunch: Boolean

    val entity: Entity?
    var certificate: SslCertificate?
    val trackingEvents: List<TrackingEvent>
    val errorCodeEvents: List<String>
    val httpErrorCodeEvents: List<Int>
    val trackerCount: Int
    val otherDomainsLoadedCount: Int
    val specialDomainsLoadedCount: Int
    val majorNetworkCount: Int
    val allTrackersBlocked: Boolean
    val surrogates: List<SurrogateResponse>
    fun trackerDetected(event: TrackingEvent)
    fun onHttpErrorDetected(errorCode: Int)
    fun onErrorDetected(error: String)
    fun resetErrors()
    fun updatePrivacyData(sitePrivacyData: SitePrivacyData)
    fun surrogateDetected(surrogate: SurrogateResponse)

    fun privacyProtection(): PrivacyShield
    fun resetTrackingEvents()

    var urlParametersRemoved: Boolean
    var consentManaged: Boolean
    var consentOptOutFailed: Boolean
    var consentSelfTestFailed: Boolean
    var consentCosmeticHide: Boolean?
    var isDesktopMode: Boolean
    var nextUrl: String

    val realBrokenSiteContext: BrokenSiteContext
}

fun Site.orderedTrackerBlockedEntities(): List<Entity> = trackingEvents
    .filter { it.status == TrackerStatus.BLOCKED }
    .mapNotNull { it.entity }
    .filter { it.displayName.isNotBlank() }
    .sortedByDescending { it.prevalence }

fun Site.domainMatchesUrl(matchingUrl: String): Boolean {
    return uri?.baseHost == matchingUrl.toUri().baseHost
}

fun Site.domainMatchesUrl(matchingUrl: Uri): Boolean {
    // TODO (cbarreiro) can we get rid of baseHost for the Uri as well?
    return uri?.baseHost == matchingUrl.host
}

val Site.domain get() = uri?.domain()
val Site.baseHost get() = uri?.baseHost
