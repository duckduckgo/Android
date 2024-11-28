/*
 * Copyright (c) 2024 DuckDuckGo
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

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAboutScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAccessibilitySettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAddHomeScreenWidget
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPOnboarding
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppTPTrackersScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAppearanceScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchAutofillSettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchCookiePopupProtectionScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchEmailProtectionNotSupported
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchFireButtonScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchGeneralSettingsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchMacOs
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPermissionsScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchPrivateSearchWebPage
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchSyncSettings
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchWebTrackingProtectionScreen
import com.duckduckgo.app.settings.NewSettingsViewModel.Command.LaunchWindows
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.sync.api.DeviceSyncState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ActivityScope::class)
class NewSettingsViewModel @Inject constructor(
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val appTrackingProtection: AppTrackingProtection,
    private val pixel: Pixel,
    private val emailManager: EmailManager,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val deviceSyncState: DeviceSyncState,
    private val dispatcherProvider: DispatcherProvider,
    private val autoconsent: Autoconsent,
    private val subscriptions: Subscriptions,
    private val duckPlayer: DuckPlayer,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
        val appTrackingProtectionOnboardingShown: Boolean = false,
        val appTrackingProtectionEnabled: Boolean = false,
        val emailAddress: String? = null,
        val showAutofill: Boolean = false,
        val showSyncSetting: Boolean = false,
        val isAutoconsentEnabled: Boolean = false,
        val isPrivacyProEnabled: Boolean = false,
        val isDuckPlayerEnabled: Boolean = false,
    )

    sealed class Command {
        data object LaunchDefaultBrowser : Command()
        data class LaunchEmailProtection(val url: String) : Command()
        data object LaunchEmailProtectionNotSupported : Command()
        data object LaunchAutofillSettings : Command()
        data object LaunchAccessibilitySettings : Command()
        data object LaunchAddHomeScreenWidget : Command()
        data object LaunchAppTPTrackersScreen : Command()
        data object LaunchAppTPOnboarding : Command()
        data object LaunchMacOs : Command()
        data object LaunchWindows : Command()
        data object LaunchSyncSettings : Command()
        data object LaunchPrivateSearchWebPage : Command()
        data object LaunchWebTrackingProtectionScreen : Command()
        data object LaunchCookiePopupProtectionScreen : Command()
        data object LaunchFireButtonScreen : Command()
        data object LaunchPermissionsScreen : Command()
        data object LaunchAppearanceScreen : Command()
        data object LaunchAboutScreen : Command()
        data object LaunchGeneralSettingsScreen : Command()
    }

    private val viewState = MutableStateFlow(ViewState())

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val appTPPollJob = ConflatedJob()

    init {
        pixel.fire(SETTINGS_OPENED)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        start()
        startPollingAppTPState()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        appTPPollJob.cancel()
    }

    @VisibleForTesting
    internal fun start() {
        val defaultBrowserAlready = defaultWebBrowserCapability.isDefaultBrowser()

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    isAppDefaultBrowser = defaultBrowserAlready,
                    showDefaultBrowserSetting = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration(),
                    appTrackingProtectionOnboardingShown = appTrackingProtection.isOnboarded(),
                    appTrackingProtectionEnabled = appTrackingProtection.isRunning(),
                    emailAddress = emailManager.getEmailAddress(),
                    showAutofill = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                    showSyncSetting = deviceSyncState.isFeatureEnabled(),
                    isAutoconsentEnabled = autoconsent.isSettingEnabled(),
                    isPrivacyProEnabled = subscriptions.isEligible(),
                    isDuckPlayerEnabled = duckPlayer.getDuckPlayerState().let { it == ENABLED || it == DISABLED_WIH_HELP_LINK },
                ),
            )
        }
    }

    // FIXME
    // We need to fix this. This logic as inside the start method but it messes with the unit tests
    // because when doing runningBlockingTest {} there is no delay and the tests crashes because this
    // becomes a while(true) without any delay
    private fun startPollingAppTPState() {
        appTPPollJob += viewModelScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                val isDeviceShieldEnabled = appTrackingProtection.isRunning()
                val currentState = currentViewState()
                viewState.value = currentState.copy(
                    appTrackingProtectionOnboardingShown = appTrackingProtection.isOnboarded(),
                    appTrackingProtectionEnabled = isDeviceShieldEnabled,
                    isPrivacyProEnabled = subscriptions.isEligible(),
                )
                delay(1_000)
            }
        }
    }

    fun viewState(): StateFlow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun userRequestedToAddHomeScreenWidget() {
        viewModelScope.launch { command.send(LaunchAddHomeScreenWidget) }
    }

    fun onDefaultBrowserSettingClicked() {
        val defaultBrowserSelected = defaultWebBrowserCapability.isDefaultBrowser()
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(isAppDefaultBrowser = defaultBrowserSelected))
            command.send(LaunchDefaultBrowser)
        }
        pixel.fire(SETTINGS_DEFAULT_BROWSER_PRESSED)
    }

    fun onPrivateSearchSettingClicked() {
        viewModelScope.launch { command.send(LaunchPrivateSearchWebPage) }
        pixel.fire(SETTINGS_PRIVATE_SEARCH_PRESSED)
    }

    fun onWebTrackingProtectionSettingClicked() {
        viewModelScope.launch { command.send(LaunchWebTrackingProtectionScreen) }
        pixel.fire(SETTINGS_WEB_TRACKING_PROTECTION_PRESSED)
    }

    fun onCookiePopupProtectionSettingClicked() {
        viewModelScope.launch { command.send(LaunchCookiePopupProtectionScreen) }
        pixel.fire(SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED)
    }

    fun onAutofillSettingsClick() {
        viewModelScope.launch { command.send(LaunchAutofillSettings) }
    }

    fun onAccessibilitySettingClicked() {
        viewModelScope.launch { command.send(LaunchAccessibilitySettings) }
        pixel.fire(SETTINGS_ACCESSIBILITY_PRESSED)
    }

    fun onAboutSettingClicked() {
        viewModelScope.launch { command.send(LaunchAboutScreen) }
        pixel.fire(SETTINGS_ABOUT_PRESSED)
    }

    fun onGeneralSettingClicked() {
        viewModelScope.launch { command.send(LaunchGeneralSettingsScreen) }
        pixel.fire(SETTINGS_GENERAL_PRESSED)
    }

    fun onEmailProtectionSettingClicked() {
        viewModelScope.launch {
            val command = if (emailManager.isEmailFeatureSupported()) {
                LaunchEmailProtection(EMAIL_PROTECTION_URL)
            } else {
                LaunchEmailProtectionNotSupported
            }
            this@NewSettingsViewModel.command.send(command)
        }
        pixel.fire(SETTINGS_EMAIL_PROTECTION_PRESSED)
    }

    fun onMacOsSettingClicked() {
        viewModelScope.launch { command.send(LaunchMacOs) }
        pixel.fire(SETTINGS_MAC_APP_PRESSED)
    }

    fun windowsSettingClicked() {
        viewModelScope.launch {
            command.send(LaunchWindows)
        }
        pixel.fire(SETTINGS_WINDOWS_APP_PRESSED)
    }

    fun onAppTPSettingClicked() {
        viewModelScope.launch {
            if (appTrackingProtection.isOnboarded()) {
                command.send(LaunchAppTPTrackersScreen)
            } else {
                command.send(LaunchAppTPOnboarding)
            }
            pixel.fire(SETTINGS_APPTP_PRESSED)
        }
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onSyncSettingClicked() {
        viewModelScope.launch { command.send(LaunchSyncSettings) }
        pixel.fire(SETTINGS_SYNC_PRESSED)
    }

    fun onFireButtonSettingClicked() {
        viewModelScope.launch { command.send(LaunchFireButtonScreen) }
        pixel.fire(SETTINGS_FIRE_BUTTON_PRESSED)
    }

    fun onPermissionsSettingClicked() {
        viewModelScope.launch { command.send(LaunchPermissionsScreen) }
        pixel.fire(SETTINGS_PERMISSIONS_PRESSED)
    }

    fun onAppearanceSettingClicked() {
        viewModelScope.launch { command.send(LaunchAppearanceScreen) }
        pixel.fire(SETTINGS_APPEARANCE_PRESSED)
    }

    fun onLaunchedFromNotification(pixelName: String) {
        pixel.fire(pixelName)
    }

    companion object {
        const val EMAIL_PROTECTION_URL = "https://duckduckgo.com/email"
    }
}
