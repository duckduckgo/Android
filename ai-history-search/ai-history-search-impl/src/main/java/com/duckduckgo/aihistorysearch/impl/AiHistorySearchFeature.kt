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

    /**
     * When enabled, history entries are ranked on-device using semantic embeddings
     * (Universal Sentence Encoder Lite via MediaPipe Tasks Text) instead of BM25.
     * Takes precedence over [bm25Enabled] when both are on.
     * Defaults to `internal` for dogfooding before production rollout.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun embeddingsEnabled(): Toggle

    /**
     * When enabled, Gemini Nano is invoked on-device via the ML Kit GenAI Prompt API as a
     * shadow path before the main routing. The on-device answer is logcatted only — Duck.ai
     * always opens normally via the embeddings / BM25 / full-history path.
     * PoC: lets us evaluate on-device quality and latency without any UI changes.
     * Defaults to `internal` so it only runs on internal builds.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun aiCoreEnabled(): Toggle

    /**
     * When enabled, Gemma 3 1B IT runs on-device via MediaPipe LLM Inference Task as a
     * shadow path before the main routing. Produces both a ranked list and a synthesized
     * answer, logcatted only — Duck.ai always opens normally.
     * PoC: evaluates on-device LLM quality across ~90% of devices (≥4 GB RAM).
     * Defaults to `internal` so it only runs on internal builds.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun gemmaEnabled(): Toggle
}
