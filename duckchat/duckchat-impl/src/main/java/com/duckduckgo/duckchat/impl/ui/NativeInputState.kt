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

package com.duckduckgo.duckchat.impl.ui

data class NativeInputState(
    val inputMode: InputMode,
    val inputContext: InputContext,
    val inputPosition: InputPosition = InputPosition.TOP,
) {
    enum class InputMode {
        SEARCH_AND_DUCK_AI,
        SEARCH_ONLY,
    }

    enum class InputContext { BROWSER, DUCK_AI, DUCK_AI_CONTEXTUAL }

    enum class ToggleSelection { SEARCH, DUCK_AI }

    enum class InputPosition { TOP, BOTTOM }

    val toggleVisible: Boolean get() = inputMode == InputMode.SEARCH_AND_DUCK_AI && inputContext != InputContext.DUCK_AI_CONTEXTUAL

    val isBottom: Boolean get() = inputPosition == InputPosition.BOTTOM

    val defaultToggleSelection: ToggleSelection get() =
        if (inputContext == InputContext.DUCK_AI || inputContext == InputContext.DUCK_AI_CONTEXTUAL) ToggleSelection.DUCK_AI else ToggleSelection.SEARCH
}
