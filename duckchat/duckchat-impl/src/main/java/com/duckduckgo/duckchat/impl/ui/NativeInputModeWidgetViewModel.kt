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
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.ChatState
import com.duckduckgo.duckchat.impl.DuckChatConstants.CHAT_ID_PARAM
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.helper.PendingNativePromptStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.ChatSuggestion
import com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions.reader.ChatSuggestionsReader
import com.duckduckgo.duckchat.impl.nativeinput.NativeInputPlugin
import com.duckduckgo.duckchat.impl.nativeinput.PromptContribution
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class NativeInputModeWidgetViewModel @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    duckAiFeatureState: DuckAiFeatureState,
    subscriptions: Subscriptions,
    private val pendingNativePromptStore: PendingNativePromptStore,
    private val chatSuggestionsReader: ChatSuggestionsReader,
    private val nativeInputPlugins: ActivePluginPoint<NativeInputPlugin>,
) : ViewModel() {

    sealed class Command {
        data class InstallPlugins(val plugins: List<NativeInputPlugin>) : Command()
        data class UpdatePluginVisibility(val containerIds: List<Int>, val visible: Boolean) : Command()
    }

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    private var activePlugins: List<NativeInputPlugin> = emptyList()

    init {
        viewModelScope.launch {
            val plugins = nativeInputPlugins.getPlugins().toList()
            activePlugins = plugins
            commandChannel.trySend(Command.InstallPlugins(plugins))
        }
    }

    fun updatePluginContainerVisibility(isChatTab: Boolean) {
        val containerIds = activePlugins.map { it.containerId }
        if (containerIds.isNotEmpty()) {
            commandChannel.trySend(Command.UpdatePluginVisibility(containerIds, isChatTab))
        }
    }

    fun getSelectedModelId(): String? {
        return activePlugins.firstNotNullOfOrNull { plugin ->
            (plugin.getPromptContribution() as? PromptContribution.ModelSelection)?.modelId
        }
    }

    private data class WidgetConfig(
        val inputContext: NativeInputState.InputContext = NativeInputState.InputContext.BROWSER,
        val inputPosition: NativeInputState.InputPosition = NativeInputState.InputPosition.TOP,
    )

    private val widgetConfig = MutableStateFlow(WidgetConfig())

    val state: SharedFlow<NativeInputState> = combine(
        duckAiFeatureState.showSettings,
        duckChatInternal.observeEnableDuckChatUserSetting(),
        duckChatInternal.observeInputScreenUserSettingEnabled(),
        widgetConfig,
    ) { isFeatureEnabled, isUserEnabled, isInputScreenUserSettingEnabled, config ->
        NativeInputState(
            inputMode = getInputMode(isFeatureEnabled && isUserEnabled, isInputScreenUserSettingEnabled),
            inputContext = config.inputContext,
            inputPosition = config.inputPosition,
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

    val defaultTogglePosition: Flow<DefaultTogglePosition> = duckChatInternal.observeDefaultTogglePosition()

    val lastUsedTogglePosition: Flow<String?> = duckChatInternal.observeLastUsedTogglePosition()

    suspend fun saveLastUsedTogglePosition(position: String) {
        duckChatInternal.saveLastUsedTogglePosition(position)
    }

    fun setDuckAiMode(isDuckAiMode: Boolean) {
        val context = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
        widgetConfig.update { it.copy(inputContext = context) }
    }

    fun setWidgetPosition(isBottom: Boolean) {
        val position = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
        widgetConfig.update { it.copy(inputPosition = position) }
    }

    fun configure(isDuckAiMode: Boolean, isBottom: Boolean) {
        val context = if (isDuckAiMode) NativeInputState.InputContext.DUCK_AI else NativeInputState.InputContext.BROWSER
        val position = if (isBottom) NativeInputState.InputPosition.BOTTOM else NativeInputState.InputPosition.TOP
        widgetConfig.value = WidgetConfig(inputContext = context, inputPosition = position)
    }

    fun storePendingPrompt(query: String) {
        pendingNativePromptStore.store(query, getSelectedModelId())
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
