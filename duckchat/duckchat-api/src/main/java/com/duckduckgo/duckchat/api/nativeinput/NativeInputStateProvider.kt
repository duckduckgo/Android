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

package com.duckduckgo.duckchat.api.nativeinput

import kotlinx.coroutines.flow.StateFlow

/**
 * Read interface for per-tab native input state.
 *
 * State is held in-memory for the lifetime of the process; nothing is persisted.
 * Mutation lives in the impl module (`MutableNativeInputStateProvider`) — only impl-side
 * ViewModels are expected to write state.
 */
interface NativeInputStateProvider {

    /**
     * Returns a [StateFlow] for a specific tab's state.
     * Creates an in-memory entry backed by [NativeInputState.zero] if the tab is not yet known.
     * The flow emits immediately on subscription with the current value.
     */
    fun stateForTab(tabId: String): StateFlow<NativeInputState>

    /**
     * Mirrors the currently active tab's state. For ambient UI consumers that do not
     * track tabs themselves (e.g. NTP background logo).
     * Resets to [NativeInputState.zero] when no tab is active.
     */
    val displayedState: StateFlow<NativeInputState>
}
