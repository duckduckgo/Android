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

package com.duckduckgo.adblocking.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AdBlockingSettingsViewModel @Inject constructor(
    private val statusChecker: AdBlockingStatusChecker,
    private val repository: AdBlockingSettingsRepository,
) : ViewModel() {

    data class ViewState(val isEnabled: Boolean = false)

    sealed class Command {
        data class OpenLearnMore(val url: String) : Command()
        data object OpenDuckPlayerSettings : Command()
    }

    val viewState: StateFlow<ViewState> = statusChecker.isUserEnabledFlow()
        .map { ViewState(isEnabled = it) }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(),
            initialValue = ViewState(),
        )

    private val commandChannel =
        Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<Command> = commandChannel.receiveAsFlow()

    fun onBlockAdsToggled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
        }
    }

    fun onLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenLearnMore(LEARN_MORE_URL))
        }
    }

    fun onDuckPlayerClicked() {
        viewModelScope.launch {
            commandChannel.send(Command.OpenDuckPlayerSettings)
        }
    }

    private companion object {
        // TODO: replace with the real Learn More URL once published
        const val LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/youtube/"
    }
}
