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

package com.duckduckgo.app.browser.certificates

import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

interface BypassedSSLCertificatesRepository {

    fun add(domain: String)

    fun contains(domain: String): Boolean
}

@SingleInstanceIn(AppScope::class)
class RealBypassedSSLCertificatesRepository @Inject constructor() : BypassedSSLCertificatesRepository {

    /**
     * Using a LinkedHashSet to:
     * 1. Prevent duplicate entries (Set behavior)
     * 2. Maintain insertion order for eviction (LinkedHashSet behavior)
     *
     * When the maximum size is reached, the oldest entries are removed to prevent unbounded memory growth.
     */
    private val trustedSites: MutableSet<String> = linkedSetOf()

    @Synchronized
    override fun add(domain: String) {
        // Remove oldest entries if we've reached the maximum size
        while (trustedSites.size >= MAX_TRUSTED_SITES) {
            trustedSites.iterator().next().let { oldest ->
                trustedSites.remove(oldest)
            }
        }
        trustedSites.add(domain)
    }

    @Synchronized
    override fun contains(domain: String): Boolean {
        return trustedSites.contains(domain)
    }

    companion object {
        // Maximum number of bypassed SSL certificate domains to store
        // This prevents unbounded memory growth while allowing reasonable usage
        private const val MAX_TRUSTED_SITES = 100
    }
}
