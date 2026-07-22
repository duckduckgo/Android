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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.url.DaxUrl
import com.duckduckgo.url.DaxUrlProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxUrlProvider @Inject constructor(
    private val unifiedUrlModelFeature: UnifiedUrlModelFeature,
) : DaxUrlProvider {

    // Instances are cached per domain so repeated reads (e.g. per exception on the hot path) reuse
    // the same memoized model rather than re-parsing. Both variants produce identical matches()
    // results, so keeping a cached variant across a flag flip only affects performance, not behavior.
    private val cache = ConcurrentHashMap<String, DaxUrl>()

    override fun getUrl(domain: String): DaxUrl {
        return cache.getOrPut(domain) {
            if (unifiedUrlModelFeature.useUnifiedUrl().isEnabled()) {
                NewDaxUrl(domain)
            } else {
                LegacyDaxUrl(domain)
            }
        }
    }
}
