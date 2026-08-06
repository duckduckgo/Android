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

package com.duckduckgo.url

/**
 * A semantic host: guaranteed already-parsed and normalized (www-stripped `baseHost`) at
 * construction. Because the invariant holds, comparisons are pure operations with no
 * parse-and-null-check "dance" at the call site.
 *
 * Exposed as an interface with no public constructor and no exposed host String: instances are
 * only ever produced by the `-impl` module (via [DaxUrlProvider]), because parsing a raw String
 * into a host needs `android.net.Uri`, which this pure-JVM `-api` module can't reference. The
 * underlying host String is kept module-internal in `-impl` so consumers work through the semantic
 * operations below rather than re-deriving state from a raw String. Implementations provide
 * value-based `equals`/`hashCode` so a [DaxDomain] is usable as a cache key.
 */
interface DaxDomain {

    /** What kind of host this domain represents; see [DaxDomainType]. */
    val type: DaxDomainType

    /**
     * Returns true if [child] is the same host as, or a subdomain of, this domain
     * (`sameOrSubdomain` semantics, this being the parent).
     */
    fun sameOrSubdomain(child: DaxDomain): Boolean

    /** The registrable domain (eTLD+1), or null when there is none (IPs, `localhost`, a bare public suffix). */
    fun toTldPlusOne(): DaxDomain?

    /** The registrable domain (eTLD+1), or this domain itself when there is none. */
    fun toTldPlusOneOrSelf(): DaxDomain
}
