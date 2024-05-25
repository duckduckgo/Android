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

package com.duckduckgo.app.searchengine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.settings.db.SettingsSharedPreferences
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SearchEngineViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {
    internal val useCustomStartPage get() = settingsDataStore.customStartPage
    internal val searxInstance get() = settingsDataStore.searxInstance

    data class ViewState(
        val useCustomStartPage: Boolean = false,
        val selectedStartPageUrl: String = "",

        val selectedSearchEngine: SearchEngine = DuckDuckGoSearchEngine,
        val selectedSearxInstance: String = SettingsSharedPreferences.DEFAULT_SEARX_INSTANCE,
    )

    sealed class Command {
        data class LaunchSearchEngineSettings(val searchEngine: SearchEngine) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, DROP_OLDEST)

    fun viewState(): Flow<ViewState> = viewState.onStart {
        viewModelScope.launch {
            viewState.emit(
                ViewState(
                    useCustomStartPage = settingsDataStore.customStartPage,
                    selectedStartPageUrl = settingsDataStore.startPage ?: "",

                    selectedSearchEngine = settingsDataStore.searchEngine,
                    selectedSearxInstance = settingsDataStore.searxInstance,
                ),
            )
        }
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun userRequestedToChangeSearchEngine() {
        viewModelScope.launch { command.send(Command.LaunchSearchEngineSettings(viewState.value.selectedSearchEngine)) }
    }

    fun onUseStartPageUpdated(value: Boolean) {
        if (settingsDataStore.customStartPage == value) {
            Timber.v("User selected same thing they already have set: $value; no need to do anything else")
            return
        }
        settingsDataStore.customStartPage = value
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(useCustomStartPage = value))
        }
    }

    fun onStartPageUrlUpdated(value: String) {
        if (settingsDataStore.startPage == value) {
            Timber.v("User selected same thing they already have set: $value; no need to do anything else")
            return
        }
        settingsDataStore.startPage = value
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(selectedStartPageUrl = value))
        }
    }

    fun onSearchEngineSelected(selectedSearchEngine: SearchEngine) {
        if (settingsDataStore.isCurrentlySelected(selectedSearchEngine)) {
            Timber.v("User selected same thing they already have set: $selectedSearchEngine; no need to do anything else")
            return
        }
        settingsDataStore.searchEngine = selectedSearchEngine
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(selectedSearchEngine = selectedSearchEngine))
        }
    }

    fun onSearxInstanceUpdated(searxInstance: String) {
        if (settingsDataStore.searxInstance == searxInstance) {
            Timber.v("User selected same thing they already have set: $searxInstance; no need to do anything else")
            return
        }
        settingsDataStore.searxInstance = searxInstance
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(selectedSearxInstance = searxInstance))
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
