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
 * Process-wide access point that turns a domain String into a [DaxUrl].
 *
 * `FeatureException.url` is a computed property on a data class in the feature-toggles `-api`
 * module, which can neither participate in dependency injection nor depend on `:di`. This holder
 * bridges that gap: the app installs the app-scoped [DaxUrlProvider] once at startup (see the
 * initializer in `:unified-url-model-impl`), and the computed property reads through it.
 * Resolution stays flag-aware because it defers to the provider on every call.
 *
 * Returns null until the provider is installed (very early startup, or tests that don't install one).
 */
object DaxUrlResolver {

    @Volatile
    private var provider: DaxUrlProvider? = null

    /** Installs the process-wide provider. Called once at app startup. Later installs overwrite. */
    fun install(provider: DaxUrlProvider) {
        println("Cris install provider")
        this.provider = provider
    }

    fun resolve(domain: String): DaxUrl? = provider?.getUrl(domain)

    fun resolveDomain(host: String): DaxDomain? = (provider?.getDomain(host))

    /** Test-only: clears the installed provider so process-global state can't leak across tests. */
    fun reset() {
        provider = null
    }
}
