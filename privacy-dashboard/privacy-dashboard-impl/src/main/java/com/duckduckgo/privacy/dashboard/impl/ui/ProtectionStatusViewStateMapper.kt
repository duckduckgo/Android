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
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.PrivacyFeatureName.ContentBlockingFeatureName
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.AllowedReasons
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.AllowedReasons.PROTECTIONS_DISABLED
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.CertificateViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.DetectedRequest
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.EntityViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.ProtectionStatusViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.PublicKeyViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Reason
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestDataViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.State.*
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.TrackerEventViewState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.TrackerViewState
import com.squareup.anvil.annotations.ContributesBinding
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import java.lang.Thread.State
import javax.inject.Inject

interface ProtectionStatusViewStateMapper {
    fun mapFromSite(site: Site): ProtectionStatusViewState
}

@ContributesBinding(AppScope::class)
class AppProtectionStatusViewStateMapper @Inject constructor(
    private val publicKeyInfoMapper: PublicKeyInfoMapper,
    private val contentBlocking: ContentBlocking,
    private val unprotectedTemporary: UnprotectedTemporary
) : ProtectionStatusViewStateMapper {

    override fun mapFromSite(site: Site): ProtectionStatusViewState {
        val enabledFeatures = mutableListOf<String>().apply {
            if (!contentBlocking.isAnException(site.url)) {
                add(ContentBlockingFeatureName.value)
            }
        }

        return ProtectionStatusViewState(
            allowlisted = site.userAllowList,
            denylisted = false,
            enabledFeatures = enabledFeatures,
            unprotectedTemporary = unprotectedTemporary.isAnException(site.url)
        )
    }
}
