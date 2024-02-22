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

    private val duckDuckGoSites = listOf("duckduckgo.com")

    override fun isDuckDuckGoSite(url: String): Boolean {
        return duckDuckGoSites.any { UriString.sameOrSubdomain(url, it) }
    }

    override fun isException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || userAgentRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }
}
