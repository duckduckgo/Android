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

import android.net.http.SslCertificate
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.CertificateViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.EntityViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.PublicKeyViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteProtectionsViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.TrackerEventViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.TrackerViewState
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SiteProtectionsViewStateMapper {
    fun mapFromSite(site: Site): SiteProtectionsViewState
}

@ContributesBinding(AppScope::class)
class AppSiteProtectionsViewStateMapper @Inject constructor(
    private val publicKeyInfoMapper: PublicKeyInfoMapper
) : SiteProtectionsViewStateMapper {

    override fun mapFromSite(site: Site): SiteProtectionsViewState {
        val trackingEvents = getTrackingEventsFrom(site)

        val entityViewState = site.entity?.let {
            EntityViewState(
                displayName = it.displayName,
                prevalence = site.entity?.prevalence ?: 0.toDouble()
            )
        }

        return SiteProtectionsViewState(
            url = site.url,
            upgradedHttps = site.upgradedHttps,
            parentEntity = entityViewState,
            site = SiteViewState(
                url = site.url,
                domain = site.domain!!,
                trackersUrls = trackingEvents.trackerUrls,
                allowlisted = site.userAllowList
            ),
            trackers = trackingEvents.trackerEvents,
            trackerBlocked = trackingEvents.blockedTrackerEvents,
            secCertificateViewModels = site.certificate?.let { listOf(it.map()) } ?: emptyList()
        )
    }

    private fun getTrackingEventsFrom(
        site: Site
    ): PrivacyDashboardSiteTrackingEvents {

        val trackingEvents: MutableMap<String, TrackerViewState> = mutableMapOf()
        val trackerUrls: MutableSet<String> = mutableSetOf()

        site.trackingEvents.forEach {
            val entity = it.entity?.let { it } ?: return@forEach

            trackerUrls.add(it.trackerUrl)

            val trackerViewState: TrackerViewState = trackingEvents[entity.displayName]?.let { trackerViewState ->
                val urls = trackerViewState.urls + Pair(
                    it.trackerUrl,
                    TrackerEventViewState(
                        isBlocked = it.blocked,
                        reason = DEFAULT_VIEW_STATE_TRACKER_REASON,
                        categories = it.categories?.toSet() ?: emptySet()
                    )
                )
                trackerViewState.copy(
                    urls = urls,
                    count = trackerViewState.count + 1
                )
            } ?: TrackerViewState(
                displayName = entity.displayName,
                prevalence = entity.prevalence,
                urls = mutableMapOf(
                    it.trackerUrl to TrackerEventViewState(
                        isBlocked = it.blocked,
                        reason = DEFAULT_VIEW_STATE_TRACKER_REASON,
                        categories = it.categories?.toSet() ?: emptySet()
                    )
                ),
                count = 1,
                type = "here goes type" // TODO: ????
            )

            trackingEvents[entity.displayName] = trackerViewState
        }

        val trackersBlocked = getBlockedTrackingEvents(site)

        return PrivacyDashboardSiteTrackingEvents(
            trackerEvents = trackingEvents,
            trackerUrls = trackerUrls,
            blockedTrackerEvents = trackersBlocked
        )
    }

    private fun getBlockedTrackingEvents(
        site: Site
    ): MutableMap<String, TrackerViewState> {

        val trackersBlocked: MutableMap<String, TrackerViewState> = mutableMapOf()

        site.trackingEvents.filter { it.blocked }.forEach {
            val entity = it.entity?.let { it } ?: return@forEach

            val trackerViewState: TrackerViewState = trackersBlocked[entity.displayName]?.let { trackerViewState ->
                val urls = trackerViewState.urls + Pair(
                    it.trackerUrl,
                    TrackerEventViewState(
                        isBlocked = it.blocked,
                        reason = "first party",
                        categories = it.categories?.toSet() ?: emptySet()
                    )
                )
                trackerViewState.copy(
                    urls = urls,
                    count = trackerViewState.count + 1
                )
            } ?: TrackerViewState(
                displayName = entity.displayName,
                prevalence = entity.prevalence,
                urls = mutableMapOf(
                    it.trackerUrl to TrackerEventViewState(
                        isBlocked = it.blocked,
                        reason = DEFAULT_VIEW_STATE_TRACKER_REASON,
                        categories = it.categories?.toSet() ?: emptySet()
                    )
                ),
                count = 1,
                type = DEFAULT_VIEW_STATE_TRACKER_TYPE
            )

            trackersBlocked[entity.displayName] = trackerViewState
        }

        return trackersBlocked
    }

    private fun SslCertificate.map(): CertificateViewState {
        val publicKeyInfo = publicKeyInfoMapper.mapFrom(this)

        return CertificateViewState(
            commonName = this.issuedTo.cName,
            summary = this.issuedTo.cName,
            publicKey = publicKeyInfo?.let {
                PublicKeyViewState(
                    blockSize = publicKeyInfo.blockSize,
                    bitSize = publicKeyInfo.bitSize,
                    canEncrypt = publicKeyInfo.canEncrypt,
                    canSign = publicKeyInfo.canSign,
                    canDerive = publicKeyInfo.canDerive,
                    canUnwrap = publicKeyInfo.canUnwrap,
                    canWrap = publicKeyInfo.canWrap,
                    canDecrypt = publicKeyInfo.canDecrypt,
                    canVerify = publicKeyInfo.canVerify,
                    effectiveSize = publicKeyInfo.effectiveSize,
                    isPermanent = publicKeyInfo.isPermanent,
                    type = publicKeyInfo.type,
                    externalRepresentation = publicKeyInfo.externalRepresentation,
                    keyId = publicKeyInfo.keyId
                )
            }
        )
    }

    private data class PrivacyDashboardSiteTrackingEvents(
        val trackerEvents: MutableMap<String, TrackerViewState>,
        val trackerUrls: MutableSet<String>,
        val blockedTrackerEvents: MutableMap<String, TrackerViewState>
    )

    companion object {
        const val DEFAULT_VIEW_STATE_TRACKER_TYPE = "type"
        const val DEFAULT_VIEW_STATE_TRACKER_REASON = "reason"
    }
}
