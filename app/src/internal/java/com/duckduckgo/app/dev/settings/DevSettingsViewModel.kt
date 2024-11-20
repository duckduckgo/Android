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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.dev.settings.db.UAOverride
import com.duckduckgo.app.survey.api.SurveyEndpointDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.traces.api.StartupTraces
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class DevSettingsViewModel @Inject constructor(
    private val devSettingsDataStore: DevSettingsDataStore,
    private val startupTraces: StartupTraces,
    private val userAgentProvider: UserAgentProvider,
    private val savedSitesRepository: SavedSitesRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val surveyEndpointDataStore: SurveyEndpointDataStore,
) : ViewModel() {

    data class ViewState(
        val startupTraceEnabled: Boolean = false,
        val overrideUA: Boolean = false,
        val userAgent: String = "",
        val useSandboxSurvey: Boolean = false,
    )

    sealed class Command {
        object SendTdsIntent : Command()
        object OpenUASelector : Command()
        object ShowSavedSitesClearedConfirmation : Command()
        object ChangePrivacyConfigUrl : Command()
        object CustomTabs : Command()
        data object Notifications : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun start() {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    startupTraceEnabled = startupTraces.isTraceEnabled,
                    overrideUA = devSettingsDataStore.overrideUA,
                    userAgent = userAgentProvider.userAgent("", false),
                    useSandboxSurvey = surveyEndpointDataStore.useSurveyCustomEnvironmentUrl,
                ),
            )
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onStartupTraceToggled(value: Boolean) {
        Timber.v("User toggled startup trace, is now enabled: $value")
        startupTraces.isTraceEnabled = value
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(startupTraceEnabled = value))
        }
    }

    fun onOverrideUAToggled(enabled: Boolean) {
        devSettingsDataStore.overrideUA = enabled
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(overrideUA = enabled))
        }
    }

    fun onSandboxSurveyToggled(enabled: Boolean) {
        surveyEndpointDataStore.useSurveyCustomEnvironmentUrl = enabled
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(useSandboxSurvey = enabled))
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onUserAgentSelectorClicked() {
        viewModelScope.launch { command.send(Command.OpenUASelector) }
    }

    fun onRemotePrivacyUrlClicked() {
        viewModelScope.launch { command.send(Command.ChangePrivacyConfigUrl) }
    }

    fun customTabsClicked() {
        viewModelScope.launch { command.send(Command.CustomTabs) }
    }

    fun onUserAgentSelected(userAgent: UAOverride) {
        devSettingsDataStore.selectedUA = userAgent
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(userAgent = userAgentProvider.userAgent("", false)))
        }
    }

    fun clearSavedSites() {
        viewModelScope.launch(dispatcherProvider.io()) {
            savedSitesRepository.deleteAll()
            command.send(Command.ShowSavedSitesClearedConfirmation)
        }
    }

    fun notificationsClicked() {
        viewModelScope.launch { command.send(Command.Notifications) }
    }
}
