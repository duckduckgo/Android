/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.permissions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesViewModel(ActivityScope::class)
class PermissionsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
) : ViewModel() {

    data class ViewState(
        val autoCompleteSuggestionsEnabled: Boolean = true,
        val automaticallyClearData: AutomaticallyClearData = AutomaticallyClearData(
            ClearWhatOption.CLEAR_NONE,
            ClearWhenOption.APP_EXIT_ONLY,
        ),
        val globalPrivacyControlEnabled: Boolean = false,
        val appLinksSettingType: AppLinkSettingType = AppLinkSettingType.ASK_EVERYTIME,
        val appTrackingProtectionOnboardingShown: Boolean = false,
        val autoconsentEnabled: Boolean = false,
        @StringRes val notificationsSettingSubtitleId: Int = R.string.settingsSubtitleNotificationsDisabled,
    )

    data class AutomaticallyClearData(
        val clearWhatOption: ClearWhatOption,
        val clearWhenOption: ClearWhenOption,
        val clearWhenOptionEnabled: Boolean = true,
    )

    sealed class Command {
        object LaunchLocation : Command()
        object LaunchNotificationsSettings : Command()
        data class LaunchAppLinkSettings(val appLinksSettingType: AppLinkSettingType) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun start(notificationsEnabled: Boolean = false) {
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    appLinksSettingType = getAppLinksSettingsState(settingsDataStore.appLinksEnabled, settingsDataStore.showAppLinksPrompt),
                    notificationsSettingSubtitleId = getNotificationsSettingSubtitleId(notificationsEnabled),
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

    fun onSitePermissionsClicked() {
        viewModelScope.launch { command.send(Command.LaunchLocation) }
        pixel.fire(AppPixelName.SETTINGS_SITE_PERMISSIONS_PRESSED)
    }

    fun userRequestedToChangeNotificationsSetting() {
        viewModelScope.launch { command.send(Command.LaunchNotificationsSettings) }
        pixel.fire(AppPixelName.SETTINGS_NOTIFICATIONS_PRESSED)
    }

    fun userRequestedToChangeAppLinkSetting() {
        viewModelScope.launch { command.send(Command.LaunchAppLinkSettings(viewState.value.appLinksSettingType)) }
        pixel.fire(AppPixelName.SETTINGS_APP_LINKS_PRESSED)
    }

    fun onAppLinksSettingChanged(appLinkSettingType: AppLinkSettingType) {
        logcat(INFO) { "User changed app links setting, is now: ${appLinkSettingType.name}" }

        val pixelName =
            when (appLinkSettingType) {
                AppLinkSettingType.ASK_EVERYTIME -> {
                    settingsDataStore.appLinksEnabled = true
                    settingsDataStore.showAppLinksPrompt = true
                    AppPixelName.SETTINGS_APP_LINKS_ASK_EVERY_TIME_SELECTED
                }
                AppLinkSettingType.ALWAYS -> {
                    settingsDataStore.appLinksEnabled = true
                    settingsDataStore.showAppLinksPrompt = false
                    AppPixelName.SETTINGS_APP_LINKS_ALWAYS_SELECTED
                }
                AppLinkSettingType.NEVER -> {
                    settingsDataStore.appLinksEnabled = false
                    settingsDataStore.showAppLinksPrompt = false
                    AppPixelName.SETTINGS_APP_LINKS_NEVER_SELECTED
                }
            }
        viewModelScope.launch { viewState.emit(currentViewState().copy(appLinksSettingType = appLinkSettingType)) }

        pixel.fire(pixelName)
    }

    private fun getAppLinksSettingsState(
        appLinksEnabled: Boolean,
        showAppLinksPrompt: Boolean,
    ): AppLinkSettingType {
        return if (appLinksEnabled) {
            if (showAppLinksPrompt) {
                AppLinkSettingType.ASK_EVERYTIME
            } else {
                AppLinkSettingType.ALWAYS
            }
        } else {
            AppLinkSettingType.NEVER
        }
    }

    private fun getNotificationsSettingSubtitleId(notificationsEnabled: Boolean): Int {
        return if (notificationsEnabled) {
            R.string.settingsSubtitleNotificationsEnabled
        } else {
            R.string.settingsSubtitleNotificationsDisabled
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
