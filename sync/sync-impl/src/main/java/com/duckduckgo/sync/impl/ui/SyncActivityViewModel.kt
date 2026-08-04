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
import com.duckduckgo.app.clipboard.ClipboardInteractor
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
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncFeature
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
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskToCopyRecoveryCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowDeviceUnsupported
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowPreviousSessionReady
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.LoadingItem
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
    private val clipboard: ClipboardInteractor,
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
    private val syncFeature: SyncFeature,
) : ViewModel() {

    private val syncStateObserverJob = ConflatedJob()
    private val backgroundRefreshJob = ConflatedJob()
    private val fetchDevicesJob = ConflatedJob()

    // @Volatile because loadAutoRestoreState() writes these on the IO dispatcher while
    // onScreenExit() reads them from a different coroutine launched on the IO dispatcher.
    @Volatile private var autoRestoreAvailable = false

    // null until the first load from preference; used by onScreenExit() to detect changes.
    @Volatile private var initialAutoRestoreEnabled: Boolean? = null

    @Volatile private var isAtomicViewStateUpdateEnabled = false

    private val command = Channel<Command>(1, DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())

    init {
        viewModelScope.launch {
            syncFeature.updateSyncActivityViewStateAtomically().enabled().collect { isAtomicViewStateUpdateEnabled = it }
        }
    }
    fun commands(): Flow<Command> = command.receiveAsFlow().onStart {
        checkIfDeviceSupported()
    }

    fun viewState(): Flow<ViewState> =
        viewState.onStart {
            observeState()
        }.flowOn(dispatchers.io())

    private fun observeState() {
        // Reset so the next updateSignedInState() call re-reads from DataStore. This is necessary because
        // the setup flow writes the auto-restore preference AFTER account creation, but the
        // syncStateMonitor can fire the signed-in event (and cache initialAutoRestoreEnabled=false)
        // while SetupAccountActivity is still on top and the user hasn't confirmed their preference yet.
        initialAutoRestoreEnabled = null
        syncStateObserverJob += syncStateMonitor.syncState()
            .onEach { syncState ->
                if (syncState == OFF) {
                    updateViewState { signedOutState() }
                } else {
                    updateSignedInState()
                }
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

    private suspend fun updateSignedInState() {
        val autoRestoreState = if (initialAutoRestoreEnabled == null) loadAutoRestoreState() else null
        val showAccount = syncAccountRepository.isSignedIn()
        val thisDevice = syncAccountRepository.getThisConnectedDevice()
        val signedOutState = signedOutState()

        updateViewState { current ->
            val syncedDevices = current.syncedDevices.ifEmpty {
                thisDevice?.let { listOf(SyncedDevice(it)) } ?: return@updateViewState signedOutState
            }
            signedOutState.copy(
                showAccount = showAccount,
                syncedDevices = syncedDevices,
                showAutoRestoreToggle = autoRestoreState?.showToggle ?: autoRestoreAvailable,
                autoRestoreEnabled = autoRestoreState?.enabled ?: current.autoRestoreEnabled,
                isThisDeviceSyncing = current.isThisDeviceSyncing,
            )
        }
    }

    private suspend fun initViewStateThisDeviceState() {
        withContext(dispatchers.io()) {
            if (!syncAccountRepository.isSignedIn()) {
                updateViewState { signedOutState() }
            } else {
                updateSignedInState()
            }
        }
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
        val isThisDeviceSyncing: Boolean = false,
    )

    sealed class SetupFlows {
        data object SignInFlow : SetupFlows()
        data object CreateAccountFlow : SetupFlows()
    }

    sealed class Command {
        data object SyncWithAnotherDevice : Command()
        data object AddAnotherDevice : Command()
        data class DeepLinkIntoSetup(val barcodeSyncUrl: SyncBarcodeUrl, val isSignedIn: Boolean) : Command()
        data class AskSetupSyncDeepLink(val syncBarcodeUrl: SyncBarcodeUrl) : Command()
        data object IntroCreateAccount : Command()
        data object IntroRecoverSyncData : Command()
        data object ShowRecoveryCode : Command()
        data class AskTurnOffSync(val device: ConnectedDevice) : Command()
        data object AskDeleteAccount : Command()
        data object CheckIfUserHasStoragePermission : Command()
        data class RecoveryCodePDFSuccess(val recoveryCodePDFFile: File) : Command()
        data object AskToCopyRecoveryCode : Command()
        data class AskRemoveDevice(val device: ConnectedDevice) : Command()
        data class AskEditDevice(val device: ConnectedDevice, val requireAuthentication: Boolean) : Command()
        data class ShowError(
            @StringRes val message: Int,
            val reason: String = "",
        ) : Command()

        data class ShowMessage(
            @StringRes val message: Int,
        ) : Command()

        data object ShowDeviceUnsupported : Command()
        data class RequestSetupAuthentication(val forSyncThisDevice: Boolean) : Command()
        data class LaunchSyncGetOnOtherPlatforms(val source: SyncGetOnOtherPlatformsLaunchSource) : Command()
        data class LaunchLearnMore(val url: String) : Command()
        data class ShowPreviousSessionReady(val syncEntryPoint: SyncEntryPoint) : Command()
        data class LaunchOriginalFlow(val syncEntryPoint: SyncEntryPoint) : Command()
    }

    fun onSyncWithAnotherDevice() {
        viewModelScope.launch(dispatchers.io()) {
            requiresSetupAuthentication {
                if (syncAutoRestore.canRestore()) {
                    command.send(ShowPreviousSessionReady(SyncEntryPoint.ADD_DEVICE))
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
        updateViewState { it.setThisDeviceSyncInProgress() }
        viewModelScope.launch(dispatchers.io()) {
            syncSetupWideEvent.onFlowStarted(source)
            requiresSetupAuthentication(
                forSyncThisDevice = true,
                onDeviceAuthNotEnrolled = { syncSetupWideEvent.onDeviceAuthNotEnrolled() },
            ) {
                if (syncAutoRestore.canRestore()) {
                    command.send(ShowPreviousSessionReady(SyncEntryPoint.SYNC_NEW_ACCOUNT))
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
                    command.send(ShowPreviousSessionReady(SyncEntryPoint.RECOVER_SYNCED_DATA))
                } else {
                    syncPixels.fireAutoRestoreSettingsManualRecoveryShown()
                    command.send(Command.IntroRecoverSyncData)
                }
            }
        }
    }

    fun onContinueSetupAfterSkipRestore(syncEntryPoint: SyncEntryPoint?) {
        if (syncEntryPoint == null) return
        viewModelScope.launch(dispatchers.io()) {
            val source = when (syncEntryPoint) {
                SyncEntryPoint.ADD_DEVICE -> SyncPixelParameters.AUTO_RESTORE_SOURCE_PAIRING
                SyncEntryPoint.SYNC_NEW_ACCOUNT -> SyncPixelParameters.AUTO_RESTORE_SOURCE_BACKUP
                SyncEntryPoint.RECOVER_SYNCED_DATA -> SyncPixelParameters.AUTO_RESTORE_SOURCE_RECOVER
            }
            when (val result = syncAutoRestoreManager.clearAutoRestoreData()) {
                is Success -> {
                    syncPixels.fireAutoRestorePreservedAccountCleared(source)
                    command.send(Command.LaunchOriginalFlow(syncEntryPoint))
                }
                is Error -> {
                    updateViewState { it.setThisDeviceSyncIdle() }
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

    private fun fetchRemoteDevices(showLoadingState: Boolean = true) {
        fetchDevicesJob += viewModelScope.launch(dispatchers.io()) {
            if (showLoadingState) {
                updateViewState { it.showDeviceListItemLoading() }
            }

            val result = syncAccountRepository.getConnectedDevices()
            ensureActive() // don't apply a result that was superseded while in flight
            updateViewState { current ->
                if (result is Success) {
                    current.hideDeviceListItemLoading().setDevices(result.data.map { SyncedDevice(it) })
                } else {
                    current.hideDeviceListItemLoading()
                }
            }
        }
    }

    fun onTurnOffSyncConfirmed(connectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            syncPixels.fireUserConfirmedToTurnOffSync()

            updateViewState { it.hideAccount().setThisDeviceSyncIdle() }
            when (val result = syncAccountRepository.logout(connectedDevice.deviceId)) {
                is Error -> {
                    updateViewState { it.showAccount() }
                    command.send(ShowError(R.string.sync_turn_off_error, result.reason))
                }

                is Success -> {
                    updateViewState { signedOutState() }
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

    fun onDeleteAccountClicked(requireAuth: Boolean) {
        viewModelScope.launch {
            if (requireAuth) {
                requiresSetupAuthentication {
                    command.send(AskDeleteAccount)
                }
            } else {
                command.send(AskDeleteAccount)
            }
        }
    }

    fun onDeleteAccountConfirmed() {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState { it.hideAccount() }
            when (val result = syncAccountRepository.deleteAccount()) {
                is Error -> {
                    updateViewState { it.showAccount() }
                    command.send(ShowError(R.string.sync_turn_off_error, result.reason))
                }

                is Success -> {
                    updateViewState { signedOutState() }
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

    fun onCopyRecoveryCodeClicked() {
        viewModelScope.launch {
            requiresSetupAuthentication {
                command.send(AskToCopyRecoveryCode)
            }
        }
    }

    fun onCopyRecoveryCodeAuthenticated() {
        viewModelScope.launch(dispatchers.io()) {
            when (val result = syncAccountRepository.getRecoveryCode()) {
                is Success -> {
                    val isNotificationShown = clipboard.copyToClipboard(result.data.rawCode, isSensitive = true)
                    if (!isNotificationShown) {
                        command.send(ShowMessage(R.string.sync_code_copied_message))
                    }
                }

                is Error -> {
                    command.send(ShowError(R.string.sync_general_error, result.reason))
                }
            }
        }
    }

    fun onAutoRestoreToggleChanged(enabled: Boolean) {
        logcat { "Sync-Recovery: restore on reinstall toggle changed to $enabled (pending until screen stopped)" }
        updateViewState { it.copy(autoRestoreEnabled = enabled) }
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

    fun onEditDeviceClicked(device: ConnectedDevice, requireAuth: Boolean) {
        viewModelScope.launch {
            val askEditCommand = AskEditDevice(device, requireAuthentication = requireAuth)
            if (requireAuth) {
                requiresSetupAuthentication {
                    command.send(askEditCommand)
                }
            } else {
                command.send(askEditCommand)
            }
        }
    }

    fun onRemoveDeviceClicked(device: ConnectedDevice) {
        viewModelScope.launch {
            command.send(AskRemoveDevice(device))
        }
    }

    fun onRemoveDeviceConfirmed(device: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState { it.showDeviceListItemLoading(device) }
            when (val result = syncAccountRepository.logout(device.deviceId)) {
                is Error -> {
                    updateViewState { it.hideDeviceListItemLoading(device) }
                    command.send(ShowError(R.string.sync_remove_device_error, result.reason))
                }

                is Success -> {
                    // Remove the device optimistically instead of refetching: a fetch that read the
                    // server before the logout could otherwise land afterwards and re-insert the
                    // device until the next periodic refresh corrects it.
                    fetchDevicesJob.cancel()
                    updateViewState { current ->
                        current.setDevices(
                            current.syncedDevices.filterNot { it is SyncedDevice && it.device.deviceId == device.deviceId },
                        )
                    }
                }
            }
        }
    }

    fun onDeviceEdited(editedConnectedDevice: ConnectedDevice) {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState { it.showDeviceListItemLoading(editedConnectedDevice) }
            when (val result = syncAccountRepository.renameDevice(editedConnectedDevice)) {
                is Error -> {
                    updateViewState { it.hideDeviceListItemLoading(editedConnectedDevice) }
                    command.send(ShowError(R.string.sync_edit_device_error, result.reason))
                }

                is Success -> fetchRemoteDevices()
            }
        }
    }

    fun onDeviceConnected() {
        updateViewState { it.setThisDeviceSyncIdle() }
        fetchRemoteDevices()
    }

    fun onDevicesUpdated() {
        fetchRemoteDevices(showLoadingState = false)
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

    fun onSyncThisDeviceCanceled() {
        updateViewState { it.setThisDeviceSyncIdle() }
    }

    private fun showAccountDetailsIfNeeded() {
        viewModelScope.launch(dispatchers.io()) {
            if (syncAccountRepository.isSignedIn()) {
                updateViewState { it.showAccount() }
            } else {
                updateViewState { signedOutState() }
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

    private suspend fun requiresSetupAuthentication(
        forSyncThisDevice: Boolean = false,
        onDeviceAuthNotEnrolled: suspend () -> Unit = {},
        action: suspend () -> Unit,
    ) {
        val hasValidDeviceAuthentication = deviceAuthenticator.hasValidDeviceAuthentication()
        if (hasValidDeviceAuthentication.not() && deviceAuthenticator.isAuthenticationRequired()) {
            onDeviceAuthNotEnrolled()
            command.send(RequestSetupAuthentication(forSyncThisDevice))
        } else {
            action()
        }
    }

    private fun updateViewState(update: (ViewState) -> ViewState) {
        if (isAtomicViewStateUpdateEnabled) {
            viewState.update(update)
        } else {
            viewState.value = update(viewState.value)
        }
    }

    private fun ViewState.setDevices(devices: List<SyncDeviceListItem>) = copy(syncedDevices = devices)
    private fun ViewState.hideDeviceListItemLoading() = copy(syncedDevices = syncedDevices.filterNot { it is LoadingItem })
    private fun ViewState.hideDeviceListItemLoading(updatedDevice: ConnectedDevice): ViewState {
        return copy(
            syncedDevices = syncedDevices.map {
                if (it is SyncedDevice && it.device.deviceId == updatedDevice.deviceId) {
                    it.copy(loading = false)
                } else {
                    it
                }
            },
        )
    }
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
    private fun ViewState.setThisDeviceSyncInProgress() = copy(isThisDeviceSyncing = true)
    private fun ViewState.setThisDeviceSyncIdle() = copy(isThisDeviceSyncing = false)

    fun processSetupDeepLink(setupUrl: String) {
        logcat { "Sync-setup: got setup deep link $setupUrl" }
        viewModelScope.launch(dispatchers.io()) {
            val parsed = SyncBarcodeUrl.parseUrl(setupUrl)
            if (parsed == null) {
                logcat { "Sync-setup: failed to parse setup URL $setupUrl" }
                return@launch
            }
            when (parsed.protocolVersion) {
                SyncBarcodeUrl.ProtocolVersion.V1 -> {
                    // V1 URL-based sync setup only accepts exchange codes
                    if (syncAccountRepository.parseSyncAuthCode(setupUrl) !is SyncAuthCode.Exchange) {
                        logcat { "Sync-setup: v1 deep-link URL does not carry an exchange code; ignoring silently" }
                        return@launch
                    }
                    command.send(Command.AskSetupSyncDeepLink(parsed))
                }
                SyncBarcodeUrl.ProtocolVersion.V2 -> {
                    // V2 exchanges have their own user confirmation built into the protocol
                    // (the Joiner/Host confirmation surfaced by the runner)
                    logcat { "Sync-setup: v2 deep link; bypassing legacy confirmation dialog" }
                    requiresSetupAuthentication {
                        command.send(Command.DeepLinkIntoSetup(parsed, syncAccountRepository.isSignedIn()))
                    }
                }
            }
        }
    }

    fun onUserAgreedToDeepLinkIntoSync(barcodeSyncUrl: SyncBarcodeUrl) {
        viewModelScope.launch(dispatchers.io()) {
            requiresSetupAuthentication {
                command.send(Command.DeepLinkIntoSetup(barcodeSyncUrl, syncAccountRepository.isSignedIn()))
            }
        }
    }

    companion object {
        private const val SETTINGS_REFRESH_RATE_MS = 5_000L
        private const val LEARN_MORE_URL =
            "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/recovery-codes-and-troubleshooting#data-expiration"
    }
}
