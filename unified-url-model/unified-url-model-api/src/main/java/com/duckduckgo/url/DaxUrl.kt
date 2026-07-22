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
 * A semantic representation of a URL/domain, built from a raw String.
 *
 * This interface intentionally exposes only JVM types: it lives in a pure-JVM `java-library`
 * module (`:unified-url-model-api`) so that `feature-toggles-api` can depend on it. Concrete
 * implementations that wrap `android.net.Uri` / `okhttp3.HttpUrl` live in `:unified-url-model-impl`.
 */
interface DaxUrl {

    /** The domain this model represents (as originally provided). */
    val domain: String

    /**
     * Returns true if [url] is the same domain as, or a subdomain of, this model's [domain]
     * (i.e. `sameOrSubdomain` semantics).
     */
    fun matches(url: String): Boolean
}
