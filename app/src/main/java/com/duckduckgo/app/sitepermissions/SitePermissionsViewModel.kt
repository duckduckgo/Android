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
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.sitepermissions.SitePermissionsViewModel.Command.LaunchWebsiteAllowed
import com.duckduckgo.app.sitepermissions.SitePermissionsViewModel.Command.ShowRemovedAllConfirmationSnackbar
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SitePermissionsViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geolocationPermissions: GeoLocationPermissions,
    private val settingsDataStore: SettingsDataStore,
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
        val locationPermissionsAllowed: List<LocationPermissionEntity> = listOf(),
    )

    sealed class Command {
        class ShowRemovedAllConfirmationSnackbar(
            val removedSitePermissions: List<SitePermissionsEntity>,
            val removedLocationPermissions: List<LocationPermissionEntity>,
        ) : Command()
        class LaunchWebsiteAllowed(val domain: String) : Command()
    }

    init {
        _viewState.value = ViewState(
            askLocationEnabled = settingsDataStore.appLocationPermission,
            askCameraEnabled = sitePermissionsRepository.askCameraEnabled,
            askMicEnabled = sitePermissionsRepository.askMicEnabled,
            askDrmEnabled = sitePermissionsRepository.askDrmEnabled,
        )
    }

    fun allowedSites() {
        viewModelScope.launch {
            val locationsPermissionsFlow = locationPermissionsRepository.getLocationPermissionsFlow()
            val sitePermissionsFlow = sitePermissionsRepository.sitePermissionsWebsitesFlow()

            sitePermissionsFlow.combine(locationsPermissionsFlow) { sitePermissionsList, locationPermissionsList ->
                Pair(sitePermissionsList, locationPermissionsList)
            }.collect {
                _viewState.emit(
                    _viewState.value.copy(
                        sitesPermissionsAllowed = it.first,
                        locationPermissionsAllowed = it.second,
                    ),
                )
            }
        }
    }

    fun combineAllPermissions(locationPermissions: List<LocationPermissionEntity>, sitePermissions: List<SitePermissionsEntity>): List<String> =
        locationPermissions.map { it.domain }.union(sitePermissions.map { it.domain }).toList()

    fun permissionToggleSelected(
        isChecked: Boolean,
        textRes: Int,
    ) {
        when (textRes) {
            R.string.sitePermissionsSettingsLocation -> {
                settingsDataStore.appLocationPermission = isChecked
                _viewState.value = _viewState.value.copy(askLocationEnabled = isChecked)
                removeLocationSites()
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

    private fun removeLocationSites() {
        viewModelScope.launch {
            geolocationPermissions.clearAll()
        }
    }

    fun allowedSiteSelected(domain: String) {
        viewModelScope.launch {
            _commands.send(LaunchWebsiteAllowed(domain))
        }
    }

    fun removeAllSitesSelected() {
        val sitePermissions = _viewState.value.sitesPermissionsAllowed.toMutableList()
        val locationPermissions = _viewState.value.locationPermissionsAllowed.toMutableList()
        viewModelScope.launch(dispatcherProvider.io()) {
            sitePermissionsRepository.sitePermissionsAllowedFlow().collect { sitePermissionsAllowed ->
                geolocationPermissions.clearAll()
                sitePermissionsRepository.deleteAll()
                _commands.send(ShowRemovedAllConfirmationSnackbar(sitePermissions, locationPermissions))
                cachedAllowedSites = sitePermissionsAllowed
            }
        }
    }

    fun onSnackBarUndoRemoveAllWebsites(
        removedSitePermissions: List<SitePermissionsEntity>,
        removedLocationPermissions: List<LocationPermissionEntity>,
    ) {
        viewModelScope.launch(dispatcherProvider.io()) {
            sitePermissionsRepository.undoDeleteAll(removedSitePermissions, cachedAllowedSites)
            geolocationPermissions.undoClearAll(removedLocationPermissions)
        }
    }
}
