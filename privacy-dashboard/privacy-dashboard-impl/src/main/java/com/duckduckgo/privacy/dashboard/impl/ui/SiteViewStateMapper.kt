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
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.SiteViewState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SiteViewStateMapper {
    fun mapFromSite(site: Site): SiteViewState
}

@ContributesBinding(AppScope::class)
class AppSiteViewStateMapper @Inject constructor(
    private val publicKeyInfoMapper: PublicKeyInfoMapper,
) : SiteViewStateMapper {

    override fun mapFromSite(site: Site): SiteViewState {
        val entityViewState = site.entity?.let {
            EntityViewState(
                displayName = it.displayName,
                prevalence = site.entity?.prevalence ?: 0.toDouble(),
            )
        }

        return SiteViewState(
            url = site.url,
            domain = site.domain,
            upgradedHttps = site.upgradedHttps,
            parentEntity = entityViewState,
            secCertificateViewModels = mapCertificate(site),
        )
    }

    private fun mapCertificate(site: Site): List<CertificateViewState> {
        return if (site.sslError) {
            emptyList()
        } else {
            site.certificate?.let { listOf(it.map()) } ?: emptyList()
        }
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
                    keyId = publicKeyInfo.keyId,
                )
            },
        )
    }
}
