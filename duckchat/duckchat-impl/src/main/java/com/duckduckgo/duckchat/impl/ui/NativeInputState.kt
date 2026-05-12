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

import android.net.Uri

data class NativeInputState(
    val inputMode: InputMode,
    val inputContext: InputContext,
    val inputPosition: InputPosition = InputPosition.TOP,
    /** Model explicitly chosen by the user for this tab. Null = use global default. */
    val selectedModelId: String? = null,
    /** Duck Chat conversation associated with this tab, or null if none yet. */
    val chatId: String? = null,
    val attachedImages: List<Uri> = emptyList(),
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

    val defaultToggleSelection: ToggleSelection
        get() = when (inputContext) {
            InputContext.DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL -> ToggleSelection.DUCK_AI
            InputContext.BROWSER -> ToggleSelection.SEARCH
        }

    companion object {
        fun zero() = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.BROWSER,
        )
    }
}
