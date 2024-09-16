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
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDrmBlock @Inject constructor(
    private val drmBlockFeature: DrmBlockFeature,
    private val drmBlockRepository: DrmBlockRepository,
    private val userAllowListRepository: UserAllowListRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : DrmBlock {

    override fun isDrmBlockedForUrl(url: String): Boolean {
        val uri = url.toUri()
        return drmBlockFeature.self().isEnabled() &&
            domainsThatBlockDrm(uri.baseHost) &&
            !userAllowListRepository.isUriInUserAllowList(uri) &&
            !unprotectedTemporary.isAnException(uri.toString())
    }

    private fun domainsThatBlockDrm(host: String?): Boolean {
        return drmBlockRepository.exceptions.firstOrNull { it.domain == host } != null
    }
}
