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
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class PrivateSearchViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val autoCompleteSuggestionsEnabled: Boolean = true,
    )

    sealed class Command {
        object LaunchCustomizeSearchWebPage : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch {
            viewState.emit(ViewState(autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled))
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
        viewModelScope.launch { viewState.emit(currentViewState().copy(autoCompleteSuggestionsEnabled = enabled)) }
    }

    fun onPrivateSearchMoreSearchSettingsClicked() {
        viewModelScope.launch { command.send(Command.LaunchCustomizeSearchWebPage) }
        pixel.fire(AppPixelName.SETTINGS_PRIVATE_SEARCH_MORE_SEARCH_SETTINGS_PRESSED)
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}