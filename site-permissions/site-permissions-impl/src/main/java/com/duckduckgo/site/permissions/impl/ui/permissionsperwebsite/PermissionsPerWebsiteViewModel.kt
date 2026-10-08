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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.site.permissions.impl.R
import com.duckduckgo.site.permissions.impl.SitePermissionsRepository
import com.duckduckgo.site.permissions.impl.feature.SitePermissionsDialogRedesignFeature
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.GoBackToSitePermissions
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.PermissionsPerWebsiteViewModel.Command.ShowPermissionSettingSelectionDialog
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK
import com.duckduckgo.site.permissions.impl.ui.permissionsperwebsite.WebsitePermissionSettingOption.ASK_DISABLED
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class PermissionsPerWebsiteViewModel @Inject constructor(
    private val sitePermissionsRepository: SitePermissionsRepository,
    private val sitePermissionsDialogRedesignFeature: SitePermissionsDialogRedesignFeature,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    private var sitePermissions: SitePermissionsEntity? = null
    private var hideUnsetPermissions = false

    data class ViewState(
        val websitePermissions: List<WebsitePermissionSetting> = listOf(),
    )

    sealed class Command {
        class ShowPermissionSettingSelectionDialog(val setting: WebsitePermissionSetting) : Command()
        data object GoBackToSitePermissions : Command()
    }

    fun websitePermissionSettings(url: String) {
        viewModelScope.launch {
            hideUnsetPermissions = withContext(dispatcherProvider.io()) {
                sitePermissionsDialogRedesignFeature.explicitPermissionsOnly().isEnabled()
            }
            val websitePermissionsSettings = sitePermissionsRepository.getSitePermissionsForWebsite(url)
            sitePermissions = websitePermissionsSettings
            val websitePermissions = convertToWebsitePermissionSettings(websitePermissionsSettings)
            logcat { "Permissions: websitePermissionsSettings for $url $websitePermissionsSettings" }
            logcat { "Permissions: websitePermissions for $url $websitePermissions" }

            _viewState.value = _viewState.value.copy(websitePermissions = websitePermissions)
        }
    }

    private fun convertToWebsitePermissionSettings(
        sitePermissionsEntity: SitePermissionsEntity?,
    ): List<WebsitePermissionSetting> {
        return listOfNotNull(
            websitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_location_24,
                R.string.sitePermissionsSettingsLocation,
                sitePermissionsEntity?.askLocationSetting,
                sitePermissionsRepository.askLocationEnabled,
            ),
            websitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_video_24,
                R.string.sitePermissionsSettingsCamera,
                sitePermissionsEntity?.askCameraSetting,
                sitePermissionsRepository.askCameraEnabled,
            ),
            websitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_microphone_24,
                R.string.sitePermissionsSettingsMicrophone,
                sitePermissionsEntity?.askMicSetting,
                sitePermissionsRepository.askMicEnabled,
            ),
            websitePermissionSetting(
                com.duckduckgo.mobile.android.R.drawable.ic_video_player_24,
                R.string.sitePermissionsSettingsDRM,
                sitePermissionsEntity?.askDrmSetting,
                sitePermissionsRepository.askDrmEnabled,
            ),
        )
    }

    private fun websitePermissionSetting(
        icon: Int,
        title: Int,
        storedSetting: String?,
        askEnabled: Boolean,
    ): WebsitePermissionSetting? {
        if (storedSetting == null && hideUnsetPermissions) return null
        var setting = WebsitePermissionSettingOption.mapToWebsitePermissionSetting(storedSetting)
        if (setting == ASK && !askEnabled) {
            setting = ASK_DISABLED
        }
        return WebsitePermissionSetting(icon, title, setting)
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
        val current = sitePermissions ?: SitePermissionsEntity(domain = url)
        val newSetting = editedPermissionSetting.setting.toSitePermissionSettingEntityType().name
        val updated = when (editedPermissionSetting.title) {
            R.string.sitePermissionsSettingsLocation -> current.copy(askLocationSetting = newSetting)
            R.string.sitePermissionsSettingsCamera -> current.copy(askCameraSetting = newSetting)
            R.string.sitePermissionsSettingsMicrophone -> current.copy(askMicSetting = newSetting)
            R.string.sitePermissionsSettingsDRM -> current.copy(askDrmSetting = newSetting)
            else -> return
        }
        sitePermissions = updated

        viewModelScope.launch {
            sitePermissionsRepository.savePermission(updated)
        }

        _viewState.value = _viewState.value.copy(websitePermissions = convertToWebsitePermissionSettings(updated))
    }
}
