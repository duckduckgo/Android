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

package com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.GoBackToSitePermissions
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK_DISABLED
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class PermissionsPerWebsiteViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    data class ViewState(
        val websitePermissions: List<WebsitePermissionSetting> = listOf(),
    )

    sealed class Command {
        class ShowPermissionSettingSelectionDialog(val setting: WebsitePermissionSetting) : Command()
        data object GoBackToSitePermissions : Command()
    }

    fun websitePermissionSettings(url: String) {
        viewModelScope.launch {
            val websitePermissionsSettings = sitePermissionsRepository.getSitePermissionsForWebsite(url)
            val websitePermissions = convertToWebsitePermissionSettings(websitePermissionsSettings)
            logcat { "Permissions: websitePermissionsSettings for $url $websitePermissionsSettings" }
            logcat { "Permissions: websitePermissions for $url $websitePermissions" }

            _viewState.value = _viewState.value.copy(websitePermissions = websitePermissions)
        }
    }

    private fun convertToWebsitePermissionSettings(
        sitePermissionsEntity: SitePermissionsEntity?,
    ): List<WebsitePermissionSetting> {
        var locationSetting = WebsitePermissionSettingOption.mapToWebsitePermissionSetting(sitePermissionsEntity?.askLocationSetting)
        if (locationSetting == ASK && !sitePermissionsRepository.askLocationEnabled) {
            locationSetting = ASK_DISABLED
        }

        var cameraSetting = WebsitePermissionSettingOption.mapToWebsitePermissionSetting(sitePermissionsEntity?.askCameraSetting)
        if (cameraSetting == ASK && !sitePermissionsRepository.askCameraEnabled) {
            cameraSetting = ASK_DISABLED
        }

        var micSetting = WebsitePermissionSettingOption.mapToWebsitePermissionSetting(sitePermissionsEntity?.askMicSetting)
        if (micSetting == ASK && !sitePermissionsRepository.askMicEnabled) {
            micSetting = ASK_DISABLED
        }

        var drmSetting = WebsitePermissionSettingOption.mapToWebsitePermissionSetting(sitePermissionsEntity?.askDrmSetting)
        if (drmSetting == ASK && !sitePermissionsRepository.askDrmEnabled) {
            drmSetting = ASK_DISABLED
        }

        return getSettingsList(locationSetting, cameraSetting, micSetting, drmSetting)
    }

    private fun getSettingsList(
        locationSetting: WebsitePermissionSettingOption,
        cameraSetting: WebsitePermissionSettingOption,
        micSetting: WebsitePermissionSettingOption,
        drmSetting: WebsitePermissionSettingOption,
    ): List<WebsitePermissionSetting> {
        return listOf(
            WebsitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_location_24,
                R.string.sitePermissionsSettingsLocation,
                locationSetting,
            ),
            WebsitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_video_24,
                R.string.sitePermissionsSettingsCamera,
                cameraSetting,
            ),
            WebsitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_microphone_24,
                R.string.sitePermissionsSettingsMicrophone,
                micSetting,
            ),
            WebsitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_video_player_24,
                R.string.sitePermissionsSettingsDRM,
                drmSetting,
            ),
        )
    }

    fun permissionSettingSelected(setting: WebsitePermissionSetting) {
        viewModelScope.launch {
            _commands.send(ShowPermissionSettingSelectionDialog(setting))
        }
    }

    fun removeWebsitePermissionsSettings(url: String) {
        viewModelScope.launch {
            sitePermissionsRepository.deletePermissionsForSite(url)
            _commands.send(GoBackToSitePermissions)
        }
    }

    fun onPermissionSettingSelected(
        editedPermissionSetting: WebsitePermissionSetting,
        url: String,
    ) {
        var askLocationSetting = viewState.value.websitePermissions[0].setting
        var askCameraSetting = viewState.value.websitePermissions[1].setting
        var askMicSetting = viewState.value.websitePermissions[2].setting
        var askDrmSetting = viewState.value.websitePermissions[3].setting

        when (editedPermissionSetting.title) {
            R.string.sitePermissionsSettingsLocation -> {
                askLocationSetting = when (editedPermissionSetting.setting == ASK && !sitePermissionsRepository.askLocationEnabled) {
                    true -> ASK_DISABLED
                    false -> editedPermissionSetting.setting
                }
            }

            R.string.sitePermissionsSettingsCamera -> {
                askCameraSetting = when (editedPermissionSetting.setting == ASK && !sitePermissionsRepository.askCameraEnabled) {
                    true -> ASK_DISABLED
                    false -> editedPermissionSetting.setting
                }
            }

            R.string.sitePermissionsSettingsMicrophone -> {
                askMicSetting = when (editedPermissionSetting.setting == ASK && !sitePermissionsRepository.askMicEnabled) {
                    true -> ASK_DISABLED
                    false -> editedPermissionSetting.setting
                }
            }

            R.string.sitePermissionsSettingsDRM -> {
                askDrmSetting = when (editedPermissionSetting.setting == ASK && !sitePermissionsRepository.askDrmEnabled) {
                    true -> ASK_DISABLED
                    false -> editedPermissionSetting.setting
                }
            }
        }

        updateSitePermissionsSetting(askCameraSetting, askMicSetting, askDrmSetting, askLocationSetting, url)

        _viewState.value = _viewState.value.copy(
            websitePermissions = getSettingsList(askLocationSetting, askCameraSetting, askMicSetting, askDrmSetting),
        )
    }

    private fun updateSitePermissionsSetting(
        askCameraSetting: WebsitePermissionSettingOption,
        askMicSetting: WebsitePermissionSettingOption,
        askDrmSetting: WebsitePermissionSettingOption,
        askLocationSetting: WebsitePermissionSettingOption,
        url: String,
    ) {
        val sitePermissionsEntity = SitePermissionsEntity(
            domain = url,
            askCameraSetting = askCameraSetting.toSitePermissionSettingEntityType().name,
            askMicSetting = askMicSetting.toSitePermissionSettingEntityType().name,
            askDrmSetting = askDrmSetting.toSitePermissionSettingEntityType().name,
            askLocationSetting = askLocationSetting.toSitePermissionSettingEntityType().name,
        )
        viewModelScope.launch {
            sitePermissionsRepository.savePermission(sitePermissionsEntity)
        }
    }
}
