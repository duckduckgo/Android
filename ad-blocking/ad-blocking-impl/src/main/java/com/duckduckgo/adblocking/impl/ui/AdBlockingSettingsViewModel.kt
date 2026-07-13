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
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode
import com.duckduckgo.adblocking.api.duckplayer.PrivatePlayerMode.AlwaysAsk
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_DISABLED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_DISABLED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_ENABLED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_ENABLED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_SETTINGS_OPENED_DAILY
import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.ui.AdBlockingSettingsViewModel.Command.OpenDuckPlayerSettings
import com.duckduckgo.adblocking.impl.ui.AdBlockingSettingsViewModel.Command.OpenLearnMore
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class AdBlockingSettingsViewModel @Inject constructor(
    statusChecker: AdBlockingStatusChecker,
    feature: AdBlockingExtensionFeature,
    private val repository: AdBlockingSettingsRepository,
    private val pixel: Pixel,
    duckPlayer: DuckPlayer,
) : ViewModel() {

    init {
        pixel.fire(AD_BLOCKING_SETTINGS_OPENED_DAILY, type = Pixel.PixelType.Daily())
        pixel.fire(AD_BLOCKING_SETTINGS_OPENED_COUNT)
    }

    data class ViewState(
        val isEnabled: Boolean = false,
        val showConsentDescription: Boolean? = null,
        val duckPlayerMode: PrivatePlayerMode = AlwaysAsk,
        val isContingencyMode: Boolean = false,
        val isStatusIndicatorOn: Boolean = false,
    )

    sealed class Command {
        data class OpenLearnMore(val url: String) : Command()
        data object OpenDuckPlayerSettings : Command()
    }

    val viewState: StateFlow<ViewState> = combine(
        statusChecker.observeState(),
        duckPlayer.observeUserPreferences(),
        feature.adBlockingUXImprovements().enabled(),
        feature.enableContingencyMode().enabled(),
    ) { state, duckPlayerPreferences, uxImprovements, contingencyModeOn ->
        val isEnabled = state is AdBlockingState.Enabled
        val isContingencyMode = uxImprovements && contingencyModeOn
        ViewState(
            isEnabled = isEnabled,
            showConsentDescription = state !is AdBlockingState.Enabled.Default,
            isContingencyMode = isContingencyMode,
            isStatusIndicatorOn = isEnabled && !isContingencyMode,
            duckPlayerMode = duckPlayerPreferences.privatePlayerMode,
        )
    }
        .stateIn(
            viewModelScope,
            started = WhileSubscribed(),
            initialValue = ViewState(),
        )

    private val commandChannel =
        Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: Flow<Command> = commandChannel.receiveAsFlow()

    fun onBlockAdsToggled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(enabled)
            if (enabled) {
                pixel.fire(AD_BLOCKING_ENABLED_DAILY, type = Pixel.PixelType.Daily())
                pixel.fire(AD_BLOCKING_ENABLED_COUNT)
            } else {
                pixel.fire(AD_BLOCKING_DISABLED_DAILY, type = Pixel.PixelType.Daily())
                pixel.fire(AD_BLOCKING_DISABLED_COUNT)
            }
        }
    }

    fun onLearnMoreClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenLearnMore(LEARN_MORE_URL))
        }
    }

    fun onDuckPlayerClicked() {
        viewModelScope.launch {
            commandChannel.send(OpenDuckPlayerSettings)
        }
    }

    private companion object {
        const val LEARN_MORE_URL = "https://duckduckgo.com/duckduckgo-help-pages/ad-blocking"
    }
}
