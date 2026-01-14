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
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.FireClearOption.DUCKAI_CHATS
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DataClearingSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val fireAnimationLoader: FireAnimationLoader,
    private val pixel: Pixel,
    private val duckChat: DuckChat,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val fireDataStore: FireDataStore,
    private val dispatcherProvider: DispatcherProvider,
    fireproofWebsiteRepository: FireproofWebsiteRepository,
) : ViewModel() {

    data class ViewState(
        val selectedFireAnimation: FireAnimation = FireAnimation.HeroFire,
        val clearDuckAiData: Boolean = false,
        val showClearDuckAiDataSetting: Boolean = false,
        val fireproofWebsitesCount: Int = 0,
        val automaticallyClearingEnabled: Boolean = false,
    )

    sealed class Command {
        data object LaunchFireproofWebsites : Command()
        data class LaunchFireAnimationSettings(val animation: FireAnimation) : Command()
        data object LaunchFireDialog : Command()
        data object LaunchAutomaticDataClearingSettings : Command()
    }

    private val _viewState = MutableStateFlow(ViewState())
    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    val viewState: Flow<ViewState> = combine(
        _viewState,
        fireproofWebsiteRepository.getFireproofWebsites().asFlow(),
        fireDataStore.getAutomaticClearOptionsFlow(),
    ) { state, fireproofSites, automaticClearOptions ->
        state.copy(
            fireproofWebsitesCount = fireproofSites.size,
            automaticallyClearingEnabled = automaticClearOptions.isNotEmpty(),
        )
    }

    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            _viewState.update {
                it.copy(
                    selectedFireAnimation = settingsDataStore.selectedFireAnimation,
                    clearDuckAiData = fireDataStore.isManualClearOptionSelected(DUCKAI_CHATS),
                    showClearDuckAiDataSetting = duckChat.wasOpenedBefore() && duckAiFeatureState.showClearDuckAIChatHistory.value,
                )
            }
        }
    }

    fun onFireproofWebsitesClicked() {
        viewModelScope.launch { _commands.send(Command.LaunchFireproofWebsites) }
        pixel.fire(AppPixelName.SETTINGS_FIREPROOF_WEBSITES_PRESSED)
    }

    fun onAutomaticDataClearingClicked() {
        viewModelScope.launch { _commands.send(Command.LaunchAutomaticDataClearingSettings) }
    }

    fun userRequestedToChangeFireAnimation() {
        viewModelScope.launch { _commands.send(Command.LaunchFireAnimationSettings(_viewState.value.selectedFireAnimation)) }
        pixel.fire(AppPixelName.FIRE_ANIMATION_SETTINGS_OPENED)
    }

    fun onFireAnimationSelected(selectedFireAnimation: FireAnimation) {
        if (settingsDataStore.isCurrentlySelected(selectedFireAnimation)) {
            logcat(VERBOSE) { "User selected same thing they already have set: $selectedFireAnimation; no need to do anything else" }
            return
        }
        settingsDataStore.selectedFireAnimation = selectedFireAnimation
        fireAnimationLoader.preloadSelectedAnimation()
        _viewState.update { it.copy(selectedFireAnimation = selectedFireAnimation) }
        pixel.fire(AppPixelName.FIRE_ANIMATION_NEW_SELECTED, mapOf(Pixel.PixelParameter.FIRE_ANIMATION to selectedFireAnimation.getPixelValue()))
    }

    fun onLaunchedFromNotification(pixelName: String) {
        pixel.fire(pixelName)
    }

    fun onClearDuckAiDataToggled(isChecked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            _viewState.update { it.copy(clearDuckAiData = isChecked) }

            if (isChecked) {
                fireDataStore.addManualClearOption(DUCKAI_CHATS)
                pixel.fire(AppPixelName.SETTINGS_CLEAR_DUCK_AI_DATA_TOGGLED_ON)
            } else {
                fireDataStore.removeManualClearOption(DUCKAI_CHATS)
                pixel.fire(AppPixelName.SETTINGS_CLEAR_DUCK_AI_DATA_TOGGLED_OFF)
            }
        }
    }

    fun onClearDataActionClicked() {
        viewModelScope.launch { _commands.send(Command.LaunchFireDialog) }
    }
}
