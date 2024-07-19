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
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_ON
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_ON
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_OFF
import com.duckduckgo.voice.impl.VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_ON
import com.duckduckgo.voice.store.VoiceSearchRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class GeneralSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val history: NavigationHistory,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchRepository: VoiceSearchRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val autoCompleteSuggestionsEnabled: Boolean = true,
        val autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled: Boolean = true,
        val storeHistoryEnabled: Boolean = false,
        val showVoiceSearch: Boolean = false,
        val voiceSearchEnabled: Boolean = false,
    )

    private val viewState = MutableStateFlow(ViewState())

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch(dispatcherProvider.io()) {
            val autoCompleteEnabled = settingsDataStore.autoCompleteSuggestionsEnabled
            if (!autoCompleteEnabled) {
                history.setHistoryUserEnabled(false)
            }
            withContext(dispatcherProvider.main()) {
                viewState.emit(
                    ViewState(
                        autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
                        autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = history.isHistoryUserEnabled(),
                        storeHistoryEnabled = history.isHistoryFeatureAvailable(),
                        showVoiceSearch = voiceSearchAvailability.isVoiceSearchSupported,
                        voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable,
                    ),
                )
            }
        }
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.autoCompleteSuggestionsEnabled = enabled
            if (!enabled) {
                viewModelScope.launch() {
                    history.setHistoryUserEnabled(false)
                }
            }
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_GENERAL_SETTINGS_TOGGLED_OFF)
            }
            withContext(dispatcherProvider.main()) {
                viewState.emit(
                    currentViewState().copy(
                        autoCompleteSuggestionsEnabled = enabled,
                        autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = history.isHistoryUserEnabled(),
                    ),
                )
            }
        }
    }

    fun onAutocompleteRecentlyVisitedSitesSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete recently visited sites setting, is now enabled: $enabled")
        viewModelScope.launch(dispatcherProvider.io()) {
            history.setHistoryUserEnabled(enabled)
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_RECENT_SITES_GENERAL_SETTINGS_TOGGLED_OFF)
            }
            withContext(dispatcherProvider.main()) {
                viewState.emit(currentViewState().copy(autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = enabled))
            }
        }
    }

    fun onVoiceSearchChanged(checked: Boolean) {
        voiceSearchRepository.setVoiceSearchUserEnabled(checked)
        if (checked) {
            voiceSearchRepository.resetVoiceSearchDismissed()
            pixel.fire(VOICE_SEARCH_GENERAL_SETTINGS_ON)
        } else {
            pixel.fire(VOICE_SEARCH_GENERAL_SETTINGS_OFF)
        }
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    voiceSearchEnabled = voiceSearchAvailability.isVoiceSearchAvailable,
                ),
            )
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
