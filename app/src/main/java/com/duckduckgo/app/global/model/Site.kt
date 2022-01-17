/*
 * Copyright (c) 2018 DuckDuckGo
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
import androidx.core.net.toUri
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.domain
import com.duckduckgo.app.global.model.SiteFactory.SitePrivacyData
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

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

    val privacyPractices: PrivacyPractices.Practices
    val entity: Entity?
    val trackingEvents: List<TrackingEvent>
    val trackerCount: Int
    val majorNetworkCount: Int
    val allTrackersBlocked: Boolean
    val surrogates: List<SurrogateResponse>
    fun trackerDetected(event: TrackingEvent)
    fun updatePrivacyData(sitePrivacyData: SitePrivacyData)
    fun surrogateDetected(surrogate: SurrogateResponse)

    fun calculateGrades(): SiteGrades

    data class SiteGrades(
        val grade: PrivacyGrade,
        val improvedGrade: PrivacyGrade
    )
}

fun Site.orderedTrackingEntities(): List<Entity> = trackingEvents
    .mapNotNull { it.entity }
    .filter { it.displayName.isNotBlank() }
    .sortedByDescending { it.prevalence }

fun Site.domainMatchesUrl(matchingUrl: String): Boolean {
    return uri?.baseHost == matchingUrl.toUri().baseHost
}

val Site.domain get() = uri?.domain()
