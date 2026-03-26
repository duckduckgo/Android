/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.site.permissions.impl.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.LaunchWebsiteAllowed
import com.duckduckgo.site.permissions.impl.ui.SitePermissionsViewModel.Command.ShowRemovedAllConfirmationSnackbar
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SitePermissionsViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var cachedAllowedSites: List<SitePermissionAllowedEntity> = listOf()

    data class ViewState(
        val askLocationEnabled: Boolean = true,
        val askCameraEnabled: Boolean = false,
        val askMicEnabled: Boolean = true,
        val askDrmEnabled: Boolean = true,
        val sitesPermissionsAllowed: List<SitePermissionsEntity> = listOf(),
    )

    sealed class Command {
        class ShowRemovedAllConfirmationSnackbar(val removedSitePermissions: List<SitePermissionsEntity>) : Command()
        class LaunchWebsiteAllowed(val domain: String) : Command()
    }

    init {
        _viewState.value = ViewState(
            // askLocationEnabled = settingsDataStore.appLocationPermission,
            askLocationEnabled = sitePermissionsRepository.askLocationEnabled,
            askCameraEnabled = sitePermissionsRepository.askCameraEnabled,
            askMicEnabled = sitePermissionsRepository.askMicEnabled,
            askDrmEnabled = sitePermissionsRepository.askDrmEnabled,
        )
    }

    fun allowedSites() {
        viewModelScope.launch {
            sitePermissionsRepository.sitePermissionsWebsitesFlow().collect {
                _viewState.emit(
                    _viewState.value.copy(
                        sitesPermissionsAllowed = it,
                    ),
                )
            }
        }
    }

    fun permissionToggleSelected(
        isChecked: Boolean,
        textRes: Int,
    ) {
        when (textRes) {
            R.string.sitePermissionsSettingsLocation -> {
                sitePermissionsRepository.askLocationEnabled = isChecked
                _viewState.value = _viewState.value.copy(askLocationEnabled = isChecked)
            }

            R.string.sitePermissionsSettingsCamera -> {
                sitePermissionsRepository.askCameraEnabled = isChecked
                _viewState.value = _viewState.value.copy(askCameraEnabled = isChecked)
            }

            R.string.sitePermissionsSettingsMicrophone -> {
                sitePermissionsRepository.askMicEnabled = isChecked
                _viewState.value = _viewState.value.copy(askMicEnabled = isChecked)
            }

            R.string.sitePermissionsSettingsDRM -> {
                sitePermissionsRepository.askDrmEnabled = isChecked
                _viewState.value = _viewState.value.copy(askDrmEnabled = isChecked)
            }
        }
    }

    fun allowedSiteSelected(domain: String) {
        viewModelScope.launch {
            _commands.send(LaunchWebsiteAllowed(domain))
        }
    }

    fun removeAllSitesSelected() {
        val sitePermissions = _viewState.value.sitesPermissionsAllowed.toMutableList()
        viewModelScope.launch(dispatcherProvider.io()) {
            sitePermissionsRepository.sitePermissionsAllowedFlow().collect { sitePermissionsAllowed ->
                sitePermissionsRepository.deleteAll()
                _commands.send(ShowRemovedAllConfirmationSnackbar(sitePermissions))
                cachedAllowedSites = sitePermissionsAllowed
            }
        }
    }

    fun onSnackBarUndoRemoveAllWebsites(removedSitePermissions: List<SitePermissionsEntity>) {
        viewModelScope.launch(dispatcherProvider.io()) {
            sitePermissionsRepository.undoDeleteAll(removedSitePermissions, cachedAllowedSites)
        }
    }
}
