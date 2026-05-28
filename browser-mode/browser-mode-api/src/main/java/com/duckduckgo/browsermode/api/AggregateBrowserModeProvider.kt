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

package com.duckduckgo.browsermode.api

import kotlinx.coroutines.flow.Flow

/**
 * Emits a [T] aggregated across the requested set of [BrowserMode]s. Implementations
 * decide how per-mode values are combined into a single [T].
 *
 * Sibling to [BrowserModeDataProvider]: that one resolves a single mode-bound instance
 * point-in-time; this one composes reactive streams from one or more modes.
 */
interface AggregateBrowserModeProvider<T> {
    /**
     * Emits a [T] composed from each mode in [modes]. Re-emits when any contributing
     * mode's underlying source emits. Passing a single mode yields that mode's flow
     * unchanged. Omitting [modes] observes every value of [BrowserMode].
     */
    fun observe(modes: Set<BrowserMode> = BrowserMode.entries.toSet()): Flow<T>
}
