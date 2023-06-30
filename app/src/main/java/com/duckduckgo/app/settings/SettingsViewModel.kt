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
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.SettingsViewModel.NetPState.CONNECTED
import com.duckduckgo.app.settings.SettingsViewModel.NetPState.DISCONNECTED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.windows.api.WindowsDownloadLinkFeature
import com.duckduckgo.windows.api.WindowsWaitlist
import com.duckduckgo.windows.api.WindowsWaitlistFeature
import com.duckduckgo.windows.api.WindowsWaitlistState
import com.duckduckgo.windows.api.WindowsWaitlistState.FeatureEnabled
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

@ContributesViewModel(ActivityScope::class)
class SettingsViewModel @Inject constructor(
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val appTrackingProtection: AppTrackingProtection,
    private val pixel: Pixel,
    private val appBuildConfig: AppBuildConfig,
    private val emailManager: EmailManager,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    private val windowsWaitlist: WindowsWaitlist,
    private val windowsFeature: WindowsWaitlistFeature,
    private val deviceSyncState: DeviceSyncState,
    private val netpWaitlistRepository: NetPWaitlistRepository,
    private val windowsDownloadLinkFeature: WindowsDownloadLinkFeature,
    private val dispatcherProvider: DispatcherProvider,
    private val autoconsent: Autoconsent,
) : ViewModel() {

    data class ViewState(
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
        val appTrackingProtectionOnboardingShown: Boolean = false,
        val appTrackingProtectionEnabled: Boolean = false,
        val emailAddress: String? = null,
        val showAutofill: Boolean = false,
        val showSyncSetting: Boolean = false,
        val windowsWaitlistState: WindowsWaitlistState? = null,
        val networkProtectionState: NetPState = DISCONNECTED,
        val networkProtectionWaitlistState: NetPWaitlistState = NetPWaitlistState.NotUnlocked,
        val isAutoconsentEnabled: Boolean = false
    )

    enum class NetPState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        INVALID,
    }

    sealed class Command {
        object LaunchDefaultBrowser : Command()
        data class LaunchEmailProtection(val url: String) : Command()
        object LaunchEmailProtectionNotSupported : Command()
        object LaunchAutofillSettings : Command()
        object LaunchAccessibilitySettings : Command()
        object LaunchAddHomeScreenWidget : Command()
        object LaunchAppTPTrackersScreen : Command()
        object LaunchNetPManagementScreen : Command()
        object LaunchNetPWaitlist : Command()
        object LaunchAppTPOnboarding : Command()
        object LaunchMacOs : Command()
        object LaunchWindowsWaitlist : Command()
        object LaunchWindows : Command()
        object LaunchSyncSettings : Command()
        object LaunchPrivateSearchWebPage : Command()
        object LaunchWebTrackingProtectionScreen : Command()
        object LaunchCookiePopupProtectionScreen : Command()
        object LaunchFireButtonScreen : Command()
        object LaunchPermissionsAndPrivacyScreen : Command()
        object LaunchAppearanceScreen : Command()
    }

    private val viewState = MutableStateFlow(ViewState())

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)

    init {
        pixel.fire(SETTINGS_OPENED)
    }

    fun start() {
        val defaultBrowserAlready = defaultWebBrowserCapability.isDefaultBrowser()

        viewModelScope.launch {
            viewState.emit(
                currentViewState().copy(
                    isAppDefaultBrowser = defaultBrowserAlready,
                    showDefaultBrowserSetting = defaultWebBrowserCapability.deviceSupportsDefaultBrowserConfiguration(),
                    appTrackingProtectionOnboardingShown = appTrackingProtection.isOnboarded(),
                    appTrackingProtectionEnabled = vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN),
                    emailAddress = emailManager.getEmailAddress(),
                    showAutofill = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                    windowsWaitlistState = windowsSettingState(),
                    showSyncSetting = deviceSyncState.isFeatureEnabled(),
                    networkProtectionWaitlistState = netpWaitlistRepository.getState(appBuildConfig.isInternalBuild()),
                    isAutoconsentEnabled = autoconsent.isSettingEnabled()
                ),
            )
        }
    }

    // FIXME
    // We need to fix this. This logic as inside the start method but it messes with the unit tests
    // because when doing runningBlockingTest {} there is no delay and the tests crashes because this
    // becomes a while(true) without any delay
    fun startPollingVpnState() {
        viewModelScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                val isDeviceShieldEnabled = vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)
                val isNetPEnabled = vpnFeaturesRegistry.isFeatureRunning(NetPVpnFeature.NETP_VPN)
                viewState.value = currentViewState().copy(
                    appTrackingProtectionOnboardingShown = appTrackingProtection.isOnboarded(),
                    appTrackingProtectionEnabled = isDeviceShieldEnabled,
                    networkProtectionState = if (isNetPEnabled) CONNECTED else DISCONNECTED,
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
        viewModelScope.launch { command.send(Command.LaunchAddHomeScreenWidget) }
    }

    fun onDefaultBrowserSettingClicked() {
        val defaultBrowserSelected = defaultWebBrowserCapability.isDefaultBrowser()
        viewModelScope.launch {
            viewState.emit(currentViewState().copy(isAppDefaultBrowser = defaultBrowserSelected))
            command.send(Command.LaunchDefaultBrowser)
        }
        pixel.fire(SETTINGS_DEFAULT_BROWSER_PRESSED)
    }

    fun onPrivateSearchSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchPrivateSearchWebPage) }
        pixel.fire(SETTINGS_PRIVATE_SEARCH_PRESSED)
    }

    fun onWebTrackingProtectionSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchWebTrackingProtectionScreen) }
        pixel.fire(SETTINGS_WEB_TRACKING_PROTECTION_PRESSED)
    }

    fun onCookiePopupProtectionSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchCookiePopupProtectionScreen) }
        pixel.fire(SETTINGS_COOKIE_POPUP_PROTECTION_PRESSED)
    }

    fun onAutofillSettingsClick() {
        viewModelScope.launch { command.send(Command.LaunchAutofillSettings) }
        pixel.fire(SETTINGS_AUTOFILL_MANAGEMENT_OPENED)
    }

    fun onAccessibilitySettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchAccessibilitySettings) }
        pixel.fire(SETTINGS_ACCESSIBILITY_PRESSED)
    }

    fun onEmailProtectionSettingClicked() {
        viewModelScope.launch {
            val command = if (emailManager.isEmailFeatureSupported()) {
                Command.LaunchEmailProtection(EMAIL_PROTECTION_URL)
            } else {
                Command.LaunchEmailProtectionNotSupported
            }
            this@SettingsViewModel.command.send(command)
        }
        pixel.fire(SETTINGS_EMAIL_PROTECTION_PRESSED)
    }

    fun onMacOsSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchMacOs) }
        pixel.fire(SETTINGS_MAC_APP_PRESSED)
    }

    fun windowsSettingClicked() {
        viewModelScope.launch {
            if (windowsDownloadLinkFeature.self().isEnabled()) {
                command.send(Command.LaunchWindows)
            } else {
                command.send(Command.LaunchWindowsWaitlist)
            }
        }
        pixel.fire(SETTINGS_WINDOWS_APP_PRESSED)
    }

    fun onAppTPSettingClicked() {
        if (appTrackingProtection.isOnboarded()) {
            viewModelScope.launch { command.send(Command.LaunchAppTPTrackersScreen) }
        } else {
            viewModelScope.launch { command.send(Command.LaunchAppTPOnboarding) }
        }
        pixel.fire(SETTINGS_APPTP_PRESSED)
    }

    fun onNetPSettingClicked() {
        if (netpWaitlistRepository.getState(appBuildConfig.isInternalBuild()) == NetPWaitlistState.InBeta) {
            viewModelScope.launch { command.send(Command.LaunchNetPManagementScreen) }
        } else {
            viewModelScope.launch { command.send(Command.LaunchNetPWaitlist) }
        }
        pixel.fire(SETTINGS_NETP_PRESSED)
    }

    private fun windowsSettingState(): WindowsWaitlistState? {
        if (windowsDownloadLinkFeature.self().isEnabled()) {
            return FeatureEnabled
        }
        if (!windowsFeature.self().isEnabled()) return null
        return windowsWaitlist.getWaitlistState()
    }

    private fun currentViewState(): ViewState {
        return viewState.value
    }

    fun onSyncSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchSyncSettings) }
        pixel.fire(SETTINGS_SYNC_PRESSED)
    }

    fun onFireButtonSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchFireButtonScreen) }
        pixel.fire(SETTINGS_FIRE_BUTTON_PRESSED)
    }

    fun onPermissionsAndPrivacySettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchPermissionsAndPrivacyScreen) }
        pixel.fire(SETTINGS_PERMISSIONS_AND_PRIVACY_PRESSED)
    }

    fun onAppearanceSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchAppearanceScreen) }
        pixel.fire(SETTINGS_APPEARANCE_PRESSED)
    }

    fun onLaunchedFromNotification(pixelName: String) {
        pixel.fire(pixelName)
    }

    companion object {
        const val EMAIL_PROTECTION_URL = "https://duckduckgo.com/email"
    }
}
