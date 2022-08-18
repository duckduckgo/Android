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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.location.GeoLocationPermissionsManager
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SitePermissionsViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val locationPermissionsRepository: SitePermissionsRepository,
    private val geolocationPermissions: GeoLocationPermissionsManager,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    data class ViewState(
        val askLocationEnabled: Boolean = true,
        val askCameraEnabled: Boolean = true,
        val askMicEnabled: Boolean = true,
        val sitesAllowed: List<String> = listOf()
    )

    sealed class Command {
        class ConfirmRemoveAllAllowedSites(val removedSites: List<String>) : Command()
    }

    init {
        _viewState.value = ViewState(
            askLocationEnabled = settingsDataStore.appLocationPermission,
            askCameraEnabled = sitePermissionsRepository.askCameraEnabled,
            askMicEnabled = sitePermissionsRepository.askMicEnabled
        )
    }

    fun allowedSites() {
        // TODO get allowed sites
        _viewState.value = ViewState(sitesAllowed = listOf("www.maps.google.com"))
    }

    fun permissionToggleSelected(
        isChecked: Boolean,
        textRes: Int
    ) {
        TODO("Not yet implemented")
    }

    fun allowedSiteSelected(domain: String) {
        TODO("Not yet implemented")
    }

    fun removeAllSitesSelected() {
        TODO("Not yet implemented")
    }

}
