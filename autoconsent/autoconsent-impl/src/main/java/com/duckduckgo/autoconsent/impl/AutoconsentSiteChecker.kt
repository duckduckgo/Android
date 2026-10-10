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

package com.duckduckgo.autoconsent.impl

import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentExceptionsRepository
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoconsentSiteChecker {
    /**
     * @return `true` if the remote feature is on and [url] is not in the user allowlist or in any exception list.
     * It does not check the user setting.
     */
    fun isEnabledForSite(url: String): Boolean
}

@ContributesBinding(AppScope::class)
class RealAutoconsentSiteChecker @Inject constructor(
    private val autoconsentFeature: AutoconsentFeature,
    private val autoconsentExceptionsRepository: AutoconsentExceptionsRepository,
    private val userAllowlistRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : AutoconsentSiteChecker {

    override fun isEnabledForSite(url: String): Boolean {
        return autoconsentFeature.self().isEnabled() && !urlInUserAllowList(url) && !isAnException(url)
    }

    private fun urlInUserAllowList(url: String): Boolean {
        return try {
            userAllowlistRepository.isUrlInUserAllowList(url)
        } catch (e: Exception) {
            false
        }
    }

    private fun isAnException(url: String): Boolean {
        return autoconsentExceptionsRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) } ||
            unprotectedTemporary.isAnException(url)
    }
}
