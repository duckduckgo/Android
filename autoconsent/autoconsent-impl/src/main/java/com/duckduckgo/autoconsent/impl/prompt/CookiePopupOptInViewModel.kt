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

package com.duckduckgo.autoconsent.impl.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class CookiePopupOptInViewModel @Inject constructor(
    private val autoconsent: Autoconsent,
    private val settingsRepository: AutoconsentSettingsRepository,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    /**
     * Whether Cookie Pop-Up Protection is already on decides what the prompt offers to turn on, and so
     * the copy: the screen itself is identical for both.
     */
    enum class Variant { PROTECTION_ON, PROTECTION_OFF }

    data class ViewState(val variant: Variant)

    sealed class Command {
        data object Close : Command()
    }

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    private val viewStateFlow = MutableStateFlow(
        ViewState(
            variant = if (autoconsent.isSettingEnabled()) Variant.PROTECTION_ON else Variant.PROTECTION_OFF,
        ),
    )
    val viewState: StateFlow<ViewState> = viewStateFlow

    fun commands(): Flow<Command> = command.receiveAsFlow()

    fun onAcceptClicked() {
        viewModelScope.launch {
            // All three land in SharedPreferences with a synchronous commit, so they stay off the main thread.
            withContext(dispatchers.io()) {
                if (viewStateFlow.value.variant == Variant.PROTECTION_OFF) {
                    autoconsent.changeSetting(true)
                }
                autoconsent.changeClickAcceptEnabled(true)
                settingsRepository.optInPromptChoiceMade = true
            }
            command.send(Command.Close)
        }
    }

    fun onDeclineClicked() {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                settingsRepository.optInPromptChoiceMade = true
            }
            command.send(Command.Close)
        }
    }
}
