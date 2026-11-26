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

package com.duckduckgo.url.impl

import com.duckduckgo.common.utils.LOCALHOST
import com.duckduckgo.common.utils.extensions.isIpv4Literal
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxDomainType

/**
 * When [memoize] is true the eTLD+1 (a public-suffix lookup) is cached; when false it is recomputed per call.
 * [value] is an already-normalized (www-stripped `baseHost`) host: instances only come from the provider, so
 * the invariant holds.
 *
 * `equals`/`hashCode` use [value] only, so a memoizing and a recomputing domain for the same host are equal
 * and either is a usable cache key.
 */
internal class RealDaxDomain(
    internal val value: String,
    internal val memoize: Boolean,
) : DaxDomain {

    private val tldPlusOne: DaxDomain? by held(memoize) {
        registrableDomain?.let { RealDaxDomain(it, memoize) }
    }

    // Exception lists are long-lived and every check compares the child against each entry, so with
    // memoization on this is built once per domain rather than once per comparison.
    private val dottedValue: String by held(memoize) { ".$value" }

    override fun toTldPlusOne(): DaxDomain? = tldPlusOne

    override fun sameOrSubdomain(child: DaxDomain): Boolean {
        // Every DaxDomain is produced by DaxUrlProvider (no public constructor), so it is always a
        // RealDaxDomain — the cast is intentional. Do not "soften" it to equals/toString: this is a
        // protection matcher, where a silent wrong match is worse than a loud failure on a foreign impl.
        val childValue = (child as RealDaxDomain).value
        return value == childValue || childValue.endsWith(dottedValue)
    }

    override fun toTldPlusOneOrSelf(): DaxDomain = toTldPlusOne() ?: this

    override fun equals(other: Any?): Boolean = other is RealDaxDomain && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    override val type: DaxDomainType
        get() = when {
            value.isIpv4Literal() -> DaxDomainType.IP
            value == LOCALHOST -> DaxDomainType.LOCALHOST
            registrableDomain != null -> DaxDomainType.REGISTRABLE_DOMAIN
            else -> DaxDomainType.OTHER
        }

    /**
     * The registrable domain (eTLD+1) of [value], or null when there is none (IPs, `localhost`, a
     * bare public suffix). Same IP-guard-then-lookup logic as [String.toTldPlusOneOrSelf], so
     * behavior can't drift; a raw `PublicSuffixDatabase` lookup alone mishandles IPv4 literals
     * (`127.0.0.1` → `0.1`). [toTldPlusOne] builds from this and only differs in whether it caches.
     */
    private val registrableDomain: String?
        get() = if (value.isIpv4Literal()) null else value.toTldPlusOne()
}
