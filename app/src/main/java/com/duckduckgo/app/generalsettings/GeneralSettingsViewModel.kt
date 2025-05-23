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

package com.duckduckgo.app.generalsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchFeature
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_ON
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_ON
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_OFF
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_ON
import com.duckduckgo.voice.store.VoiceSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class GeneralSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val history: NavigationHistory,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
    private val showOnAppLaunchOptionDataStore: ShowOnAppLaunchOptionDataStore,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val maliciousSiteProtection: MaliciousSiteProtection,
) : ViewModel() {

    data class ViewState(
        val autoCompleteSuggestionsEnabled: Boolean,
        val autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled: Boolean,
        val storeHistoryEnabled: Boolean,
        val showVoiceSearch: Boolean,
        val voiceSearchEnabled: Boolean,
        val isShowOnAppLaunchOptionVisible: Boolean,
        val showOnAppLaunchSelectedOption: ShowOnAppLaunchOption,
        val maliciousSiteProtectionEnabled: Boolean,
        val maliciousSiteProtectionFeatureAvailable: Boolean,
    )

    sealed class Command {
        data object LaunchShowOnAppLaunchScreen : Command()
        data object OpenMaliciousLearnMore : Command()
    }

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow()

    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            val autoCompleteEnabled = settingsDataStore.autoCompleteSuggestionsEnabled
            if (!autoCompleteEnabled) {
                history.setHistoryUserEnabled(false)
            }
            _viewState.value = ViewState(
                autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
                autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = history.isHistoryUserEnabled(),
                storeHistoryEnabled = history.isHistoryFeatureAvailable(),
                showVoiceSearch = voiceSearchAvailability.isVoiceSearchSupported,
                voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable,
                isShowOnAppLaunchOptionVisible = showOnAppLaunchFeature.self().isEnabled(),
                showOnAppLaunchSelectedOption = showOnAppLaunchOptionDataStore.optionFlow.first(),
                maliciousSiteProtectionEnabled = settingsDataStore.maliciousSiteProtectionEnabled,
                maliciousSiteProtectionFeatureAvailable =
                androidBrowserConfigFeature.enableMaliciousSiteProtection().isEnabled() && maliciousSiteProtection.isFeatureEnabled(),
            )
        }

        observeShowOnAppLaunchOption()
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        logcat(INFO) { "User changed autocomplete setting, is now enabled: $enabled" }
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.autoCompleteSuggestionsEnabled = enabled
            if (!enabled) {
                history.setHistoryUserEnabled(false)
            }
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_OFF)
            }
            _viewState.value = _viewState.value?.copy(
                autoCompleteSuggestionsEnabled = enabled,
                autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = history.isHistoryUserEnabled(),
            )
        }
    }

    fun onAutocompleteRecentlyVisitedSitesSettingChanged(enabled: Boolean) {
        logcat(INFO) { "User changed autocomplete recently visited sites setting, is now enabled: $enabled" }
        viewModelScope.launch(dispatcherProvider.io()) {
            history.setHistoryUserEnabled(enabled)
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_OFF)
            }
            _viewState.value = _viewState.value?.copy(autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = enabled)
        }
    }

    fun onVoiceSearchChanged(checked: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            voiceSearchRepository.setVoiceSearchUserEnabled(checked)
            if (checked) {
                voiceSearchRepository.resetVoiceSearchDismissed()
                pixel.fire(VOICE_SEARCH_GENERAL_SETTINGS_ON)
            } else {
                pixel.fire(VOICE_SEARCH_GENERAL_SETTINGS_OFF)
            }
            _viewState.value = _viewState.value?.copy(voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable)
        }
    }

    fun onShowOnAppLaunchButtonClick() {
        sendCommand(Command.LaunchShowOnAppLaunchScreen)
        pixel.fire(AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_PRESSED)
    }

    fun onMaliciousSiteProtectionSettingChanged(enabled: Boolean) {
        logcat(INFO) { "User changed malicious site setting, is now enabled: $enabled" }
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.maliciousSiteProtectionEnabled = enabled
            pixel.fire(
                AppPixelName.MALICIOUS_SITE_PROTECTION_SETTING_TOGGLED,
                mapOf(NEW_STATE to enabled.toString()),
            )
            _viewState.value = _viewState.value?.copy(
                maliciousSiteProtectionEnabled = enabled,
            )
        }
    }

    fun maliciousSiteLearnMoreClicked() {
        sendCommand(Command.OpenMaliciousLearnMore)
    }

    private fun observeShowOnAppLaunchOption() {
        showOnAppLaunchOptionDataStore.optionFlow
            .onEach { showOnAppLaunchOption ->
                _viewState.value?.let { state -> _viewState.update { state.copy(showOnAppLaunchSelectedOption = showOnAppLaunchOption) } }
            }.launchIn(viewModelScope)
    }

    private fun sendCommand(newCommand: Command) {
        viewModelScope.launch {
            _commands.send(newCommand)
        }
    }

    companion object {
        private const val NEW_STATE = "newState"
    }
}
