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

package com.duckduckgo.contentscopescripts.api

/**
 * Reports which of the content scope scripts performance optimisations are currently active.
 *
 * Intended for telemetry that measures work these optimisations affect, so a measurement can be attributed to the code
 * path that produced it instead of being pooled across states. Not a way to branch behaviour: each optimisation is
 * applied by content scope scripts itself, and consumers must not re-implement those decisions.
 */
interface ContentScopeOptimizations {

    /**
     * @return the state of each optimisation at the time of the call. The values are read live, so they reflect any
     * privacy config update already applied, and two calls within one flow may disagree.
     */
    suspend fun current(): State

    data class State(
        /**
         * Whether the optimised content scope script assembly path is in use.
         */
        val injectionOptimized: Boolean,
        /**
         * Whether inbound content scope JS messages are queued off the WebView JavaBridge thread.
         */
        val messagingOptimized: Boolean,
        /**
         * Whether resolved content scope experiments are held between privacy config updates.
         */
        val experimentsCached: Boolean,
    )
}
