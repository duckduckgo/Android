/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui.state

data class InputScreenVisibilityState(
    val submitButtonVisible: Boolean = false,
    val voiceInputButtonVisible: Boolean = false,
    val autoCompleteSuggestionsVisible: Boolean = false,
    val bottomFadeVisible: Boolean = false,
    val showChatLogo: Boolean = true,
    val showSearchLogo: Boolean = true,
    val newLineButtonVisible: Boolean = false,
    val mainButtonsVisible: Boolean = false,
    val searchMode: Boolean = false,
    val fullScreenMode: Boolean = false,
) {
    val actionButtonsContainerVisible: Boolean = submitButtonVisible || voiceInputButtonVisible || newLineButtonVisible
}
