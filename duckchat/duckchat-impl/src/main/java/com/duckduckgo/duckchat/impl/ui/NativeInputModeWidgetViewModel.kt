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

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatConstants.CHAT_ID_PARAM
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class NativeInputModeWidgetViewModel @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    duckAiFeatureState: DuckAiFeatureState,
    subscriptions: Subscriptions,
    private val pendingNativePromptStore: PendingNativePromptStore,
    private val chatSuggestionsReader: ChatSuggestionsReader,
) : ViewModel() {

    private val inputContext = MutableStateFlow(NativeInputState.InputContext.BROWSER)
    private val inputPosition = MutableStateFlow(NativeInputState.InputPosition.TOP)

    val state: SharedFlow<NativeInputState> = combine(
        duckAiFeatureState.showSettings,
        duckChatInternal.observeEnableDuckChatUserSetting(),
        duckChatInternal.observeInputScreenUserSettingEnabled(),
        inputContext,
        inputPosition,
    ) { isFeatureEnabled, isUserEnabled, isInputScreenUserSettingEnabled, context, position ->
        NativeInputState(
            inputMode = getInputMode(isFeatureEnabled && isUserEnabled, isInputScreenUserSettingEnabled),
            inputContext = context,
            inputPosition = position,
        )
    }.shareIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    val chatState: Flow<ChatState> = duckChatInternal.chatState

    val isPaidTier: Flow<Boolean> = subscriptions.getEntitlementStatus()
        .map { entitlements -> entitlements.any { it == Product.DuckAiPlus } }

    val chatSuggestionsUserEnabled: Flow<Boolean> = duckChatInternal.observeChatSuggestionsUserSettingEnabled()

    fun setDuckAiMode(isDuckAiMode: Boolean) {
        inputContext.value = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
    }

    fun setWidgetPosition(isBottom: Boolean) {
        inputPosition.value = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
    }

    fun storePendingPrompt(query: String, modelId: String?) {
        pendingNativePromptStore.store(query, modelId)
    }

    suspend fun fetchChatSuggestions(query: String): List<ChatSuggestion> =
        runCatching { chatSuggestionsReader.fetchSuggestions(query) }.getOrDefault(emptyList())

    fun cancelChatSuggestions() {
        chatSuggestionsReader.tearDown()
    }

    fun buildChatSuggestionUrl(suggestion: ChatSuggestion): String =
        duckChatInternal.getDuckChatUrl("", false)
            .toUri()
            .buildUpon()
            .appendQueryParameter(CHAT_ID_PARAM, suggestion.chatId)
            .build()
            .toString()

    private fun getInputMode(
        isEnabled: Boolean,
        isInputScreenUserSettingEnabled: Boolean,
    ): NativeInputState.InputMode =
        if (isEnabled && isInputScreenUserSettingEnabled) {
            NativeInputState.InputMode.SEARCH_AND_DUCK_AI
        } else {
            NativeInputState.InputMode.SEARCH_ONLY
        }
}
