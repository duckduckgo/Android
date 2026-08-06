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
package com.duckduckgo.feature.toggles.api

import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxUrlResolver

data class FeatureException(val domain: String, val reason: String?) {

    @Transient
    private var resolved: DaxDomain? = null

    /**
     * The normalized [DaxDomain] for this exception, or null when a provider hasn't been installed
     * yet or [domain] has no resolvable host.
     *
     * Resolved once and pinned: the result is a pure function of the immutable [domain] string, so
     * caching it is as safe as caching [domain] itself. Only non-null results are cached — a null
     * (provider not yet installed at first access) is retried on the next call, so late provider
     * installation still works. Matching via [DaxDomain.sameOrSubdomain] is then parse-free.
     *
     * The cache is [Transient] (kept out of Moshi (de)serialization) and lives outside the primary
     * constructor, so `equals`/`hashCode`/`copy` remain by [domain]/[reason]. The write is unguarded
     * on purpose: a reference write is atomic and every resolution of the same [domain] yields an
     * equal [DaxDomain], so a race only costs a redundant lookup, never a wrong value.
     */
    val daxDomain: DaxDomain?
        get() = resolved ?: DaxUrlResolver.resolveDomain(domain)?.also { resolved = it }
}
