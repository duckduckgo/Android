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

import android.content.Context
import androidx.lifecycle.*
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.fire.FireAnimationLoader
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.settings.clear.ClearWhatOption
import com.duckduckgo.app.settings.clear.ClearWhenOption
import com.duckduckgo.app.settings.clear.FireAnimation
import com.duckduckgo.app.settings.clear.getPixelValue
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_ANIMATION
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.analytics.DeviceShieldAnalytics
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import com.duckduckgo.mobile.android.vpn.exclusions.DeviceShieldExcludedApps
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboarding
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Provider
import javax.inject.Singleton

class SettingsViewModel(
    private val deviceShieldAnalytics: DeviceShieldAnalytics,
    private val appContext: Context,
    private val settingsDataStore: SettingsDataStore,
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val variantManager: VariantManager,
    private val fireAnimationLoader: FireAnimationLoader,
    private val pixel: Pixel,
    private val deviceShieldExcludedApps: DeviceShieldExcludedApps,
    private val deviceShieldOnboarding: DeviceShieldOnboarding
) : ViewModel(), LifecycleObserver {

    private var deviceShieldStatePollingJob: Job? = null

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
        val deviceShieldEnabled: Boolean = false,
        val excludedAppsInfo: String = ""
    )

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
        object LaunchFireAnimationSettings : Command()
        object LaunchGlobalPrivacyControl : Command()
        object LaunchExcludedAppList : Command()
        object LaunchDeviceShieldPrivacyReport : Command()
        object UpdateTheme : Command()
        object LaunchDeviceShieldOnboarding : Command()
        object StartDeviceShield : Command()
        object StopDeviceShield : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        value = ViewState()
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

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
            deviceShieldEnabled = TrackerBlockingVpnService.isServiceRunning(appContext),
            excludedAppsInfo = getExcludedAppsInfo()
        )
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun pollDeviceShieldState() {
        deviceShieldStatePollingJob = viewModelScope.launch {
            while (isActive) {
                val isDeviceShieldEnabled = TrackerBlockingVpnService.isServiceRunning(appContext)
                if (currentViewState().deviceShieldEnabled != isDeviceShieldEnabled) {
                    viewState.value = currentViewState().copy(
                        deviceShieldEnabled = isDeviceShieldEnabled
                    )
                }
                delay(1_000)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stopPollingDeviceShieldState() {
        deviceShieldStatePollingJob?.cancel()
    }

    private fun getExcludedAppsInfo(): String {
        val apps = deviceShieldExcludedApps.getExcludedApps()
        return when (apps.size) {
            0 -> "None"
            1 -> apps.first().name
            2 -> "${apps.first().name} and ${apps.take(2)[1].name}"
            else -> "${apps.first().name}, ${apps.take(2)[1].name} and more"
        }
    }

    fun userRequestedToSendFeedback() {
        command.value = Command.LaunchFeedback
    }

    fun userRequestedToChangeIcon() {
        command.value = Command.LaunchAppIcon
    }

    fun userRequestedToChangeFireAnimation() {
        command.value = Command.LaunchFireAnimationSettings
        pixel.fire(FIRE_ANIMATION_SETTINGS_OPENED)
    }

    fun onFireproofWebsitesClicked() {
        command.value = Command.LaunchFireproofWebsites
    }

    fun onExcludedAppsClicked() {
        command.value = Command.LaunchExcludedAppList
    }

    fun onDeviceShieldPrivacyReportClicked() {
        command.value = Command.LaunchDeviceShieldPrivacyReport
    }

    fun onLocationClicked() {
        command.value = Command.LaunchLocation
    }

    fun onGlobalPrivacyControlClicked() {
        command.value = Command.LaunchGlobalPrivacyControl
    }

    fun onLightThemeToggled(enabled: Boolean) {
        Timber.i("User toggled light theme, is now enabled: $enabled")
        settingsDataStore.theme = if (enabled) DuckDuckGoTheme.LIGHT else DuckDuckGoTheme.DARK
        viewState.value = currentViewState().copy(lightThemeEnabled = enabled)
        command.value = Command.UpdateTheme

        val pixelName = if (enabled) SETTINGS_THEME_TOGGLED_LIGHT else SETTINGS_THEME_TOGGLED_DARK
        pixel.fire(pixelName)
    }

    fun onAutocompleteSettingChanged(enabled: Boolean) {
        Timber.i("User changed autocomplete setting, is now enabled: $enabled")
        settingsDataStore.autoCompleteSuggestionsEnabled = enabled
        viewState.value = currentViewState().copy(autoCompleteSuggestionsEnabled = enabled)
    }

    fun onDeviceShieldSettingChanged(enabled: Boolean) {
        Timber.i("Device Shield, is now enabled: $enabled")

        if (enabled) {
            deviceShieldAnalytics.enableFromSettings()
        } else {
            deviceShieldAnalytics.disableFromSettings()
        }

        val deviceShieldOnboardingIntent = deviceShieldOnboarding.prepare(appContext)
        command.value = when {
            enabled && deviceShieldOnboardingIntent != null -> Command.LaunchDeviceShieldOnboarding
            enabled -> Command.StartDeviceShield
            else -> Command.StopDeviceShield
        }
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
        command.value = Command.LaunchWhitelist
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

@Module
@ContributesTo(AppObjectGraph::class)
class SettingsViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideSettingsViewModelFactory(
        deviceShieldAnalytics: Provider<DeviceShieldAnalytics>,
        appContext: Provider<Context>,
        settingsDataStore: Provider<SettingsDataStore>,
        defaultWebBrowserCapability: Provider<DefaultBrowserDetector>,
        variantManager: Provider<VariantManager>,
        fireAnimationLoader: Provider<FireAnimationLoader>,
        pixel: Provider<Pixel>,
        deviceShieldExcludedApps: Provider<DeviceShieldExcludedApps>,
        deviceShieldOnboarding: Provider<DeviceShieldOnboarding>
    ): ViewModelFactoryPlugin {
        return SettingsViewModelFactory(deviceShieldAnalytics, appContext, settingsDataStore, defaultWebBrowserCapability, variantManager, fireAnimationLoader, pixel, deviceShieldExcludedApps, deviceShieldOnboarding)
    }
}

private class SettingsViewModelFactory(
    private val deviceShieldAnalytics: Provider<DeviceShieldAnalytics>,
    private val appContext: Provider<Context>,
    private val settingsDataStore: Provider<SettingsDataStore>,
    private val defaultWebBrowserCapability: Provider<DefaultBrowserDetector>,
    private val variantManager: Provider<VariantManager>,
    private val fireAnimationLoader: Provider<FireAnimationLoader>,
    private val pixel: Provider<Pixel>,
    private val deviceShieldExcludedApps: Provider<DeviceShieldExcludedApps>,
    private val deviceShieldOnboarding: Provider<DeviceShieldOnboarding>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(SettingsViewModel::class.java) -> (SettingsViewModel(deviceShieldAnalytics.get(), appContext.get(), settingsDataStore.get(), defaultWebBrowserCapability.get(), variantManager.get(), fireAnimationLoader.get(), pixel.get(), deviceShieldExcludedApps.get(), deviceShieldOnboarding.get()) as T)
                else -> null
            }
        }
    }
}
