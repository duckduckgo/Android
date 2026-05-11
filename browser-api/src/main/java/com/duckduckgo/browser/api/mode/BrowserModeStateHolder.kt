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

package com.duckduckgo.browser.api.mode

import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory holder of the user's currently-active [BrowserMode].
 *
 * App-scoped singleton with no persistence: cold-start always reconstructs as
 * [BrowserMode.REGULAR]; hot-start retains the last value. [switchTo] is the only
 * mutator. Consumers either collect [currentMode] for reactive updates, or read
 * [StateFlow.value] for a snapshot.
 *
 * Tab-level code should not consume this directly — capture the active mode at tab
 * construction time instead, so a mid-session switch cannot race with tab-bound writes.
 */
interface BrowserModeStateHolder {
    val currentMode: StateFlow<BrowserMode>

    fun switchTo(mode: BrowserMode)
}
