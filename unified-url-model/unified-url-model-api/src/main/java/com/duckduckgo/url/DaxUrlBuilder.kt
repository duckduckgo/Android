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
 * Builds a [DaxUrl] from its components. Obtained from [DaxUrlProvider.builder].
 *
 * Components are taken verbatim and never re-encoded, so callers that canonicalize a URL before hashing or
 * matching get back exactly what they set.
 */
interface DaxUrlBuilder {

    /** Sets the scheme. [DaxScheme.OTHER] leaves the scheme unset. */
    fun scheme(scheme: DaxScheme): DaxUrlBuilder

    /** Sets the host verbatim: `www.` is kept, case and encoding are preserved. */
    fun encodedHost(host: String?): DaxUrlBuilder

    /** Sets the already-encoded path verbatim. */
    fun encodedPath(path: String?): DaxUrlBuilder

    /** Sets the already-encoded query (without the leading `?`) verbatim. */
    fun encodedQuery(query: String?): DaxUrlBuilder

    fun build(): DaxUrl
}
