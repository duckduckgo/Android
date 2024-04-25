/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.user.agent.impl

import com.duckduckgo.app.browser.UriString
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.user.agent.store.UserAgentRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface UserAgent {
    /**
     * Determines if a [url] should use the "legacy" DuckDuckGo user agent.
     * The legacy user agent contains the DuckDuckGo application and Version components.
     * @return true if the [url] is in the local legacy sites list, otherwise false.
     */
    fun useLegacyUserAgent(url: String): Boolean
    fun isException(url: String): Boolean
}

@ContributesBinding(AppScope::class)
class RealUserAgent @Inject constructor(
    private val userAgentRepository: UserAgentRepository,
    private val unprotectedTemporary: UnprotectedTemporary,
) : UserAgent {

    private val legacySites = listOf("duckduckgo.com", "ddg.gg", "duck.com", "duck.it")

    override fun useLegacyUserAgent(url: String): Boolean {
        return legacySites.any { UriString.sameOrSubdomain(url, it) }
    }

    override fun isException(url: String): Boolean {
        return unprotectedTemporary.isAnException(url) || userAgentRepository.exceptions.any { UriString.sameOrSubdomain(url, it.domain) }
    }
}
