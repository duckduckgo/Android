/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.location.ui

import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import javax.inject.Singleton

class LocationPermissionsViewModel(
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geoLocationPermissions: GeoLocationPermissions,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel
) : SiteLocationPermissionDialog.SiteLocationPermissionDialogListener, ViewModel() {

    data class ViewState(
        val locationPermissionEnabled: Boolean = false,
        val systemLocationPermissionDialogResponse: Boolean = false,
        val systemLocationPermissionGranted: Boolean = false,
        val locationPermissionEntities: List<LocationPermissionEntity> = emptyList()
    )

    sealed class Command {
        class ConfirmDeleteLocationPermission(val entity: LocationPermissionEntity) : Command()
        class EditLocationPermissions(val entity: LocationPermissionEntity) : Command()
    }

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData()
    val viewState: LiveData<ViewState> = _viewState
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val locationPermissions: LiveData<List<LocationPermissionEntity>> = locationPermissionsRepository.getLocationPermissionsAsync()
    private val locationPermissionsObserver = Observer<List<LocationPermissionEntity>> { onLocationPermissionsEntitiesChanged(it!!) }

    override fun onCleared() {
        super.onCleared()
        locationPermissions.removeObserver(locationPermissionsObserver)
    }

    init {
        _viewState.value = ViewState(
            locationPermissionEnabled = settingsDataStore.appLocationPermission
        )
        locationPermissions.observeForever(locationPermissionsObserver)
    }

    fun loadLocationPermissions(systemLocationPermissionEnabled: Boolean) {
        _viewState.value = _viewState.value?.copy(
            systemLocationPermissionGranted = systemLocationPermissionEnabled
        )
    }

    private fun onLocationPermissionsEntitiesChanged(entities: List<LocationPermissionEntity>) {
        _viewState.value = _viewState.value?.copy(
            locationPermissionEntities = entities
        )
    }

    fun onDeleteRequested(entity: LocationPermissionEntity) {
        command.value = Command.ConfirmDeleteLocationPermission(entity)
    }

    fun onEditRequested(entity: LocationPermissionEntity) {
        command.value = Command.EditLocationPermissions(entity)
    }

    fun delete(entity: LocationPermissionEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            locationPermissionsRepository.deletePermission(entity.domain)
            geoLocationPermissions.clear(entity.domain)
        }
    }

    fun onLocationPermissionToggled(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io()) {
            settingsDataStore.appLocationPermission = enabled
            if (!enabled) {
                geoLocationPermissions.clearAll()
            }
        }
        _viewState.value = _viewState.value?.copy(locationPermissionEnabled = enabled)
    }

    override fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType) {
        when (permission) {
            LocationPermissionType.ALLOW_ALWAYS -> {
                pixel.fire(Pixel.PixelName.PRECISE_LOCATION_SITE_DIALOG_ALLOW_ALWAYS)
                geoLocationPermissions.allow(domain)
                viewModelScope.launch {
                    locationPermissionsRepository.savePermission(domain, permission)
                }
            }
            LocationPermissionType.DENY_ALWAYS -> {
                geoLocationPermissions.clear(domain)
                pixel.fire(Pixel.PixelName.PRECISE_LOCATION_SITE_DIALOG_DENY_ALWAYS)
                viewModelScope.launch {
                    locationPermissionsRepository.savePermission(domain, permission)
                }
            }
            else -> {
                geoLocationPermissions.clear(domain)
                viewModelScope.launch {
                    locationPermissionsRepository.deletePermission(domain)
                }
            }
        }
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
class LocationPermissionsViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideLocationPermissionsViewModelFactory(
        locationPermissionsRepository: LocationPermissionsRepository,
        geoLocationPermissions: GeoLocationPermissions,
        dispatcherProvider: DispatcherProvider,
        settingsDataStore: SettingsDataStore,
        pixel: Pixel
    ): ViewModelFactoryPlugin {
        return LocationPermissionsViewModelFactory(locationPermissionsRepository, geoLocationPermissions, dispatcherProvider, settingsDataStore, pixel)
    }
}

private class LocationPermissionsViewModelFactory(
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geoLocationPermissions: GeoLocationPermissions,
    private val dispatcherProvider: DispatcherProvider,
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(LocationPermissionsViewModel::class.java) -> (LocationPermissionsViewModel(locationPermissionsRepository, geoLocationPermissions, dispatcherProvider, settingsDataStore, pixel) as T)
                else -> null
            }
        }
    }
}
