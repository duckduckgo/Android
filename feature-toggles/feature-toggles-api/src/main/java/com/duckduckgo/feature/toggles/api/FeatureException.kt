/*
 * Copyright (c) 2023 DuckDuckGo
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
package com.duckduckgo.feature.toggles.api

import com.duckduckgo.url.DaxUrl
import com.duckduckgo.url.DaxUrlResolver

data class FeatureException(val domain: String, val reason: String?) {
    /**
     * Semantic [DaxUrl] built from [domain], resolved through the process-wide
     * [DaxUrlResolver] (flag-gated legacy-vs-new). Null until a provider is installed at startup.
     * Computed, not stored: it has no backing field, so Moshi never (de)serializes it — consumers
     * don't need any URL adapter.
     */
    val url: DaxUrl? get() = DaxUrlResolver.resolve(domain)
}
