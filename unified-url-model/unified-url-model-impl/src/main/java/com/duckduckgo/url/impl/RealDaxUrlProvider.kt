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
import com.duckduckgo.url.DaxUrlProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxUrlProvider @Inject constructor(
    private val unifiedUrlModelFeature: UnifiedUrlModelFeature,
) : DaxUrlProvider {

    // Read once (not per call): keeps the hot path off the toggle store and makes resolution
    // single-variant for the process lifetime, so which path a URL takes can't change mid-session.
    // A flag flip therefore takes effect on next process start — the right granularity for an
    // implementation switch (no swapping the URL model mid-session).
    private val useUnifiedUrl by lazy { unifiedUrlModelFeature.useUnifiedUrl().isEnabled() }

    // No dedup cache: each call builds a fresh model. Reuse is the caller's job — resolve once at the
    // entry boundary and hold the DaxUrl/DaxDomain, rather than re-resolving the same String downstream.
    override fun getUrl(url: String): DaxUrl = RealDaxUrl(url, memoize = useUnifiedUrl)

    override fun getDomain(host: String): DaxDomain? = getUrl(host).domain

    // Every DaxUrl is produced by this provider (no public constructor), so it is always a
    // RealDaxUrl — the cast is intentional.
    override fun withScheme(url: DaxUrl, scheme: DaxScheme): DaxUrl = withScheme((url as RealDaxUrl).raw, scheme)

    override fun withScheme(url: String, scheme: DaxScheme): DaxUrl {
        val rewritten = url.toUri().withScheme().buildUpon().scheme(scheme.value).build().toString()
        return getUrl(rewritten)
    }
}
