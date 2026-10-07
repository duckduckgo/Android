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

package com.duckduckgo.app.browser.newtab

import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.DuckChatInputModeState
import com.duckduckgo.duckchat.api.InputMode
import com.duckduckgo.savedsites.api.SavedSitesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Decides whether the Next Steps section gives way to what the focused input shows on the New Tab Page:
 * the favourites, and the recent chats listed under the Duck.ai tab.
 */
class NextStepsInputFocusVisibilityHandler @Inject constructor(
    savedSitesRepository: SavedSitesRepository,
    duckChat: DuckChat,
    duckChatInputModeState: DuckChatInputModeState,
) {

    private val isInputFocused = MutableStateFlow(false)

    // No value is replayed until the Chat tab first fetches, so an unknown state counts as no chats.
    private val hasDuckAiChats = combine(
        duckChat.observeHasChatSuggestions().onStart { emit(false) },
        duckChat.observeChatSuggestionsUserSettingEnabled(),
    ) { hasChats, chatSuggestionsEnabled -> hasChats && chatSuggestionsEnabled }

    val shouldHideNextSteps: Flow<Boolean> = combine(
        isInputFocused,
        savedSitesRepository.getFavorites().map { it.isNotEmpty() },
        duckChatInputModeState.displayedMode.map { it == InputMode.DUCK_AI },
        hasDuckAiChats,
    ) { focused, hasFavourites, isDuckAiTabSelected, hasChats ->
        focused && (hasFavourites || (isDuckAiTabSelected && hasChats))
    }.distinctUntilChanged()

    fun onInputFocusChanged(isFocused: Boolean) {
        isInputFocused.value = isFocused
    }
}
