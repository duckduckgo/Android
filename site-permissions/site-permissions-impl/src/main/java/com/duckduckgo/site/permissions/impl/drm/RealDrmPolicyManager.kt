/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.drm

import androidx.core.net.toUri
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Drm
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.impl.drmblock.DrmBlock
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionAskSettingType
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDrmPolicyManager @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val drmSessionStore: DrmSessionStore,
    private val drmBlock: DrmBlock,
    private val drm: Drm,
    private val userAllowListRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : DrmPolicyManager {

    override suspend fun decide(url: String, tabId: String?): DrmPolicyDecision {
        val domain = url.extractDomain() ?: url
        val uri = url.toUri()
        // Settings are keyed on the host as typed, so example.com and www.example.com are separate
        // rows, while the allow list matches both
        val siteSetting = listOfNotNull(domain, uri.baseHost, uri.baseHost?.let { "www.$it" })
            .distinct()
            .firstNotNullOfOrNull { sitePermissionsRepository.getSitePermissionsForWebsite(it)?.askDrmSetting }
            ?.let { setting -> runCatching { SitePermissionAskSettingType.valueOf(setting) }.getOrNull() }

        return DrmPolicyContext(
            isGlobalAskEnabled = sitePermissionsRepository.askDrmEnabled,
            siteSetting = siteSetting,
            sessionChoice = tabId?.let { drmSessionStore.get(it, domain) },
            isBlockedByBlockList = drmBlock.isDrmBlockedForUrl(url),
            isSiteUnprotected = userAllowListRepository.isUriInUserAllowList(uri) || unprotectedTemporary.isAnException(url),
            isAllowedByAllowList = drm.isDrmAllowedForUrl(url),
        ).evaluate()
    }
}
