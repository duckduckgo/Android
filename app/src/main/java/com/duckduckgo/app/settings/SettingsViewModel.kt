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

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.Hidden
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.Pending
import com.duckduckgo.app.settings.SettingsViewModel.NetPEntryState.ShowState
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.common.ui.view.listitem.CheckListItem
import com.duckduckgo.common.ui.view.listitem.CheckListItem.CheckItemStatus
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.app.tracking.AppTrackingProtection
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.sync.api.DeviceSyncState
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("NoLifecycleObserver")
@ContributesViewModel(ActivityScope::class)
class SettingsViewModel @Inject constructor(
    private val defaultWebBrowserCapability: DefaultBrowserDetector,
    private val appTrackingProtection: AppTrackingProtection,
    private val pixel: Pixel,
    private val emailManager: EmailManager,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val networkProtectionState: NetworkProtectionState,
    private val deviceSyncState: DeviceSyncState,
    private val networkProtectionWaitlist: NetworkProtectionWaitlist,
    private val dispatcherProvider: DispatcherProvider,
    private val autoconsent: Autoconsent,
    private val subscriptions: Subscriptions,
) : ViewModel(), DefaultLifecycleObserver {

    data class ViewState(
        val showDefaultBrowserSetting: Boolean = false,
        val isAppDefaultBrowser: Boolean = false,
        val appTrackingProtectionOnboardingShown: Boolean = false,
        val appTrackingProtectionEnabled: Boolean = false,
        val emailAddress: String? = null,
        val showAutofill: Boolean = false,
        val showSyncSetting: Boolean = false,
        val networkProtectionEntryState: NetPEntryState = Hidden,
        val isAutoconsentEnabled: Boolean = false,
        val isPrivacyProEnabled: Boolean = false,
    )

    sealed class NetPEntryState {
        object Hidden : NetPEntryState()
        object Pending : NetPEntryState()
        data class ShowState(
            val icon: CheckItemStatus,
            @StringRes val subtitle: Int,
        ) : NetPEntryState()
    }

    sealed class Command {
        object LaunchDefaultBrowser : Command()
        data class LaunchEmailProtection(val url: String) : Command()
        object LaunchEmailProtectionNotSupported : Command()
        object LaunchAutofillSettings : Command()
        object LaunchAccessibilitySettings : Command()
        object LaunchAddHomeScreenWidget : Command()
        object LaunchAppTPTrackersScreen : Command()
        data class LaunchNetPWaitlist(val screen: ActivityParams) : Command()
        object LaunchAppTPOnboarding : Command()
        object LaunchMacOs : Command()
        object LaunchWindows : Command()
        object LaunchSyncSettings : Command()
        object LaunchPrivateSearchWebPage : Command()
        object LaunchWebTrackingProtectionScreen : Command()
        object LaunchCookiePopupProtectionScreen : Command()
        object LaunchFireButtonScreen : Command()
        object LaunchPermissionsScreen : Command()
        object LaunchAppearanceScreen : Command()
        object LaunchAboutScreen : Command()
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

    private suspend fun getNetworkProtectionEntryState(networkProtectionConnectionState: ConnectionState): NetPEntryState {
        return when (val networkProtectionWaitlistState = networkProtectionWaitlist.getState()) {
            is NetPWaitlistState.InBeta -> {
                if (networkProtectionWaitlistState.termsAccepted || networkProtectionState.isOnboarded()) {
                    val subtitle = when (networkProtectionConnectionState) {
                        CONNECTED -> R.string.netpSettingsConnected
                        CONNECTING -> R.string.netpSettingsConnecting
                        else -> R.string.netpSettingsDisconnected
                    }

                    val netPItemStatus = if (networkProtectionConnectionState != DISCONNECTED) {
                        CheckListItem.CheckItemStatus.ENABLED
                    } else {
                        CheckListItem.CheckItemStatus.WARNING
                    }

                    ShowState(
                        icon = netPItemStatus,
                        subtitle = subtitle,
                    )
                } else {
                    Pending
                }
            }
            NetPWaitlistState.NotUnlocked -> Hidden
            NetPWaitlistState.PendingInviteCode, NetPWaitlistState.JoinedWaitlist -> Pending
        }
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
                    networkProtectionEntryState = (if (networkProtectionState.isRunning()) CONNECTED else DISCONNECTED).run {
                        getNetworkProtectionEntryState(this)
                    },
                    isAutoconsentEnabled = autoconsent.isSettingEnabled(),
                ),
            )
            networkProtectionState.getConnectionStateFlow()
                .onEach {
                    viewState.emit(
                        currentViewState().copy(
                            networkProtectionEntryState = getNetworkProtectionEntryState(it),
                        ),
                    )
                }.flowOn(dispatcherProvider.main())
                .launchIn(viewModelScope)
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
                val isPrivacyProEnabled = subscriptions.isEnabled()
                val currentState = currentViewState()
                viewState.value = currentState.copy(
                    appTrackingProtectionOnboardingShown = appTrackingProtection.isOnboarded(),
                    appTrackingProtectionEnabled = isDeviceShieldEnabled,
                    isPrivacyProEnabled = isPrivacyProEnabled,
                    networkProtectionEntryState = if (isPrivacyProEnabled) Hidden else currentState.networkProtectionEntryState,
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
    }

    fun onAccessibilitySettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchAccessibilitySettings) }
        pixel.fire(SETTINGS_ACCESSIBILITY_PRESSED)
    }

    fun onAboutSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchAboutScreen) }
        pixel.fire(SETTINGS_ABOUT_PRESSED)
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
            command.send(Command.LaunchWindows)
        }
        pixel.fire(SETTINGS_WINDOWS_APP_PRESSED)
    }

    fun onAppTPSettingClicked() {
        viewModelScope.launch {
            if (appTrackingProtection.isOnboarded()) {
                command.send(Command.LaunchAppTPTrackersScreen)
            } else {
                command.send(Command.LaunchAppTPOnboarding)
            }
            pixel.fire(SETTINGS_APPTP_PRESSED)
        }
    }

    fun onNetPSettingClicked() {
        viewModelScope.launch {
            val screen = networkProtectionWaitlist.getScreenForCurrentState()
            screen?.let {
                command.send(Command.LaunchNetPWaitlist(screen))
                pixel.fire(SETTINGS_NETP_PRESSED)
            } ?: Timber.w("Get screen for current NetP state is null")
        }
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

    fun onPermissionsSettingClicked() {
        viewModelScope.launch { command.send(Command.LaunchPermissionsScreen) }
        pixel.fire(SETTINGS_PERMISSIONS_PRESSED)
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
