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

package com.duckduckgo.privacy.dashboard.impl.ui

import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.AD_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.BLOCKED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SAME_ENTITY_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SITE_BREAKAGE_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.USER_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.withScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.AllowedReasons
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.DetectedRequest
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Reason
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Allowed
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Blocked
import com.squareup.anvil.annotations.ContributesBinding
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import javax.inject.Inject

interface RequestDataViewStateMapper {
    fun mapFromSite(site: Site): RequestDataViewState
}

@ContributesBinding(AppScope::class)
class AppSiteRequestDataViewStateMapper @Inject constructor() : RequestDataViewStateMapper {

    private val publicSuffixDatabase = PublicSuffixDatabase()
    private val allowedCategories = listOf(
        "Analytics",
        "Advertising",
        "Social Network",
        "Content Delivery",
        "Embedded Content",
    )

    override fun mapFromSite(site: Site): RequestDataViewState {
        val installedSurrogates = mutableListOf<String>()

        val uniqueEntityDomainState = mutableMapOf<String, List<TrackerStatus>>()
        val requests: List<DetectedRequest> = site.trackingEvents.map {
            val withSchemeTrackerUrl = kotlin.runCatching { it.trackerUrl.toUri().withScheme().toString() }.getOrNull() ?: return@map null
            val trackerEvent = it.copy(trackerUrl = withSchemeTrackerUrl)

            if (trackerEvent.surrogateId?.isNotEmpty() == true) {
                installedSurrogates.add(trackerEvent.trackerUrl)
            }

            if (uniqueEntityDomainState.shouldSkipEvent(trackerEvent.entity, trackerEvent)) return@map null

            DetectedRequest(
                category = trackerEvent.categories?.firstOrNull { category -> allowedCategories.contains(category) },
                url = trackerEvent.trackerUrl,
                eTLDplus1 = toTldPlusOne(trackerEvent.trackerUrl),
                pageUrl = trackerEvent.documentUrl,
                entityName = trackerEvent.entity?.displayName,
                ownerName = trackerEvent.entity?.name,
                prevalence = trackerEvent.entity?.prevalence,
                state = trackerEvent.status.mapToViewState(),
            )
        }.filterNotNull()

        return RequestDataViewState(
            installedSurrogates = installedSurrogates,
            requests = requests,
        )
    }

    private fun MutableMap<String, List<TrackerStatus>>.shouldSkipEvent(entity: Entity?, trackerEvent: TrackingEvent): Boolean {
        val entityName = entity?.displayName
        val trackerDomain = trackerEvent.trackerUrl.extractDomain() ?: return true
        val trackerStatus = trackerEvent.status
        val hash = "$entityName$trackerDomain"

        val mappedTrackerEvent = this[hash]
        if (mappedTrackerEvent == null) {
            this[hash] = listOf(trackerStatus)
        } else {
            if (mappedTrackerEvent.contains(trackerStatus)) {
                return true
            } else {
                this[hash] = this[hash]!! + listOf(trackerStatus)
            }
        }

        return false
    }

    private fun TrackerStatus.mapToViewState(): PrivacyDashboardHybridViewModel.RequestState {
        return when (this) {
            BLOCKED -> Blocked()
            USER_ALLOWED -> Allowed(allowed = Reason(reason = AllowedReasons.PROTECTIONS_DISABLED.value))
            AD_ALLOWED -> Allowed(allowed = Reason(reason = AllowedReasons.AD_CLICK_ATTRIBUTION.value))
            SITE_BREAKAGE_ALLOWED -> Allowed(allowed = Reason(reason = AllowedReasons.RULE_EXCEPTION.value))
            SAME_ENTITY_ALLOWED -> Allowed(allowed = Reason(reason = AllowedReasons.OWNED_BY_FIRST_PARTY.value))
            ALLOWED -> Allowed(allowed = Reason(reason = AllowedReasons.OTHER_THIRD_PARTY_REQUEST.value))
        }
    }

    private fun toTldPlusOne(url: String): String? {
        val urlAdDomain = UriString.host(url)
        if (urlAdDomain.isNullOrEmpty()) return urlAdDomain
        return kotlin.runCatching { publicSuffixDatabase.getEffectiveTldPlusOne(urlAdDomain) }.getOrNull()
    }
}
