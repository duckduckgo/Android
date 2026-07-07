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

package com.duckduckgo.duckchat.api

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import kotlinx.coroutines.flow.StateFlow

/**
 * The input mode currently displayed by the Duck.ai input widget.
 *
 * Emits [InputMode.SEARCH] when no widget is showing, and the user's currently-selected
 * tab while a widget is attached. Consumers can use this to drive ambient UI such as the
 * NTP background logo.
 */
interface DuckChatInputModeState {

    /**
     * The input mode currently being expressed to ambient UI.
     *
     * Emits the current value on subscription and on every change. Backed by an app-scoped
     * state holder, so all subscribers see the same value. Resets to [InputMode.SEARCH] when
     * no Duck.ai input widget is attached.
     */
    val displayedMode: StateFlow<InputMode>

    /**
     * The live input query — the trimmed text in the shared input field, updated per keystroke
     * regardless of the selected tab (the field is shared between Search and Chat, so the query is the
     * same on both).
     *
     * High-frequency, kept as its own flow so it doesn't churn [displayedMode] consumers. Holds [""]
     * only when the field is blank or no widget is attached. Deliberately NOT reset to [""] on the
     * Search tab: a chat-tab item that derives its visibility from this (e.g. a zero-state card) would
     * otherwise go stale while Search is active and flash when the Chat tab re-renders. Pair it with
     * [displayedMode] when you also need to know which tab is showing.
     */
    val inputQuery: StateFlow<String>

    /**
     * Whether the current configuration offers the Search↔Duck.ai toggle in the address bar
     * ([NativeInputState.InputMode.SEARCH_AND_DUCK_AI]) or is search-only
     * ([NativeInputState.InputMode.SEARCH_ONLY]).
     *
     * Unlike [displayedMode] (the selected tab, pushed by a live widget), this is the capability of the
     * current config and is known even when no widget is attached — so callers can decide whether to use
     * the native input for the browser omnibar at all. Emits on subscription and on every settings change.
     */
    val availableInputMode: StateFlow<NativeInputState.InputMode>
}

/**
 * Which tab is currently selected in the Duck.ai input widget toggle.
 */
enum class InputMode {
    SEARCH,
    DUCK_AI,
}
