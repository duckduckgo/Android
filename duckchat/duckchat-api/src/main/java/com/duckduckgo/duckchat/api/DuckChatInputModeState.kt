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
 * This is consumer-facing state — what to render right now — not the user's persistent
 * selection. The toggle is treated as dormant when the omnibar input is not engaged
 * (focused), so [displayedMode] collapses to [InputMode.SEARCH] in that case regardless
 * of the user's underlying choice. When the user re-engages the omnibar, the value flips
 * back to their selection.
 */
interface DuckChatInputModeState {

    /**
     * The input mode currently being expressed to ambient UI (e.g. the NTP background logo).
     *
     * Emits the current value on subscription and on every change. Backed by an app-scoped
     * state holder, so all subscribers see the same value.
     *
     * Behaviour:
     * - Reflects the user's selection while the omnibar input is engaged (focused).
     * - Collapses to [InputMode.SEARCH] when the input is not engaged, regardless of the
     *   user's underlying selection (the toggle is dormant).
     */
    val displayedMode: StateFlow<InputMode>
}

/**
 * Which tab is currently selected in the Duck.ai input widget toggle.
 */
enum class InputMode {
    SEARCH,
    DUCK_AI,
}
