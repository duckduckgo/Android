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

/**
 * Write interface for per-tab native input state.
 *
 * State is held in-memory only; nothing survives process death.
 */
interface MutableNativeInputStateProvider {

    /**
     * Marks [tabId] as the active tab. The tab's existing state is unchanged — to write fields,
     * call [update] separately. Drives [NativeInputStateProvider.displayedState] to mirror this tab.
     */
    fun updateActiveTab(tabId: String)

    /**
     * Applies [patch] to the current state for [tabId], creating the entry from
     * [NativeInputState.zero] if no widget has yet attached to that tab.
     *
     * Usage: `mutableProvider.update(tabId) { copy(selectedModelId = newId) }`
     */
    fun update(tabId: String, patch: NativeInputState.() -> NativeInputState)

    /**
     * Patches [tabId] from the persisted chat record for [chatId]: sets [NativeInputState.chatId]
     * and hydrates [NativeInputState.selectedModelId] from the record. Call when a tab navigates
     * to a Duck.ai chat URL with the chatID query parameter present.
     *
     * If the chat record is unknown or has no model recorded, [NativeInputState.selectedModelId]
     * is left untouched — only [NativeInputState.chatId] is updated.
     */
    suspend fun updateFromChat(tabId: String, chatId: String)

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
