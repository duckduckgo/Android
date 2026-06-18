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

package com.duckduckgo.adblocking.impl.duckplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.OpenDuckPlayerInNewTab
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.Disabled
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_ALWAYS_SETTINGS
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_BACK_TO_DEFAULT
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerPixelName.DUCK_PLAYER_SETTINGS_NEVER_SETTINGS
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsViewModel.Command.OpenLearnMore
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsViewModel.Command.OpenPlayerModeSelector
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsViewModel.ViewState.DisabledWithHelpLink
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsViewModel.ViewState.Enabled
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckPlayerSettingsViewModel @Inject constructor(
    private val duckPlayer: DuckPlayerInternal,
    private val duckPlayerFeatureRepository: DuckPlayerFeatureRepository,
    private val pixel: Pixel,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    val viewState: StateFlow<ViewState> = duckPlayer.observeUserPreferences()
        .combine(duckPlayer.observeShouldOpenInNewTab()) { userPreferences, shouldOpenInNewTab ->
            val helpPageLink = duckPlayerFeatureRepository.getDuckPlayerDisabledHelpPageLink()
            if (duckPlayer.getDuckPlayerState() == DISABLED_WIH_HELP_LINK && helpPageLink?.isNotEmpty() == true) {
                DisabledWithHelpLink(userPreferences.privatePlayerMode, shouldOpenInNewTab, helpPageLink)
            } else {
                Enabled(userPreferences.privatePlayerMode, shouldOpenInNewTab)
            }
        }.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = Enabled(duckPlayer.getUserPreferences().privatePlayerMode, duckPlayer.shouldOpenDuckPlayerInNewTab()),
        )

    sealed class Command {
        data class OpenPlayerModeSelector(val current: PrivatePlayerMode) : Command()
        data class OpenLearnMore(val learnMoreLink: String) : Command()
        data class LaunchDuckPlayerContingencyPage(val helpPageLink: String) : Command()
    }

    sealed class ViewState(open val privatePlayerMode: PrivatePlayerMode = AlwaysAsk, open val openDuckPlayerInNewTab: OpenDuckPlayerInNewTab) {
        data class Enabled(
            override val privatePlayerMode: PrivatePlayerMode,
            override val openDuckPlayerInNewTab: OpenDuckPlayerInNewTab,
        ) : ViewState(privatePlayerMode, openDuckPlayerInNewTab)
        data class DisabledWithHelpLink(
            override val privatePlayerMode: PrivatePlayerMode,
            override val openDuckPlayerInNewTab: OpenDuckPlayerInNewTab,
            val helpPageLink: String,
        ) : ViewState(privatePlayerMode, openDuckPlayerInNewTab)
    }
    fun duckPlayerModeSelectorClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenPlayerModeSelector(duckPlayer.getUserPreferences().privatePlayerMode))
        }
    }

    fun onPlayerModeSelected(selectedPlayerMode: PrivatePlayerMode) {
        viewModelScope.launch {
            duckPlayer.setUserPreferences(overlayInteracted = false, privatePlayerMode = selectedPlayerMode.value).also {
                val pixelName = when (selectedPlayerMode) {
                    is PrivatePlayerMode.Enabled -> { DUCK_PLAYER_SETTINGS_ALWAYS_SETTINGS }
                    is AlwaysAsk -> { DUCK_PLAYER_SETTINGS_BACK_TO_DEFAULT }
                    is Disabled -> { DUCK_PLAYER_SETTINGS_NEVER_SETTINGS }
                }
                pixel.fire(pixelName)
            }
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

    fun onOpenDuckPlayerInNewTabToggled(checked: Boolean) {
        duckPlayer.setOpenInNewTab(checked)
    }
}
