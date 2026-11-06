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
 * Assembles a [DaxUrl] from its parts, so a consumer that rewrites a URL never has to reach for
 * `android.net.Uri`. Obtained from [DaxUrlProvider.builder]; the parse it wraps is impl-only.
 *
 * Every component is taken **verbatim**: already-encoded, and neither re-encoded nor normalized on
 * the way in or out. That contract is the point of the builder — a consumer doing byte-exact work
 * (hashing a host, matching a regex against a serialized URL) cannot tolerate the built URL
 * differing from what it asked for. In particular the host is NOT www-stripped, so the resulting
 * [DaxUrl.host] round-trips while [DaxUrl.domain] still normalizes as it does everywhere else.
 *
 * Unset components are omitted. A builder starts empty, so a caller rewriting one part of an
 * existing URL must carry the others over itself — that keeps "what ends up in the URL" explicit at
 * the call site rather than inherited invisibly.
 */
interface DaxUrlBuilder {

    fun scheme(scheme: DaxScheme): DaxUrlBuilder

    /** Sets the host verbatim. Not normalized: `www.` is kept, case and encoding are preserved. */
    fun encodedHost(host: String?): DaxUrlBuilder

    fun encodedPath(path: String?): DaxUrlBuilder

    fun encodedQuery(query: String?): DaxUrlBuilder

    fun build(): DaxUrl
}
