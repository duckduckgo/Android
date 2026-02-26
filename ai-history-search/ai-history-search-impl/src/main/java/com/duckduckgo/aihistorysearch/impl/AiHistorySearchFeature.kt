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

package com.duckduckgo.aihistorysearch.impl

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "aiHistorySearch",
)
interface AiHistorySearchFeature {

    /**
     * @return `true` when the aiHistorySearch feature is enabled globally.
     * If the remote feature is not present defaults to `internal`.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    /**
     * When enabled, history entries are ranked on-device using BM25 before being sent to Duck.ai.
     * Only the top-ranked matches are included in the prompt, improving privacy by keeping the
     * full browser history on-device.
     * When disabled, the full time-filtered history (up to [AiHistorySearchInteractor.MAX_ENTRIES_FULL]
     * entries) is sent to Duck.ai for semantic matching.
     * Defaults to `internal` so it can be validated internally before a production rollout.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun bm25Enabled(): Toggle
}
