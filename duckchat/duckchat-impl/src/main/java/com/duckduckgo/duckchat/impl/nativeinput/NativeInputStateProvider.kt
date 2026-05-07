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

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.duckchat.impl.ui.NativeInputState
import kotlinx.coroutines.flow.StateFlow

/**
 * Read interface for per-tab native input state.
 *
 * Both interfaces live in duckchat-impl (not duckchat-api) because [NativeInputState]
 * contains impl-level types. Expose specific derived values to duckchat-api if an
 * external consumer ever needs them — YAGNI for now.
 */
interface NativeInputStateProvider {

    /**
     * Returns a [StateFlow] for a specific tab's state.
     * Creates an empty entry backed by [NativeInputState.zero] if the tab is not yet known.
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

/**
 * Write interface for per-tab native input state.
 *
 * The widget ViewModel calls [setActiveTab] and plugin ViewModels call [update].
 * Persisted fields ([NativeInputState.selectedModelId]) are written to Room automatically
 * when they change.
 */
interface MutableNativeInputStateProvider {

    /**
     * Called by the widget ViewModel when it attaches to a tab.
     * [structural] carries the fields the widget owns: [NativeInputState.inputMode],
     * [NativeInputState.inputContext], [NativeInputState.inputPosition].
     * These are merged with any persisted state already stored for [tabId].
     * Drives [NativeInputStateProvider.displayedState] to reflect this tab.
     */
    fun setActiveTab(tabId: String, structural: NativeInputState)

    /**
     * Applies [patch] to the current state for [tabId].
     * No-op if [tabId] has already been cleared. Persists any persisted fields that changed.
     *
     * Usage: `mutableProvider.update(tabId) { copy(selectedModelId = newId) }`
     */
    fun update(tabId: String, patch: NativeInputState.() -> NativeInputState)

    /**
     * Called when a tab is closed. Removes the in-memory flow and deletes the DB row.
     * If [tabId] was the active tab, resets [NativeInputStateProvider.displayedState] to
     * [NativeInputState.zero].
     */
    fun clearTab(tabId: String)
}
