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
     * The current Chat-tab query — the text typed while the Chat tab is the input target.
     *
     * High-frequency (updates per keystroke), kept as its own flow so it doesn't churn
     * [displayedMode] consumers. Holds [""] when the Chat tab isn't the target (Search tab selected
     * or no widget attached), mirroring how [displayedMode] resets to [InputMode.SEARCH]. A sibling
     * `searchQuery` can follow the same pattern for the Search tab.
     */
    val chatQuery: StateFlow<String>
}

/**
 * Which tab is currently selected in the Duck.ai input widget toggle.
 */
enum class InputMode {
    SEARCH,
    DUCK_AI,
}
