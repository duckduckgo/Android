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

package com.duckduckgo.app.permissionsandprivacy

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
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
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
class PermissionsAndPrivacyViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val pixel: Pixel,
    private val gpc: Gpc,
    private val featureToggle: FeatureToggle,
    private val autoconsent: Autoconsent,
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
        object LaunchGlobalPrivacyControl : Command()
        object LaunchAutoconsent : Command()
        object LaunchFireproofWebsites : Command()
        data class ShowClearWhatDialog(val option: ClearWhatOption) : Command()
        data class ShowClearWhenDialog(val option: ClearWhenOption) : Command()
        object LaunchWhitelist : Command()
        object LaunchLocation : Command()
        object LaunchNotificationsSettings : Command()
        data class LaunchAppLinkSettings(val appLinksSettingType: AppLinkSettingType) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())
    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    fun start(notificationsEnabled: Boolean = false) {
        val automaticallyClearWhat = settingsDataStore.automaticallyClearWhatOption
        val automaticallyClearWhen = settingsDataStore.automaticallyClearWhenOption
        val automaticallyClearWhenEnabled = isAutomaticallyClearingDataWhenSettingEnabled(automaticallyClearWhat)

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
                    automaticallyClearData = AutomaticallyClearData(automaticallyClearWhat, automaticallyClearWhen, automaticallyClearWhenEnabled),
                    globalPrivacyControlEnabled = gpc.isEnabled() && featureToggle.isFeatureEnabled(PrivacyFeatureName.GpcFeatureName.value),
                    appLinksSettingType = getAppLinksSettingsState(settingsDataStore.appLinksEnabled, settingsDataStore.showAppLinksPrompt),
                    autoconsentEnabled = autoconsent.isSettingEnabled(),
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

    fun onGlobalPrivacyControlClicked() {
        viewModelScope.launch { command.send(Command.LaunchGlobalPrivacyControl) }
    }

    fun onAutoconsentClicked() {
        viewModelScope.launch { command.send(Command.LaunchAutoconsent) }
    }

    fun onFireproofWebsitesClicked() {
        viewModelScope.launch { command.send(Command.LaunchFireproofWebsites) }
    }

    fun onAutomaticallyClearWhatClicked() {
        viewModelScope.launch { command.send(Command.ShowClearWhatDialog(viewState.value.automaticallyClearData.clearWhatOption)) }
    }

    fun onAutomaticallyClearWhenClicked() {
        viewModelScope.launch { command.send(Command.ShowClearWhenDialog(viewState.value.automaticallyClearData.clearWhenOption)) }
    }

    fun onManageWhitelistSelected() {
        viewModelScope.launch { command.send(Command.LaunchWhitelist) }
        pixel.fire(AppPixelName.SETTINGS_MANAGE_WHITELIST)
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
        viewModelScope.launch { viewState.emit(currentViewState().copy(autoCompleteSuggestionsEnabled = enabled)) }
    }

    fun onSitePermissionsClicked() {
        viewModelScope.launch { command.send(Command.LaunchLocation) }
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
        Timber.i("User changed app links setting, is now: ${appLinkSettingType.name}")

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

    fun onAutomaticallyWhatOptionSelected(clearWhatNewSetting: ClearWhatOption) {
        if (settingsDataStore.isCurrentlySelected(clearWhatNewSetting)) {
            Timber.v("User selected same thing they already have set: $clearWhatNewSetting; no need to do anything else")
            return
        }

        pixel.fire(clearWhatNewSetting.pixelEvent())

        settingsDataStore.automaticallyClearWhatOption = clearWhatNewSetting

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    automaticallyClearData = AutomaticallyClearData(
                        clearWhatOption = clearWhatNewSetting,
                        clearWhenOption = settingsDataStore.automaticallyClearWhenOption,
                        clearWhenOptionEnabled = isAutomaticallyClearingDataWhenSettingEnabled(clearWhatNewSetting),
                    ),
                ),
            )
        }
    }

    fun onAutomaticallyWhenOptionSelected(clearWhenNewSetting: ClearWhenOption) {
        if (settingsDataStore.isCurrentlySelected(clearWhenNewSetting)) {
            Timber.v("User selected same thing they already have set: $clearWhenNewSetting; no need to do anything else")
            return
        }

        clearWhenNewSetting.pixelEvent()?.let {
            pixel.fire(it)
        }

        settingsDataStore.automaticallyClearWhenOption = clearWhenNewSetting
        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    automaticallyClearData = AutomaticallyClearData(
                        settingsDataStore.automaticallyClearWhatOption,
                        clearWhenNewSetting,
                    ),
                ),
            )
        }
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

    private fun isAutomaticallyClearingDataWhenSettingEnabled(clearWhatOption: ClearWhatOption?): Boolean {
        return clearWhatOption != null && clearWhatOption != ClearWhatOption.CLEAR_NONE
    }

    private fun ClearWhatOption.pixelEvent(): Pixel.PixelName {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE
            ClearWhatOption.CLEAR_TABS_ONLY -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS
            ClearWhatOption.CLEAR_TABS_AND_DATA -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA
        }
    }

    private fun ClearWhenOption.pixelEvent(): Pixel.PixelName? {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY
            ClearWhenOption.APP_EXIT_OR_5_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_5_MINS
            ClearWhenOption.APP_EXIT_OR_15_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_15_MINS
            ClearWhenOption.APP_EXIT_OR_30_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_30_MINS
            ClearWhenOption.APP_EXIT_OR_60_MINS -> AppPixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_60_MINS
            else -> null
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }
}
