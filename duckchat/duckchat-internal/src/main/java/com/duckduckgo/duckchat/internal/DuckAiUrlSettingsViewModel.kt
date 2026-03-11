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

package com.duckduckgo.duckchat.internal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckAiUrlSettingsViewModel @Inject constructor(
    private val devDuckAiUrlOverride: DevDuckAiUrlOverride,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    fun viewState(): StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    fun commands(): Flow<Command> = _commands.receiveAsFlow()

    data class ViewState(
        val customUrl: String = "",
    )

    sealed class Command {
        data class ShowMessage(val messageResId: Int) : Command()
    }

    fun start() {
        viewModelScope.launch {
            _viewState.emit(
                ViewState(customUrl = devDuckAiUrlOverride.getCustomUrl().orEmpty()),
            )
        }
    }

    fun onSaveClicked(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotBlank()) {
            devDuckAiUrlOverride.setCustomUrl(trimmed)
            sendCommand(Command.ShowMessage(R.string.devSettingsDuckAiUrlSet))
        } else {
            devDuckAiUrlOverride.setCustomUrl(null)
            sendCommand(Command.ShowMessage(R.string.devSettingsDuckAiUrlCleared))
        }
    }

    fun onResetClicked() {
        devDuckAiUrlOverride.setCustomUrl(null)
        viewModelScope.launch {
            _viewState.emit(ViewState(customUrl = ""))
        }
        sendCommand(Command.ShowMessage(R.string.devSettingsDuckAiUrlCleared))
    }

    private fun sendCommand(command: Command) {
        viewModelScope.launch { _commands.send(command) }
    }
}
