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

data class NativeInputState(
    val inputMode: InputMode,
    val inputContext: InputContext,
    val inputPosition: InputPosition = InputPosition.TOP,
    val toggleSelection: ToggleSelection = defaultToggleFor(inputContext),
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

    companion object {
        fun defaultToggleFor(context: InputContext): ToggleSelection = when (context) {
            InputContext.DUCK_AI, InputContext.DUCK_AI_CONTEXTUAL -> ToggleSelection.DUCK_AI
            InputContext.BROWSER -> ToggleSelection.SEARCH
        }

        /**
         * Placeholder state used by [NativeInputStateProvider] for a tab that has not yet been
         * published to. Observers should not rely on these values: the native input widget overwrites
         * them via [NativeInputStatePublisher.publish] as soon as it is configured for the tab.
         */
        fun zero(): NativeInputState = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
            inputPosition = InputPosition.TOP,
        )
    }
}
