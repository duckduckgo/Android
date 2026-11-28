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

import androidx.core.net.toUri
import com.duckduckgo.common.utils.withScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.url.DaxDomain
import com.duckduckgo.url.DaxScheme
import com.duckduckgo.url.DaxUrl
import com.duckduckgo.url.DaxUrlBuilder
import com.duckduckgo.url.DaxUrlProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

// Lazy: every generated remote-feature module injects this provider, so eagerly injecting a feature here
// would close a DI cycle. Breaking it once at this end keeps Lazy out of generated code.
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxUrlProvider @Inject constructor(
    private val unifiedUrlModelFeature: Lazy<UnifiedUrlModelFeature>,
) : DaxUrlProvider {

    // Read once per process so the hot path stays off the toggle store and a DaxUrl/DaxDomain never mixes
    // modes within a session. A flag flip takes effect on the next process start.
    private val memoize by lazy { unifiedUrlModelFeature.value.useUnifiedUrl().isEnabled() }

    override fun getUrl(url: String): DaxUrl = RealDaxUrl(url, memoize)

    override fun getDomain(host: String): DaxDomain? = getUrl(host).domain

    override fun builder(): DaxUrlBuilder = RealDaxUrlBuilder(memoize)

    // Every DaxUrl comes from this provider, so the cast always holds.
    override fun withScheme(url: DaxUrl, scheme: DaxScheme): DaxUrl = withScheme((url as RealDaxUrl).raw, scheme)

    override fun withScheme(url: String, scheme: DaxScheme): DaxUrl {
        val rewritten = url.toUri().withScheme().buildUpon().scheme(scheme.value).build().toString()
        return getUrl(rewritten)
    }
}
