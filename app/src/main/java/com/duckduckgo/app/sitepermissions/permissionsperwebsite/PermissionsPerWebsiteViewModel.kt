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

package com.duckduckgo.app.sitepermissions.permissionsperwebsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.sitepermissions.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
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
class PermissionsPerWebsiteViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    data class ViewState(
        val websitePermissions: List<WebsitePermissionSetting> = listOf()
    )

    sealed class Command {
        class ShowPermissionSettingSelectionDialog(val setting: WebsitePermissionSetting) : Command()
    }

    fun websitePermissionSettings(url: String) {
        viewModelScope.launch {
            val websitePermissionsSettings = sitePermissionsRepository.getSitePermissionsForWebsite(url)
            val locationSetting = locationPermissionsRepository.getDomainPermission(url)
            val websitePermissions = convertToWebsitePermissionSettings(websitePermissionsSettings, locationSetting)

            _viewState.value = _viewState.value.copy(websitePermissions = websitePermissions)
        }
    }

    private fun convertToWebsitePermissionSettings(
        sitePermissionsEntity: SitePermissionsEntity?,
        locationPermissionEntity: LocationPermissionEntity?
    ): List<WebsitePermissionSetting> {
        val micSetting = WebsitePermissionSettingType.mapToWebsitePermissionSetting(sitePermissionsEntity?.askMicSetting)
        val cameraSetting = WebsitePermissionSettingType.mapToWebsitePermissionSetting(sitePermissionsEntity?.askCameraSetting)
        val locationSetting = WebsitePermissionSettingType.mapToWebsitePermissionSetting(locationPermissionEntity?.permission?.name)
        return listOf(
            WebsitePermissionSetting(
                R.drawable.ic_location,
                R.string.sitePermissionsSettingsLocation,
                locationSetting
            ),
            WebsitePermissionSetting(
                R.drawable.ic_camera,
                R.string.sitePermissionsSettingsCamera,
                cameraSetting
            ),
            WebsitePermissionSetting(
                R.drawable.ic_microphone,
                R.string.sitePermissionsSettingsMicrophone,
                micSetting
            )
        )
    }

    fun permissionSettingSelected(setting: WebsitePermissionSetting) {
        viewModelScope.launch {
            _commands.send(ShowPermissionSettingSelectionDialog(setting))
        }
    }
}
