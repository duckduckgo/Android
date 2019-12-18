/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.trackerdetection.Client.Result
import com.duckduckgo.app.trackerdetection.model.DomainContainer

/**
 * Performs matching of the top level document url to the domains. This is commonly used for whitelisting where
 * the main site is whitelisted (not just individual trackers).
 */
class DocumentDomainClient(override val name: Client.ClientName, private val domains: List<DomainContainer>) : Client {

    override fun matches(url: String, documentUrl: String): Result {
        return Result(domains.any { sameOrSubdomain(documentUrl, it.domain) })
    }
}