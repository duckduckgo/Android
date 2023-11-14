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

package com.duckduckgo.privacy.config.impl.features.useragent

import com.duckduckgo.common.utils.UriString
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.DefaultPolicy
import com.duckduckgo.privacy.config.api.DefaultPolicy.CLOSEST
import com.duckduckgo.privacy.config.api.DefaultPolicy.DDG
import com.duckduckgo.privacy.config.api.DefaultPolicy.DDG_FIXED
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.api.UserAgent
import com.duckduckgo.privacy.config.store.features.useragent.UserAgentRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealUserAgent @Inject constructor(
    private val userAgentRepository: UserAgentRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : UserAgent {

    override fun isAnApplicationException(url: String): Boolean {
        return userAgentRepository.omitApplicationExceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun isAVersionException(url: String): Boolean {
        return userAgentRepository.omitVersionExceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun isADefaultException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || userAgentRepository.defaultExceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun defaultPolicy(): DefaultPolicy {
        return when (userAgentRepository.defaultPolicy) {
            "ddg" -> { DDG }
            "ddgFixed" -> { DDG_FIXED }
            "closest" -> { CLOSEST }
            else -> { DDG }
        }
    }

    override fun isADdgDefaultSite(url: String): Boolean {
        return userAgentRepository.ddgDefaultSites.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun isADdgFixedSite(url: String): Boolean {
        return userAgentRepository.ddgFixedSites.any { UriString.sameOrSubdomain(url, it.domain) }
    }

    override fun closestUserAgentEnabled(): Boolean {
        return userAgentRepository.closestUserAgentState
    }

    override fun ddgFixedUserAgentEnabled(): Boolean {
        return userAgentRepository.ddgFixedUserAgentState
    }

    override fun isClosestUserAgentVersion(version: String): Boolean {
        return userAgentRepository.closestUserAgentVersions.contains(version)
    }

    override fun isDdgFixedUserAgentVersion(version: String): Boolean {
        return userAgentRepository.ddgFixedUserAgentVersions.contains(version)
    }
}
