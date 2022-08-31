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

package com.duckduckgo.app.sitepermissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.location.GeoLocationPermissionsManager
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.sitepermissions.SitePermissionsViewModel.Command.LaunchWebsiteAllowed
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
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
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geolocationPermissions: GeoLocationPermissionsManager,
    private val settingsDataStore: SettingsDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    data class ViewState(
        val askLocationEnabled: Boolean = true,
        val askCameraEnabled: Boolean = false,
        val askMicEnabled: Boolean = true,
        val sitesPermissionsAllowed: List<SitePermissionsEntity> = listOf(),
        val locationPermissionsAllowed: List<LocationPermissionEntity> = listOf()
    )

    sealed class Command {
        class ShowRemovedAllConfirmationSnackbar(
            val removedSitePermissions: List<SitePermissionsEntity>,
            val removedLocationPermissions: List<LocationPermissionEntity>
        ) : Command()
        class LaunchWebsiteAllowed(val domain: String) : Command()
    }

    init {
        _viewState.value = ViewState(
            askLocationEnabled = settingsDataStore.appLocationPermission,
            askCameraEnabled = sitePermissionsRepository.askCameraEnabled,
            askMicEnabled = sitePermissionsRepository.askMicEnabled
        )
    }

    fun allowedSites() {
        viewModelScope.launch {
            locationPermissionsRepository.getLocationPermissionsFlow().collect { locationPermissionsList ->
                sitePermissionsRepository.sitePermissionsWebsitesFlow().collect { sitePermissionsList ->
                    _viewState.emit(
                        _viewState.value.copy(
                            sitesPermissionsAllowed = sitePermissionsList,
                            locationPermissionsAllowed = locationPermissionsList
                        )
                    )
                }
            }
        }
    }

    fun permissionToggleSelected(
        isChecked: Boolean,
        textRes: Int
    ) {
        when (textRes) {
            R.string.sitePermissionsSettingsLocation -> {
                settingsDataStore.appLocationPermission = isChecked
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
        }
    }

    fun allowedSiteSelected(domain: String) {
        viewModelScope.launch {
            _commands.send(LaunchWebsiteAllowed(domain))
        }
    }

    fun removeAllSitesSelected() {
        viewModelScope.launch(dispatcherProvider.io()) {
            geolocationPermissions.clearAll()
            sitePermissionsRepository.deleteAll()
        }
    }

    fun onSnackBarUndoRemoveAllWebsites(
        removedSitePermissions: List<SitePermissionsEntity>,
        removedLocationPermissions: List<LocationPermissionEntity>
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            sitePermissionsRepository.undoDeleteAll(removedSitePermissions)
            geolocationPermissions.undoClearAll(removedLocationPermissions)
        }
    }
}
