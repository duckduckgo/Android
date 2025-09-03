/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.privatesearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_HISTORY_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_HISTORY_TOGGLED_ON
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_TOGGLED_OFF
import com.duckduckgo.app.pixels.AppPixelName.AUTOCOMPLETE_TOGGLED_ON
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.history.api.NavigationHistory
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class PrivateSearchViewModel @Inject constructor(
    private val autoCompleteSettings: AutoCompleteSettings,
    private val pixel: Pixel,
    private val history: NavigationHistory,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val autoCompleteSuggestionsEnabled: Boolean,
        val autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled: Boolean,
        val storeHistoryEnabled: Boolean,
    )

    sealed class Command {
        data object LaunchCustomizeSearchWebPage : Command()
    }

    private val _viewState = MutableStateFlow<ViewState?>(null)
    val viewState = _viewState.asStateFlow()

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch(dispatcherProvider.io()) {
            val autoCompleteEnabled = autoCompleteSettings.autoCompleteSuggestionsEnabled
            if (!autoCompleteEnabled) {
                history.setHistoryUserEnabled(false)
            }
            _viewState.value = ViewState(
                autoCompleteSuggestionsEnabled = autoCompleteSettings.autoCompleteSuggestionsEnabled,
                autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = history.isHistoryUserEnabled(),
                storeHistoryEnabled = history.isHistoryFeatureAvailable(),
            )
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        logcat(INFO) { "User changed autocomplete setting, is now enabled: $enabled" }
        viewModelScope.launch(dispatcherProvider.io()) {
            autoCompleteSettings.autoCompleteSuggestionsEnabled = enabled
            if (!enabled) {
                history.setHistoryUserEnabled(false)
            }
            if (enabled) {
                pixel.fire(AUTOCOMPLETE_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_TOGGLED_OFF)
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
                pixel.fire(AUTOCOMPLETE_HISTORY_TOGGLED_ON)
            } else {
                pixel.fire(AUTOCOMPLETE_HISTORY_TOGGLED_OFF)
            }
            _viewState.value = _viewState.value?.copy(autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = enabled)
        }
    }

    fun onPrivateSearchMoreSearchSettingsClicked() {
        viewModelScope.launch { command.send(Command.LaunchCustomizeSearchWebPage) }
        pixel.fire(AppPixelName.SETTINGS_PRIVATE_SEARCH_MORE_SEARCH_SETTINGS_PRESSED)
    }
}
