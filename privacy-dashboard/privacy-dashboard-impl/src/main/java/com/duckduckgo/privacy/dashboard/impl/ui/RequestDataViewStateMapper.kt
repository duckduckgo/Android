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
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.AD_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.BLOCKED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SAME_ENTITY_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SITE_BREAKAGE_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.USER_ALLOWED
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.AllowedReasons
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.CertificateViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.DetectedRequest
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.PublicKeyViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Reason
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.State.*
import com.squareup.anvil.annotations.ContributesBinding
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import javax.inject.Inject

interface RequestDataViewStateMapper {
    fun mapFromSite(site: Site): RequestDataViewState
}

@ContributesBinding(AppScope::class)
class AppSiteRequestDataViewStateMapper @Inject constructor(
    private val publicKeyInfoMapper: PublicKeyInfoMapper
) : RequestDataViewStateMapper {

    private val publicSuffixDatabase = PublicSuffixDatabase()

    override fun mapFromSite(site: Site): RequestDataViewState {
        val installedSurrogates = emptyList<String>()
        val allowedCategories = listOf(
            "Analytics",
            "Advertising",
            "Social Network",
            "Content Delivery",
            "Embedded Content"
        );
        val requests: List<DetectedRequest> = site.trackingEvents.map { it ->
            val entity: Entity = if (it.entity == null) return@map null else it.entity!!

            DetectedRequest(
                category = it.categories?.firstOrNull { category -> allowedCategories.contains(category) },
                url = it.trackerUrl,
                eTLDplus1 = toTldPlusOne(it.trackerUrl),
                pageUrl = it.documentUrl,
                entityName = entity.displayName,
                ownerName = entity.name,
                prevalence = entity.prevalence,
                state = it.status.mapToViewState()
            )
        }.filterNotNull()

        return RequestDataViewState(
            installedSurrogates = installedSurrogates,
            requests = requests
        )
    }

    private fun TrackerStatus.mapToViewState(): PrivacyDashboardHybridViewModel.State {
        return when(this) {
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
}
