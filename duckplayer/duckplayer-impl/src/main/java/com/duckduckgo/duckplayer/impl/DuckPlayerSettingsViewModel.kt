/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.duckplayer.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.PrivatePlayerMode
import com.duckduckgo.duckplayer.api.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.Command.OpenLearnMore
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.Command.OpenPlayerModeSelector
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.ViewState.DisabledWithHelpLink
import com.duckduckgo.duckplayer.impl.DuckPlayerSettingsViewModel.ViewState.Enabled
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@ContributesViewModel(ActivityScope::class)
class DuckPlayerSettingsViewModel @Inject constructor(
    private val duckPlayer: DuckPlayer,
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    val viewState: StateFlow<ViewState> = duckPlayer.observeUserPreferences()
        .map {
            val helpPageLink = duckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink()
            if (duckPlayer.getDuckPlayerState() == DISABLED_WIH_HELP_LINK && helpPageLink?.isNotEmpty() == true) {
                DisabledWithHelpLink(it.privatePlayerMode, helpPageLink)
            } else {
                Enabled(it.privatePlayerMode)
            }
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = runBlocking { Enabled(duckPlayer.getUserPreferences().privatePlayerMode) },
        )

    sealed class Command {
        data class OpenPlayerModeSelector(val current: PrivatePlayerMode) : Command()
        data class OpenLearnMore(val learnMoreLink: String) : Command()
        data class LaunchDuckPlayerContingencyPage(val helpPageLink: String) : Command()
    }

    sealed class ViewState(open val privatePlayerMode: PrivatePlayerMode = AlwaysAsk) {
        data class Enabled(override val privatePlayerMode: PrivatePlayerMode) : ViewState(privatePlayerMode)
        data class DisabledWithHelpLink(override val privatePlayerMode: PrivatePlayerMode, val helpPageLink: String) : ViewState(privatePlayerMode)
    }
    fun duckPlayerModeSelectorClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenPlayerModeSelector(duckPlayer.getUserPreferences().privatePlayerMode))
        }
    }

    fun onPlayerModeSelected(selectedPlayerMode: PrivatePlayerMode) {
        viewModelScope.launch {
            duckPlayer.setUserPreferences(overlayInteracted = false, privatePlayerMode = selectedPlayerMode.value)
        }
    }

    fun duckPlayerLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenLearnMore("https://duckduckgo.com/duckduckgo-help-pages/duck-player/"))
        }
    }

    fun onContingencyLearnMoreClicked() {
        viewModelScope.launch {
            duckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink()?.let {
                commandChannel.send(Command.LaunchDuckPlayerContingencyPage(it))
            }
        }
    }
}
