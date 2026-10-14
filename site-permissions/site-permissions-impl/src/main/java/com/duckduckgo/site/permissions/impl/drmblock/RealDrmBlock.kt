/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.drmblock

import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.site.permissions.impl.feature.DrmPolicyFeature
import com.duckduckgo.site.permissions.impl.feature.isCentralPolicyEnabled
import com.duckduckgo.site.permissions.impl.isSiteUnprotectedByUser
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDrmBlock @Inject constructor(
    private val drmBlockFeature: DrmBlockFeature,
    private val drmBlockRepository: DrmBlockRepository,
    private val userAllowListRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val drmPolicyFeature: DrmPolicyFeature,
) : DrmBlock {

    override fun isDrmBlockedForUrl(url: String): Boolean {
        val uri = url.toUri()
        if (!drmBlockFeature.self().isEnabled()) return false

        // The central policy widens both the list match and the protections-off check. The legacy path has to
        // stay as it is on develop, so that turning the flag off during the rollout restores today's behaviour.
        return if (drmPolicyFeature.isCentralPolicyEnabled()) {
            drmBlockRepository.exceptions.any { sameOrSubdomain(url, it.domain) } &&
                !userAllowListRepository.isSiteUnprotectedByUser(uri) &&
                !unprotectedTemporary.isAnException(uri.toString())
        } else {
            drmBlockRepository.exceptions.firstOrNull { it.domain == uri.baseHost } != null &&
                !userAllowListRepository.isUriInUserAllowList(uri) &&
                !unprotectedTemporary.isAnException(uri.toString())
        }
    }
}
