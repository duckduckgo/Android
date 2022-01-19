/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.dev.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.traces.api.StartupTraces
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class DevSettingsViewModel @Inject constructor(
    private val devSettingsDataStore: DevSettingsDataStore,
    private val startupTraces: StartupTraces,
) : ViewModel() {

    data class ViewState(
        val nextTdsEnabled: Boolean = false,
        val startupTraceEnabled: Boolean = false,
    )

    sealed class Command {
        object SendTdsIntent : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun start() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    nextTdsEnabled = devSettingsDataStore.nextTdsEnabled,
                    startupTraceEnabled = startupTraces.isTraceEnabled
                )
            )
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onNextTdsToggled(nextTds: Boolean) {
        Timber.i("User toggled next tds, is now enabled: $nextTds")
        devSettingsDataStore.nextTdsEnabled = nextTds
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(nextTdsEnabled = nextTds))
            command.send(Command.SendTdsIntent)
        }
    }

    fun onStartupTraceToggled(value: Boolean) {
        Timber.v("User toggled startup trace, is now enabled: $value")
        startupTraces.isTraceEnabled = value
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(startupTraceEnabled = value))
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}

@ContributesMultibinding(AppScope::class)
class SettingsViewModelFactory @Inject constructor(
    private val devSettingsDataStore: Provider<DevSettingsDataStore>,
    private val startupTraces: Provider<StartupTraces>,
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(DevSettingsViewModel::class.java) ->
                    DevSettingsViewModel(devSettingsDataStore.get(), startupTraces.get()) as T
                else -> null
            }
        }
    }
}
