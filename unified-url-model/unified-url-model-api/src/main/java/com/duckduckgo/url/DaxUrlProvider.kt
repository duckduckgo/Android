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
 * Single factory for the semantic URL types. Construction — and any operation that *produces* a
 * new [DaxUrl]/[DaxDomain] — lives here rather than on the interfaces, because parsing needs
 * `android.net.Uri` (impl-only) and instances are cache-managed.
 */
interface DaxUrlProvider {

    fun getUrl(url: String): DaxUrl

    /** Parses [host] into a normalized [DaxDomain], or null when it has no resolvable host. */
    fun getDomain(host: String): DaxDomain?

    /** Returns a new [DaxUrl] with [url]'s scheme rewritten to [scheme] (was `Uri.toHttps`). */
    fun withScheme(url: DaxUrl, scheme: DaxScheme): DaxUrl

    /** Convenience overload for raw-String callers. */
    fun withScheme(url: String, scheme: DaxScheme): DaxUrl
}
