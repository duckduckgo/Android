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
    val selectedTool: String? = null,
    /** Set when the active tab is a Duck.ai page already attached to an existing chat. */
    val chatId: String? = null,
    /** True while the active chat is streaming a response (ChatState.STREAMING or LOADING). */
    val isChatStreaming: Boolean = false,
    /**
     * Set when we are in model change mode in ongoing chats
     * (between `showModelPicker` message and the next prompt submit, chat change, or dismissal without selection).
     * Routes picker taps to `submitChangeModelAction` instead of normal behaviour.
     */
    val modelChangeMode: Boolean = false,
    /**
     * Whether the native input submit button is enabled for this tab.
     */
    val submitEnabled: Boolean = true,
    /**
     * Whole input field is non-interactive and dimmed. Generic capability; currently set during the
     * Duck.ai onboarding demo.
     */
    val interactionLocked: Boolean = false,
    /**
     * Emphasise the leading fire button (bright + pulsing). Rendered only where that button is shown
     * (i.e. [InputContext.DUCK_AI]).
     */
    val duckAiFireButtonHighlighted: Boolean = false,
) {
    enum class InputMode {
        SEARCH_AND_DUCK_AI,
        SEARCH_ONLY,
    }

    enum class InputContext { BROWSER, DUCK_AI, DUCK_AI_CONTEXTUAL }

    enum class ToggleSelection { SEARCH, DUCK_AI }

    enum class InputPosition { TOP, BOTTOM }

    val toggleVisible: Boolean get() = inputMode == InputMode.SEARCH_AND_DUCK_AI && inputContext == InputContext.BROWSER

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
