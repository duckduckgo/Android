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

package com.duckduckgo.app.firebutton

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireClearOption
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.DATA_CLEAR_TYPE_CHATS
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.DATA_CLEAR_TYPE_DATA
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.DATA_CLEAR_TYPE_TABS
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AutomaticDataClearingSettingsViewModel @Inject constructor(
    private val fireDataStore: FireDataStore,
    private val duckChat: DuckChat,
    duckAiFeatureState: DuckAiFeatureState,
    private val dispatcherProvider: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val automaticClearingEnabled: Boolean = false,
        val clearTabs: Boolean = false,
        val clearData: Boolean = false,
        val clearDuckAiChats: Boolean = false,
        val showDuckAiChatsOption: Boolean = false,
        val clearWhenOption: ClearWhenOption = ClearWhenOption.APP_EXIT_ONLY,
    )

    sealed class Command {
        data class ShowClearWhenDialog(val option: ClearWhenOption) : Command()
    }

    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private var duckChatWasOpenedBefore = MutableStateFlow(false)
    private var initialOptions: Set<FireClearOption>? = null

    val viewState: Flow<ViewState> = combine(
        fireDataStore.getAutomaticClearOptionsFlow(),
        fireDataStore.getAutomaticallyClearWhenOptionFlow(),
        duckAiFeatureState.showClearDuckAIChatHistory,
        duckChatWasOpenedBefore,
    ) { options, clearWhenOption, showClearDuckAiChatHistory, wasOpenedBefore ->
        val isDuckChatClearingAvailable = wasOpenedBefore && showClearDuckAiChatHistory
        val clearingOptions = if (!isDuckChatClearingAvailable) {
            options - FireClearOption.DUCKAI_CHATS
        } else {
            options
        }

        ViewState(
            automaticClearingEnabled = clearingOptions.isNotEmpty(),
            clearTabs = FireClearOption.TABS in clearingOptions,
            clearData = FireClearOption.DATA in clearingOptions,
            clearDuckAiChats = FireClearOption.DUCKAI_CHATS in clearingOptions,
            showDuckAiChatsOption = isDuckChatClearingAvailable,
            clearWhenOption = clearWhenOption,
        )
    }.flowOn(dispatcherProvider.io())

    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        loadDuckChatState()
        loadInitialOptions()
    }

    private fun loadDuckChatState() {
        viewModelScope.launch(dispatcherProvider.io()) {
            duckChatWasOpenedBefore.value = duckChat.wasOpenedBefore()
        }
    }

    private fun loadInitialOptions() {
        viewModelScope.launch(dispatcherProvider.io()) {
            initialOptions = fireDataStore.getAutomaticClearOptions()
        }
    }

    fun onAutomaticClearingToggled(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (enabled) {
                fireDataStore.setAutomaticClearOptions(setOf(FireClearOption.TABS, FireClearOption.DATA))
            } else {
                fireDataStore.setAutomaticClearOptions(emptySet())
            }
        }
    }

    fun onOptionToggled(option: FireClearOption, enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            if (enabled) {
                fireDataStore.addAutomaticClearOption(option)
            } else {
                fireDataStore.removeAutomaticClearOption(option)
            }
        }
    }

    fun onClearWhenClicked() {
        viewModelScope.launch {
            pixel.fire(AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_SHOWN)

            val currentOption = withContext(dispatcherProvider.io()) {
                fireDataStore.getAutomaticallyClearWhenOption()
            }
            _commands.send(Command.ShowClearWhenDialog(currentOption))
        }
    }

    fun onClearWhenOptionSelected(option: ClearWhenOption) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io()) {
                fireDataStore.setAutomaticallyClearWhenOption(option)
            }
        }
    }

    fun onScreenExit() {
        sendPixelIfOptionsChanged()
    }

    private fun sendPixelIfOptionsChanged() {
        viewModelScope.launch(dispatcherProvider.io()) {
            val options = fireDataStore.getAutomaticClearOptions()
            if (options != initialOptions) {
                pixel.fire(
                    AppPixelName.DATA_CLEARING_AUTOMATIC_OPTIONS_UPDATED,
                    mapOf(
                        DATA_CLEAR_TYPE_TABS to (FireClearOption.TABS in options).toString(),
                        DATA_CLEAR_TYPE_DATA to (FireClearOption.DATA in options).toString(),
                        DATA_CLEAR_TYPE_CHATS to (FireClearOption.DUCKAI_CHATS in options).toString(),
                    ),
                )
                initialOptions = options
            }
        }
    }
}
