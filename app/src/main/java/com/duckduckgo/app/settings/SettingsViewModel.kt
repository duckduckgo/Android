/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager,
    private val emailManager: EmailManager,
    private val fireAnimationLoader: FireAnimationLoader,
    private val pixel: Pixel
) : ViewModel() {

    data class ViewState(
        val loading: Boolean = true,
        val version: String = "",
        val lightThemeEnabled: Boolean = false,
        val autoCompleteSuggestionsEnabled: Boolean = true,
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
        val selectedFireAnimation: FireAnimation = FireAnimation.HeroFire,
        val automaticallyClearData: AutomaticallyClearData = AutomaticallyClearData(ClearWhatOption.CLEAR_NONE, ClearWhenOption.APP_EXIT_ONLY),
        val appIcon: AppIcon = AppIcon.DEFAULT,
        val globalPrivacyControlEnabled: Boolean = false,
        val emailSetting: EmailSetting = EmailSetting.EmailSettingOff
    )

    sealed class EmailSetting {
        object EmailSettingOff : EmailSetting()
        data class EmailSettingOn(val emailAddress: String) : EmailSetting()
    }

    data class AutomaticallyClearData(
        val clearWhatOption: ClearWhatOption,
        val clearWhenOption: ClearWhenOption,
        val clearWhenOptionEnabled: Boolean = true
    )

    sealed class Command {
        object LaunchFeedback : Command()
        object LaunchFireproofWebsites : Command()
        object LaunchLocation : Command()
        object LaunchWhitelist : Command()
        object LaunchAppIcon : Command()
        data class LaunchFireAnimationSettings(val animation: FireAnimation) : Command()
        object LaunchGlobalPrivacyControl : Command()
        object UpdateTheme : Command()
        object LaunchEmailDialog : Command()
        data class ShowClearWhatDialog(val option: ClearWhatOption) : Command()
        data class ShowClearWhenDialog(val option: ClearWhenOption) : Command()
    }

    private val viewState = MutableStateFlow(ViewState())

    private val command = MutableSharedFlow<Command>()

    init {
        pixel.fire(SETTINGS_OPENED)
    }

    fun start() {
        val defaultBrowserAlready = defaultWebBrowserCapability.isDefaultBrowser()
        val variant = variantManager.getVariant()
        val isLightTheme = settingsDataStore.theme == DuckDuckGoTheme.LIGHT
        val automaticallyClearWhat = settingsDataStore.automaticallyClearWhatOption
        val automaticallyClearWhen = settingsDataStore.automaticallyClearWhenOption
        val automaticallyClearWhenEnabled = isAutomaticallyClearingDataWhenSettingEnabled(automaticallyClearWhat)

        viewState.value = currentViewState().copy(
            loading = false,
            lightThemeEnabled = isLightTheme,
            autoCompleteSuggestionsEnabled = settingsDataStore.autoCompleteSuggestionsEnabled,
            isAppDefaultBrowser = defaultBrowserAlready,
            showDefaultBrowserSetting = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration(),
            version = obtainVersion(variant.key),
            automaticallyClearData = AutomaticallyClearData(automaticallyClearWhat, automaticallyClearWhen, automaticallyClearWhenEnabled),
            appIcon = settingsDataStore.appIcon,
            selectedFireAnimation = settingsDataStore.selectedFireAnimation,
            globalPrivacyControlEnabled = settingsDataStore.globalPrivacyControlEnabled,
            emailSetting = getEmailSetting()
        )
    }

    private fun getEmailSetting(): EmailSetting {
        val emailAddress = emailManager.getEmailAddress()

        return if (emailManager.isSignedIn()) {
            when (emailAddress) {
                null -> EmailSetting.EmailSettingOff
                else -> EmailSetting.EmailSettingOn(emailAddress)
            }
        } else {
            EmailSetting.EmailSettingOff
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command
    }

    fun onEmailSettingClicked() {
        if (getEmailSetting() is EmailSetting.EmailSettingOn) {
            viewModelScope.launch { command.emit(Command.LaunchEmailDialog) }
        }
    }

    fun userRequestedToSendFeedback() {
        viewModelScope.launch { command.emit(Command.LaunchFeedback) }
    }

    fun userRequestedToChangeIcon() {
        viewModelScope.launch { command.emit(Command.LaunchAppIcon) }
    }

    fun userRequestedToChangeFireAnimation() {
        viewModelScope.launch { command.emit(Command.LaunchFireAnimationSettings(viewState.value.selectedFireAnimation)) }
        pixel.fire(FIRE_ANIMATION_SETTINGS_OPENED)
    }

    fun onFireproofWebsitesClicked() {
        viewModelScope.launch { command.emit(Command.LaunchFireproofWebsites) }
    }

    fun onLocationClicked() {
        viewModelScope.launch { command.emit(Command.LaunchLocation) }
    }

    fun onAutomaticallyClearWhatClicked() {
        viewModelScope.launch { command.emit(Command.ShowClearWhatDialog(viewState.value.automaticallyClearData.clearWhatOption)) }
    }

    fun onAutomaticallyClearWhenClicked() {
        viewModelScope.launch { command.emit(Command.ShowClearWhenDialog(viewState.value.automaticallyClearData.clearWhenOption)) }
    }

    fun onGlobalPrivacyControlClicked() {
        viewModelScope.launch { command.emit(Command.LaunchGlobalPrivacyControl) }
    }

    fun onEmailLogout() {
        emailManager.signOut()
        viewState.value = currentViewState().copy(emailSetting = EmailSetting.EmailSettingOff)
    }

    fun onLightThemeToggled(enabled: Boolean) {
        Timber.i("User toggled light theme, is now enabled: $enabled")
        settingsDataStore.theme = if (enabled) DuckDuckGoTheme.LIGHT else DuckDuckGoTheme.DARK
        viewState.value = currentViewState().copy(lightThemeEnabled = enabled)
        viewModelScope.launch { command.emit(Command.UpdateTheme) }

        val pixelName = if (enabled) SETTINGS_THEME_TOGGLED_LIGHT else SETTINGS_THEME_TOGGLED_DARK
        pixel.fire(pixelName)
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
        viewState.value = currentViewState().copy(autoCompleteSuggestionsEnabled = enabled)
    }

    private fun obtainVersion(variantKey: String): String {
        val formattedVariantKey = if (variantKey.isBlank()) " " else " $variantKey "
        return "${BuildConfig.VERSION_NAME}$formattedVariantKey(${BuildConfig.VERSION_CODE})"
    }

    fun onAutomaticallyWhatOptionSelected(clearWhatNewSetting: ClearWhatOption) {
        if (settingsDataStore.isCurrentlySelected(clearWhatNewSetting)) {
            Timber.v("User selected same thing they already have set: $clearWhatNewSetting; no need to do anything else")
            return
        }

        pixel.fire(clearWhatNewSetting.pixelEvent())

        settingsDataStore.automaticallyClearWhatOption = clearWhatNewSetting

        viewState.value = currentViewState().copy(
            automaticallyClearData = AutomaticallyClearData(
                clearWhatOption = clearWhatNewSetting,
                clearWhenOption = settingsDataStore.automaticallyClearWhenOption,
                clearWhenOptionEnabled = isAutomaticallyClearingDataWhenSettingEnabled(clearWhatNewSetting)
            )
        )
    }

    private fun isAutomaticallyClearingDataWhenSettingEnabled(clearWhatOption: ClearWhatOption?): Boolean {
        return clearWhatOption != null && clearWhatOption != ClearWhatOption.CLEAR_NONE
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
        viewState.value = currentViewState().copy(
            automaticallyClearData = AutomaticallyClearData(
                settingsDataStore.automaticallyClearWhatOption,
                clearWhenNewSetting
            )
        )
    }

    fun onFireAnimationSelected(selectedFireAnimation: FireAnimation) {
        if (settingsDataStore.isCurrentlySelected(selectedFireAnimation)) {
            Timber.v("User selected same thing they already have set: $selectedFireAnimation; no need to do anything else")
            return
        }
        settingsDataStore.selectedFireAnimation = selectedFireAnimation
        fireAnimationLoader.preloadSelectedAnimation()
        viewState.value = currentViewState().copy(
            selectedFireAnimation = selectedFireAnimation
        )
        pixel.fire(FIRE_ANIMATION_NEW_SELECTED, mapOf(FIRE_ANIMATION to selectedFireAnimation.getPixelValue()))
    }

    fun onManageWhitelistSelected() {
        pixel.fire(SETTINGS_MANAGE_WHITELIST)
        viewModelScope.launch { command.emit(Command.LaunchWhitelist) }
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }

    private fun ClearWhatOption.pixelEvent(): PixelName {
        return when (this) {
            ClearWhatOption.CLEAR_NONE -> AUTOMATIC_CLEAR_DATA_WHAT_OPTION_NONE
            ClearWhatOption.CLEAR_TABS_ONLY -> AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS
            ClearWhatOption.CLEAR_TABS_AND_DATA -> AUTOMATIC_CLEAR_DATA_WHAT_OPTION_TABS_AND_DATA
        }
    }

    private fun ClearWhenOption.pixelEvent(): PixelName? {
        return when (this) {
            ClearWhenOption.APP_EXIT_ONLY -> AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY
            ClearWhenOption.APP_EXIT_OR_5_MINS -> AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_5_MINS
            ClearWhenOption.APP_EXIT_OR_15_MINS -> AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_15_MINS
            ClearWhenOption.APP_EXIT_OR_30_MINS -> AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_30_MINS
            ClearWhenOption.APP_EXIT_OR_60_MINS -> AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_60_MINS
            else -> null
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class SettingsViewModelFactory @Inject constructor(
    private val settingsDataStore: Provider<SettingsDataStore>,
    private val defaultWebBrowserCapability: Provider<DefaultBrowserDetector>,
    private val variantManager: Provider<VariantManager>,
    private val emailManager: Provider<EmailManager>,
    private val fireAnimationLoader: Provider<FireAnimationLoader>,
    private val pixel: Provider<Pixel>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsViewModel::class.java) -> (SettingsViewModel(settingsDataStore.get(), defaultWebBrowserCapability.get(), variantManager.get(), emailManager.get(), fireAnimationLoader.get(), pixel.get()) as T)
                else -> null
            }
        }
    }
}
