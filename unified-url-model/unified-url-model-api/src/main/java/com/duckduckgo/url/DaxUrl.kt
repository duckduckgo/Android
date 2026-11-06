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
 * A semantic representation of a URL, built from a raw String.
 *
 * This interface intentionally exposes only semantic types, never the raw String it was built from
 * (that's kept module-internal in `-impl`) so consumers work through the operations below rather
 * than re-parsing a String. It lives in a pure-JVM `java-library` module (`:unified-url-model-api`)
 * so that `feature-toggles-api` can depend on it; concrete implementations that wrap
 * `android.net.Uri` / `okhttp3.HttpUrl` live in `:unified-url-model-impl`.
 */
interface DaxUrl {

    /** The normalized [DaxDomain] of this URL, or null when it has no resolvable host. */
    val domain: DaxDomain?

    /**
     * The host exactly as written (decoded, `www.` kept, case preserved), or null when there is none.
     * For matching use [domain]; this is for byte-exact work such as hashing or re-serializing a URL.
     */
    val host: String?

    /**
     * The URL serialized exactly as it was built or parsed, for byte-exact work only: regex matching,
     * hashing, cache keys. For comparison use [domain] or [matches], never this.
     */
    val serialized: String

    /** This URL's scheme. */
    val scheme: DaxScheme

    /** Sugar for `scheme == DaxScheme.HTTPS` — the shape the majority of scheme checks want. */
    val isHttps: Boolean

    /** True if this is a well-formed http/https web URL (`UriString.isWebUrl` semantics). */
    val isWebUrl: Boolean

    /** True if the host is local/private: `file://`, `localhost`, loopback, or a private range. */
    val isLocalUrl: Boolean

    /** True if the host is a mobile-site variant (`m.`/`mobile.` prefix). */
    val isMobileSite: Boolean

    /** The decoded path, or null when there is none (e.g. opaque URLs). */
    val path: String?

    /** The decoded path segments; empty for hostless/opaque URLs. Never null. */
    val pathSegments: List<String>

    /** The last decoded path segment, or null when there is none. */
    val lastPathSegment: String?

    /** The port, or null when none is specified (no `-1` sentinel). */
    val port: Int?

    /** The decoded query component (everything after `?`), or null when there is none. */
    val query: String?

    /** The decoded fragment (everything after `#`), or null when there is none. */
    val fragment: String?

    /** The set of query parameter names; empty for opaque URLs. */
    val queryParameterNames: Set<String>

    /** The first decoded value for [key], or null when absent (or the URL is opaque). */
    fun queryParameter(key: String): String?

    /** True if this URL's host is the same as, or a subdomain of, [other]'s host. */
    fun matches(other: DaxUrl): Boolean

    /**
     * True if this URL's host is the same as, or a subdomain of, [other]. This is the hot-path
     * overload for `exceptions.any { … }`, where the exception domains are already [DaxDomain]s
     * and so aren't re-parsed per iteration.
     */
    fun matches(other: DaxDomain): Boolean
}
