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

package com.duckduckgo.sync.impl.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.api.SyncAutoRestore
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.onFailure
import com.duckduckgo.sync.impl.onSuccess
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_DISABLED
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsLaunchSource.SOURCE_SYNC_ENABLED
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowDeviceUnsupported
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowPreviousSessionReady
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.LoadingItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import java.io.File
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncActivityViewModel @Inject constructor(
    private val deviceAuthenticator: DeviceAuthenticator,
    private val recoveryCodePDF: RecoveryCodePDF,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStateMonitor: SyncStateMonitor,
    private val syncEngine: SyncEngine,
    private val dispatchers: DispatcherProvider,
    private val syncFeatureToggle: SyncFeatureToggle,
    private val settingsPageFeature: SettingsPageFeature,
    private val syncPixels: SyncPixels,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val syncAutoRestore: SyncAutoRestore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val syncSetupWideEvent: SyncSetupWideEvent,
) : ViewModel() {

    private var syncStateObserverJob = ConflatedJob()
    private var backgroundRefreshJob = ConflatedJob()

    // @Volatile because loadAutoRestoreState() writes these on the IO dispatcher while
    // onScreenExit() reads them from a different coroutine launched on the IO dispatcher.
    @Volatile private var autoRestoreAvailable = false

    // null until the first load from preference; used by onScreenExit() to detect changes.
    @Volatile private var initialAutoRestoreEnabled: Boolean? = null

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun commands(): Flow<Command> = command.receiveAsFlow().onStart {
        checkIfDeviceSupported()
    }

    fun viewState(): Flow<ViewState> =
        viewState.onStart {
            observeState()
        }.flowOn(dispatchers.io())

    private fun observeState() {
        // Reset so the next signedInState() call re-reads from DataStore. This is necessary because
        // the setup flow writes the auto-restore preference AFTER account creation, but the
        // syncStateMonitor can fire the signed-in event (and cache initialAutoRestoreEnabled=false)
        // while SetupAccountActivity is still on top and the user hasn't confirmed their preference yet.
        initialAutoRestoreEnabled = null
        syncStateObserverJob += syncStateMonitor.syncState()
            .onEach { syncState ->
                val state = if (syncState == OFF) {
                    signedOutState()
                } else {
                    signedInState()
                }
                viewState.value = state
            }.onStart {
                initViewStateThisDeviceState()
                fetchRemoteDevices()
                syncEngine.triggerSync(FEATURE_READ)
                schedulePeriodicRefresh()
            }.flowOn(dispatchers.io())
            .launchIn(viewModelScope)
    }

    private fun schedulePeriodicRefresh() {
        backgroundRefreshJob += viewModelScope.launch(dispatchers.io()) {
            while (isActive && syncFeatureToggle.automaticallyUpdateSyncSettings()) {
                delay(SETTINGS_REFRESH_RATE_MS)
                if (syncAccountRepository.isSignedIn()) {
                    fetchRemoteDevices(showLoadingState = false)
                }
            }
        }
    }

    private suspend fun checkIfDeviceSupported() {
        val isSupported = withContext(dispatchers.io()) {
            syncAccountRepository.isSyncSupported()
        }
        if (!isSupported) {
            command.send(ShowDeviceUnsupported)
        }
    }

    private suspend fun signedInState(): ViewState {
        val currentState = viewState.value
        val autoRestoreState = if (initialAutoRestoreEnabled == null) {
            loadAutoRestoreState()
        } else {
            AutoRestoreState(showToggle = autoRestoreAvailable, enabled = currentState.autoRestoreEnabled)
        }
        val syncedDevices = currentState.syncedDevices.ifEmpty {
            val thisDevice = syncAccountRepository.getThisConnectedDevice() ?: return signedOutState()
            listOf(SyncedDevice(thisDevice))
        }

        return ViewState(
            showAccount = syncAccountRepository.isSignedIn(),
            syncedDevices = syncedDevices,
            disabledSetupFlows = disabledSetupFlows(),
            aiChatSyncEnabled = syncFeatureToggle.allowAiChatSync(),
            newDesktopBrowserSettingEnabled = settingsPageFeature.newDesktopBrowserSettingEnabled().isEnabled(),
            showAutoRestoreToggle = autoRestoreState.showToggle,
            autoRestoreEnabled = autoRestoreState.enabled,
        )
    }

    private suspend fun initViewStateThisDeviceState() {
        val state = withContext(dispatchers.io()) {
            if (!syncAccountRepository.isSignedIn()) {
                signedOutState()
            } else {
                signedInState()
            }
        }

        viewState.value = state
    }

    private suspend fun loadAutoRestoreState(): AutoRestoreState {
        autoRestoreAvailable = syncAutoRestoreManager.isAutoRestoreAvailable()
        if (!autoRestoreAvailable) {
            return AutoRestoreState(showToggle = false, enabled = false)
        }

        val enabled = syncAutoRestoreManager.isRestoreOnReinstallEnabled().also { initialAutoRestoreEnabled = it }
        syncPixels.fireAutoRestoreSettingsPageShown()
        return AutoRestoreState(showToggle = true, enabled = enabled)
    }

    private data class AutoRestoreState(val showToggle: Boolean, val enabled: Boolean)

    data class ViewState(
        val showAccount: Boolean = false,
        val syncedDevices: List<SyncDeviceListItem> = emptyList(),
        val disabledSetupFlows: List<SetupFlows> = emptyList(),
        val aiChatSyncEnabled: Boolean = false,
        val newDesktopBrowserSettingEnabled: Boolean = false,
        val showAutoRestoreToggle: Boolean = false,
        val autoRestoreEnabled: Boolean = false,
    )

    sealed class SetupFlows {
        data object SignInFlow : SetupFlows()
        data object CreateAccountFlow : SetupFlows()
    }

    sealed class Command {
        data object SyncWithAnotherDevice : Command()
        data object AddAnotherDevice : Command()
        data class DeepLinkIntoSetup(val barcodeSyncUrl: SyncBarcodeUrl) : Command()
        data class AskSetupSyncDeepLink(val syncBarcodeUrl: SyncBarcodeUrl) : Command()
        data object IntroCreateAccount : Command()
        data object IntroRecoverSyncData : Command()
        data object ShowRecoveryCode : Command()
        data object ShowDeviceConnected : Command()
        data class AskTurnOffSync(val device: ConnectedDevice) : Command()
        data object AskDeleteAccount : Command()
        data object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
        data class AskRemoveDevice(val device: ConnectedDevice) : Command()
        data class AskEditDevice(val device: ConnectedDevice) : Command()
        data class ShowError(
            @StringRes val message: Int,
            val reason: String = "",
        ) : Command()

        data object ShowDeviceUnsupported : Command()
        data object RequestSetupAuthentication : Command()
        data class LaunchSyncGetOnOtherPlatforms(val source: SyncGetOnOtherPlatformsLaunchSource) : Command()
        data class LaunchLearnMore(val url: String) : Command()
        data class ShowPreviousSessionReady(val originalFlow: OriginalFlow) : Command()
        data class LaunchOriginalFlow(val originalFlow: OriginalFlow) : Command()
    }

    enum class OriginalFlow {
        SYNC_THIS_DEVICE,
        SYNC_WITH_ANOTHER,
        RECOVER_SYNCED_DATA,
    }

    fun onSyncWithAnotherDevice() {
        viewModelScope.launch(dispatchers.io()) {
            requiresSetupAuthentication {
                if (syncAutoRestore.canRestore()) {
                    command.send(ShowPreviousSessionReady(OriginalFlow.SYNC_WITH_ANOTHER))
                } else {
                    command.send(Command.SyncWithAnotherDevice)
                }
            }
        }
    }

    fun onAddAnotherDevice() {
        viewModelScope.launch {
            requiresSetupAuthentication {
                command.send(Command.AddAnotherDevice)
            }
        }
    }

    fun onSyncThisDevice(source: String? = null) {
        viewModelScope.launch(dispatchers.io()) {
            syncSetupWideEvent.onFlowStarted(source)
            requiresSetupAuthentication(
                onDeviceAuthNotEnrolled = { syncSetupWideEvent.onDeviceAuthNotEnrolled() },
            ) {
                if (syncAutoRestore.canRestore()) {
                    command.send(ShowPreviousSessionReady(OriginalFlow.SYNC_THIS_DEVICE))
                } else {
                    command.send(IntroCreateAccount)
                }
            }
        }
    }

    fun onRecoverYourSyncedData() {
        viewModelScope.launch(dispatchers.io()) {
            requiresSetupAuthentication {
                if (syncAutoRestore.canRestore()) {
                    command.send(ShowPreviousSessionReady(OriginalFlow.RECOVER_SYNCED_DATA))
                } else {
                    syncPixels.fireAutoRestoreSettingsManualRecoveryShown()
                    command.send(Command.IntroRecoverSyncData)
                }
            }
        }
    }

    fun onContinueSetupAfterSkipRestore(originalFlow: OriginalFlow?) {
        if (originalFlow == null) return
        viewModelScope.launch(dispatchers.io()) {
            val source = when (originalFlow) {
                OriginalFlow.SYNC_WITH_ANOTHER -> SyncPixelParameters.AUTO_RESTORE_SOURCE_PAIRING
                OriginalFlow.SYNC_THIS_DEVICE -> SyncPixelParameters.AUTO_RESTORE_SOURCE_BACKUP
                OriginalFlow.RECOVER_SYNCED_DATA -> SyncPixelParameters.AUTO_RESTORE_SOURCE_RECOVER
            }
            when (val result = syncAutoRestoreManager.clearAutoRestoreData()) {
                is Success -> {
                    syncPixels.fireAutoRestorePreservedAccountCleared(source)
                    command.send(Command.LaunchOriginalFlow(originalFlow))
                }
                is Error -> {
                    syncPixels.fireAutoRestorePreservedAccountClearFailed(
                        source = source,
                        errorCode = result.code.toString(),
                        errorMessage = result.reason,
                    )
                    command.send(ShowError(R.string.sync_general_error, result.reason))
                }
            }
        }
    }

    fun onLoginSuccess() {
        viewModelScope.launch {
            command.send(Command.ShowRecoveryCode)
        }
    }

    fun onTurnOffClicked() {
        viewModelScope.launch {
            syncAccountRepository.getThisConnectedDevice()?.let {
                command.send(AskTurnOffSync(it))
            } ?: showAccountDetailsIfNeeded()
        }
    }

    private suspend fun fetchRemoteDevices(showLoadingState: Boolean = true) {
        if (showLoadingState) {
            viewState.value = viewState.value.showDeviceListItemLoading()
        }

        val result = withContext(dispatchers.io()) {
            syncAccountRepository.getConnectedDevices()
        }
        if (result is Success) {
            val newState = viewState.value.hideDeviceListItemLoading().setDevices(result.data.map { SyncedDevice(it) })
            viewState.value = newState
        } else {
            viewState.value = viewState.value.hideDeviceListItemLoading()
        }
    }

    fun onTurnOffSyncConfirmed(connectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            syncPixels.fireUserConfirmedToTurnOffSync()

            viewState.value = viewState.value.hideAccount()
            when (val result = syncAccountRepository.logout(connectedDevice.deviceId)) {
                is Error -> {
                    viewState.value = viewState.value.showAccount()
                    command.send(ShowError(R.string.sync_turn_off_error, result.reason))
                }

                is Success -> {
                    viewState.value = signedOutState()
                }
            }
        }
    }

    fun onTurnOffSyncCancelled() {
        showAccountDetailsIfNeeded()
    }

    fun onConnectionCancelled() {
        viewModelScope.launch {
            syncSetupWideEvent.onFlowCancelled()
        }
        showAccountDetailsIfNeeded()
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch {
            command.send(AskDeleteAccount)
        }
    }

    fun onDeleteAccountConfirmed() {
        viewModelScope.launch(dispatchers.io()) {
            viewState.value = viewState.value.hideAccount()
            when (val result = syncAccountRepository.deleteAccount()) {
                is Error -> {
                    viewState.value = viewState.value.showAccount()
                    command.send(ShowError(R.string.sync_turn_off_error, result.reason))
                }

                is Success -> {
                    viewState.value = signedOutState()
                }
            }
        }
    }

    fun onDeleteAccountCancelled() {
        showAccountDetailsIfNeeded()
    }

    fun onSaveRecoveryCodeClicked() {
        viewModelScope.launch {
            requiresSetupAuthentication {
                command.send(CheckIfUserHasStoragePermission)
            }
        }
    }

    fun onAutoRestoreToggleChanged(enabled: Boolean) {
        logcat { "Sync-Recovery: restore on reinstall toggle changed to $enabled (pending until screen stopped)" }
        viewState.value = viewState.value.copy(autoRestoreEnabled = enabled)
    }

    fun onScreenExit() {
        val current = viewState.value
        val initial = initialAutoRestoreEnabled
        if (!autoRestoreAvailable || initial == null) {
            logcat { "Sync-Recovery: screen exit — auto-restore not available, nothing to write" }
            return
        }
        if (current.autoRestoreEnabled == initial) {
            logcat { "Sync-Recovery: screen exit — restore on reinstall unchanged (${current.autoRestoreEnabled}), nothing to write" }
            return
        }
        logcat { "Sync-Recovery: screen exit — committing restore on reinstall: $initial -> ${current.autoRestoreEnabled}" }

        // appCoroutineScope as we don't want this cancelled even if the activity / view model lifecycle ends
        appCoroutineScope.launch(dispatchers.io()) {
            if (current.autoRestoreEnabled) {
                syncPixels.fireAutoRestoreSettingsPageToggleEnabled()
                syncAccountRepository.getRecoveryCode()
                    .onSuccess { authCode ->
                        val deviceId = syncAccountRepository.getThisConnectedDevice()?.deviceId
                        syncAutoRestoreManager.saveAutoRestoreData(authCode.rawCode, deviceId)
                        initialAutoRestoreEnabled = true
                    }
                    .onFailure { error ->
                        logcat(LogPriority.ERROR) { "Sync-Recovery: failed to get recovery code, preference not written - ${error.reason}" }
                    }
            } else {
                syncPixels.fireAutoRestoreSettingsPageToggleDisabled()
                logcat { "Sync-Recovery: clearing recovery payload from Block Store" }
                syncAutoRestoreManager.clearAutoRestoreData()
                initialAutoRestoreEnabled = false
            }
        }
    }

    fun generateRecoveryCode(viewContext: Context) {
        viewModelScope.launch(dispatchers.io()) {
            syncAccountRepository.getRecoveryCode().onSuccess { authCode ->
                kotlin.runCatching {
                    recoveryCodePDF.generateAndStoreRecoveryCodePDF(viewContext, authCode.rawCode)
                }.onSuccess { generateRecoveryCodePDF ->
                    command.send(RecoveryCodePDFSuccess(generateRecoveryCodePDF))
                }.onFailure {
                    syncPixels.fireSyncAccountErrorPixel(Error(reason = it.message.toString()), type = SyncAccountOperation.CREATE_PDF)
                    command.send(ShowError(R.string.sync_recovery_pdf_error))
                }
            }.onFailure {
                command.send(ShowError(R.string.sync_recovery_pdf_error))
            }
        }
    }

    fun onEditDeviceClicked(device: ConnectedDevice) {
        viewModelScope.launch {
            command.send(AskEditDevice(device))
        }
    }

    fun onRemoveDeviceClicked(device: ConnectedDevice) {
        viewModelScope.launch {
            command.send(AskRemoveDevice(device))
        }
    }

    fun onRemoveDeviceConfirmed(device: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            val oldList = viewState.value.syncedDevices
            viewState.value = viewState.value.showDeviceListItemLoading(device)
            when (val result = syncAccountRepository.logout(device.deviceId)) {
                is Error -> {
                    viewState.value = viewState.value.setDevices(oldList)
                    command.send(ShowError(R.string.sync_remove_device_error, result.reason))
                }

                is Success -> fetchRemoteDevices()
            }
        }
    }

    fun onDeviceEdited(editedConnectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            val oldList = viewState.value.syncedDevices
            viewState.value = viewState.value.showDeviceListItemLoading(editedConnectedDevice)
            when (val result = syncAccountRepository.renameDevice(editedConnectedDevice)) {
                is Error -> {
                    viewState.value = viewState.value.setDevices(oldList)
                    command.send(ShowError(R.string.sync_edit_device_error, result.reason))
                }

                is Success -> fetchRemoteDevices()
            }
        }
    }

    fun onDeviceConnected() {
        viewModelScope.launch {
            fetchRemoteDevices()
        }
    }

    fun onGetOnOtherPlatformsClickedWhenSyncDisabled() {
        viewModelScope.launch(dispatchers.main()) {
            command.send(LaunchSyncGetOnOtherPlatforms(source = SOURCE_SYNC_DISABLED))
        }
    }

    fun onGetOnOtherPlatformsClickedWhenSyncEnabled() {
        viewModelScope.launch(dispatchers.main()) {
            command.send(LaunchSyncGetOnOtherPlatforms(source = SOURCE_SYNC_ENABLED))
        }
    }

    fun onLearnMoreClicked() {
        viewModelScope.launch {
            command.send(Command.LaunchLearnMore(LEARN_MORE_URL))
        }
    }

    private fun showAccountDetailsIfNeeded() {
        viewModelScope.launch(dispatchers.io()) {
            if (syncAccountRepository.isSignedIn()) {
                viewState.value = viewState.value.showAccount()
            } else {
                viewState.value = signedOutState()
            }
        }
    }

    private fun disabledSetupFlows(): List<SetupFlows> {
        if (!syncFeatureToggle.allowSetupFlows()) return listOf(SetupFlows.SignInFlow, SetupFlows.CreateAccountFlow)
        if (!syncFeatureToggle.allowCreateAccount()) return listOf(SetupFlows.CreateAccountFlow)
        return emptyList()
    }

    private fun signedOutState(): ViewState = ViewState(
        disabledSetupFlows = disabledSetupFlows(),
        aiChatSyncEnabled = syncFeatureToggle.allowAiChatSync(),
        newDesktopBrowserSettingEnabled = settingsPageFeature.newDesktopBrowserSettingEnabled().isEnabled(),
    )

    private suspend fun requiresSetupAuthentication(onDeviceAuthNotEnrolled: suspend() -> Unit = {}, action: suspend () -> Unit) {
        val hasValidDeviceAuthentication = deviceAuthenticator.hasValidDeviceAuthentication()
        if (hasValidDeviceAuthentication.not() && deviceAuthenticator.isAuthenticationRequired()) {
            onDeviceAuthNotEnrolled()
            command.send(RequestSetupAuthentication)
        } else {
            action()
        }
    }

    private fun ViewState.setDevices(devices: List<SyncDeviceListItem>) = copy(syncedDevices = devices)
    private fun ViewState.hideDeviceListItemLoading() = copy(syncedDevices = syncedDevices.filterNot { it is LoadingItem })
    private fun ViewState.showDeviceListItemLoading() = copy(syncedDevices = syncedDevices + LoadingItem)
    private fun ViewState.showDeviceListItemLoading(updatingDevice: ConnectedDevice): ViewState {
        return copy(
            syncedDevices = syncedDevices.map {
                if (it is SyncedDevice && it.device.deviceId == updatingDevice.deviceId) {
                    it.copy(loading = true)
                } else {
                    it
                }
            },
        )
    }

    private fun ViewState.showAccount() = copy(showAccount = true)
    private fun ViewState.hideAccount() = copy(showAccount = false)

    fun processSetupDeepLink(setupUrl: String) {
        logcat { "Sync-setup: got setup deep link $setupUrl" }
        viewModelScope.launch(dispatchers.io()) {
            // parse here to test validity before asking user to use it
            val parsed = SyncBarcodeUrl.parseUrl(setupUrl)

            if (parsed == null) {
                logcat { "Sync-setup: failed to parse setup URL $setupUrl" }
            } else {
                command.send(Command.AskSetupSyncDeepLink(parsed))
            }
        }
    }

    fun onUserAgreedToDeepLinkIntoSync(barcodeSyncUrl: SyncBarcodeUrl) {
        viewModelScope.launch(dispatchers.io()) {
            requiresSetupAuthentication {
                command.send(Command.DeepLinkIntoSetup(barcodeSyncUrl))
            }
        }
    }

    companion object {
        private const val SETTINGS_REFRESH_RATE_MS = 5_000L
        private const val LEARN_MORE_URL =
            "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/recovery-codes-and-troubleshooting#data-expiration"
    }
}
