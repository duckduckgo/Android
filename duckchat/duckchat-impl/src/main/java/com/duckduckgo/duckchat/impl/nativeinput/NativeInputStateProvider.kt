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
 * State is held in-memory for the lifetime of the process; nothing is persisted.
 * Both interfaces live in duckchat-impl (not duckchat-api) because [NativeInputState]
 * contains impl-level types. Expose specific derived values to duckchat-api if an
 * external consumer ever needs them — YAGNI for now.
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

/**
 * Write interface for per-tab native input state.
 *
 * State is held in-memory only; nothing survives process death.
 * The widget ViewModel calls [setActiveTab] and plugin ViewModels call [update].
 */
interface MutableNativeInputStateProvider {

    /**
     * Called by the widget ViewModel when it attaches to a tab.
     * [structural] carries the fields the widget owns: [NativeInputState.inputMode],
     * [NativeInputState.inputContext], [NativeInputState.inputPosition].
     * Other fields already known for [tabId] (e.g. [NativeInputState.selectedModelId],
     * [NativeInputState.chatId]) are preserved.
     * Drives [NativeInputStateProvider.displayedState] to reflect this tab.
     */
    fun setActiveTab(tabId: String, structural: NativeInputState)

    /**
     * Applies [patch] to the current state for [tabId], creating the entry from
     * [NativeInputState.zero] if no widget has yet attached to that tab.
     *
     * Usage: `mutableProvider.update(tabId) { copy(selectedModelId = newId) }`
     */
    fun update(tabId: String, patch: NativeInputState.() -> NativeInputState)

    /**
     * Records that [tabId] is showing the Duck.ai conversation with [chatId], and hydrates the
     * tab's selected model from the persisted chat record. Call when a tab navigates to a
     * Duck.ai chat URL with the chatID query parameter present.
     *
     * If the chat record is unknown or has no model recorded, the existing [NativeInputState.selectedModelId]
     * is left untouched — only [NativeInputState.chatId] is updated.
     */
    suspend fun loadChatState(tabId: String, chatId: String)

    /**
     * Called when a tab is closed. Removes the in-memory entry.
     * If [tabId] was the active tab, resets [NativeInputStateProvider.displayedState] to
     * [NativeInputState.zero].
     */
    fun clearTab(tabId: String)

    /**
     * Drops every in-memory per-tab entry and resets [NativeInputStateProvider.displayedState]
     * to [NativeInputState.zero]. Call when all tabs are wiped (e.g. fire button).
     */
    fun clearAll()
}
