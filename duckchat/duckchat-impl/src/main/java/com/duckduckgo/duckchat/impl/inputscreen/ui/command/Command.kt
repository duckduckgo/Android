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

package com.duckduckgo.duckchat.impl.inputscreen.ui.command

sealed class Command {
    data class SwitchToTab(val tabId: String) : Command()
    data class UserSubmittedQuery(val query: String) : Command()
    data class EditWithSelectedQuery(val query: String) : Command()
    data class SubmitSearch(val query: String) : Command()
    data class SubmitChat(val query: String) : Command()
    data object ShowKeyboard : Command()
    data object HideKeyboard : Command()
    data class SetInputModeWidgetScrollPosition(val position: Int, val offset: Float) : Command()
    data class SetLogoProgress(val targetProgress: Float) : Command()
    data class AnimateLogoToProgress(val targetProgress: Float) : Command()
    data object FireButtonRequested : Command()
    data object TabSwitcherRequested : Command()
    data object MenuRequested : Command()
}
