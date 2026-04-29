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

package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.sync.api.favicons.FaviconsFetchingStore
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.autorestore.SyncRecoveryPersistentStorageKey
import com.duckduckgo.sync.impl.getOrNull
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ReadQR
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInternalSettingsViewModel.Command.ShowQR
import com.duckduckgo.sync.store.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SyncInternalSettingsViewModel
@Inject
constructor(
    private val syncAccountRepository: SyncAccountRepository,
    private val syncStore: SyncStore,
    private val syncEnvDataStore: SyncInternalEnvDataStore,
    private val syncFaviconFetchingStore: FaviconsFetchingStore,
    private val dispatchers: DispatcherProvider,
    private val syncPromotionDataStore: SyncPromotionDataStore,
    private val persistentStorage: PersistentStorage,
    private val syncAutoRestoreManager: SyncAutoRestoreManager,
    private val syncFeature: SyncFeature,
    private val appBuildConfig: AppBuildConfig,
    @field:SuppressLint("StaticFieldLeak") private val context: Context,
) : ViewModel() {

    private val command = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    private val viewState = MutableStateFlow(ViewState())
    fun viewState(): Flow<ViewState> = viewState.onStart { getConnectedDevices() }
    fun commands(): Flow<Command> = command.receiveAsFlow()

    data class ViewState(
        val userId: String = "",
        val deviceName: String = "",
        val deviceId: String = "",
        val token: String = "",
        val isSignedIn: Boolean = false,
        val primaryKey: String = "",
        val secretKey: String = "",
        val protectedEncryptionKey: String = "",
        val passwordHash: String = "",
        val connectedDevices: List<ConnectedDevice> = emptyList(),
        val useDevEnvironment: Boolean = false,
        val environment: String = "",
        val recoveryCode: String = "",
        val syncAutoRestoreEnabled: Boolean = false,
        val blockStoreAvailable: Boolean? = null,
        val blockStoreE2ESupported: Boolean? = null,
        val blockStoreCurrentValue: BlockStoreValue = BlockStoreValue.Loading,
        val blockStoreFeatureFlagText: String = "",
        val blockStoreAvailabilityText: String = "",
        val blockStoreApiLevelText: String = "",
        val blockStoreDeviceLockText: String = "",
        val blockStoreE2eText: String = "",
        val blockStoreInferredBackupText: String = "",
        val blockStoreCurrentValueText: String = "Loading...",
    )

    sealed class BlockStoreValue {
        data object Loading : BlockStoreValue()
        data object NotSet : BlockStoreValue()
        data class HasValue(val value: String) : BlockStoreValue()
    }

    sealed class Command {
        data class ShowMessage(val message: String) : Command()
        data object ReadQR : Command()
        data object ReadConnectQR : Command()
        data class ShowQR(val string: String) : Command()
        data object LoginSuccess : Command()
        data object LaunchRecoverDataScreen : Command()
    }

    init {
        viewModelScope.launch(dispatchers.io()) {
            updateViewState()
            checkSyncAutoRestoreFlag()
            checkBlockStoreAvailability()
            refreshBlockStoreValue()
        }
    }

    fun onResume() {
        viewModelScope.launch(dispatchers.io()) {
            checkSyncAutoRestoreFlag()
            checkBlockStoreAvailability()
            refreshBlockStoreValue()
        }
    }

    fun onLaunchRecoverDataScreen() {
        viewModelScope.launch(dispatchers.io()) {
            if (syncAccountRepository.isSignedIn()) {
                command.send(Command.LaunchRecoverDataScreen)
            } else {
                command.send(Command.ShowMessage("Not signed in — create an account first"))
            }
        }
    }

    fun onCreateAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.createAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
            startInitialSync()
        }
    }

    fun onResetClicked() {
        viewModelScope.launch(dispatchers.io()) {
            syncStore.clearAll()
            updateViewState()
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val currentDeviceId = syncAccountRepository.getAccountInfo().deviceId
            val result = syncAccountRepository.logout(currentDeviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onDeviceLogoutClicked(deviceId: String) {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.logout(deviceId)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            getConnectedDevices()
        }
    }

    fun onEnvironmentChanged(devEnvironment: Boolean) {
        viewModelScope.launch(dispatchers.io()) {
            syncEnvDataStore.useSyncDevEnvironment = devEnvironment
            updateViewState()
        }
    }

    fun onDeleteAccountClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val result = syncAccountRepository.deleteAccount()
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    private fun getConnectedDevices() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
                is Error -> command.send(Command.ShowMessage(connectedDevices.reason))
                is Success -> {
                    viewState.emit(
                        viewState.value.copy(
                            connectedDevices = connectedDevices.data,
                        ),
                    )
                }
            }
            updateViewState()
        }
    }

    private fun startInitialSync() {
        viewModelScope.launch(dispatchers.io()) {
            when (val connectedDevices = syncAccountRepository.getConnectedDevices()) {
                is Error -> command.send(Command.ShowMessage(connectedDevices.reason))
                is Success -> {
                    viewState.emit(
                        viewState.value.copy(
                            connectedDevices = connectedDevices.data,
                        ),
                    )
                }
            }
            updateViewState()
        }
    }

    private suspend fun updateViewState() {
        val accountInfo = syncAccountRepository.getAccountInfo()
        val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull()?.rawCode ?: ""
        viewState.emit(
            viewState.value.copy(
                userId = accountInfo.userId,
                deviceName = accountInfo.deviceName,
                deviceId = accountInfo.deviceId,
                isSignedIn = accountInfo.isSignedIn,
                token = syncAccountRepository.latestToken(),
                primaryKey = accountInfo.primaryKey,
                secretKey = accountInfo.secretKey,
                recoveryCode = recoveryCode,
                useDevEnvironment = syncEnvDataStore.useSyncDevEnvironment,
                environment = syncEnvDataStore.syncEnvironmentUrl,
            ),
        )
    }

    fun onReadQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            command.send(ReadQR)
        }
    }

    fun onShowQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull() ?: return@launch
            command.send(ShowQR(recoveryCode.qrCode))
        }
    }

    fun onQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val codeType = syncAccountRepository.parseSyncAuthCode(contents)
            val result = syncAccountRepository.processCode(codeType)
            if (result is Error) {
                command.send(Command.ShowMessage("$result"))
            }
            updateViewState()
        }
    }

    fun onConnectQRScanned(contents: String) {
        viewModelScope.launch(dispatchers.io()) {
            val codeType = syncAccountRepository.parseSyncAuthCode(contents)
            val result = syncAccountRepository.processCode(codeType)
            when (result) {
                is Error -> {
                    command.send(Command.ShowMessage("$result"))
                }

                is Success -> {
                    command.send(Command.ShowMessage("${result.data}"))
                    updateViewState()
                }
            }
        }
    }

    fun onConnectStart() {
        viewModelScope.launch(dispatchers.io()) {
            val qrCode = when (val qrCodeResult = syncAccountRepository.getConnectQR()) {
                is Error -> {
                    command.send(ShowMessage("$qrCodeResult"))
                    return@launch
                }

                is Success -> {
                    qrCodeResult.data.qrCode
                }
            }
            updateViewState()
            command.send(ShowQR(qrCode))
            var polling = true
            while (polling) {
                delay(7000)
                when (val result = syncAccountRepository.pollConnectionKeys()) {
                    is Error -> {
                        command.send(Command.ShowMessage("$result"))
                    }

                    is Success -> {
                        command.send(Command.ShowMessage(result.data.toString()))
                        polling = false
                        updateViewState()
                    }
                }
            }
        }
    }

    fun onReadConnectQRClicked() {
        viewModelScope.launch(dispatchers.io()) {
            viewModelScope.launch(dispatchers.io()) {
                command.send(ReadConnectQR)
            }
        }
    }

    fun useRecoveryCode(recoveryCode: String) {
        viewModelScope.launch(dispatchers.io()) {
            authFlow(recoveryCode)
        }
    }

    fun resetFaviconsPrompt() {
        logcat { "Sync-Internal: Reset Favicons Prompt" }
        syncFaviconFetchingStore.isFaviconsFetchingEnabled = false
        syncFaviconFetchingStore.promptShown = false
    }

    private suspend fun authFlow(
        pastedCode: String,
    ) {
        val codeType = syncAccountRepository.parseSyncAuthCode(pastedCode)
        val result = syncAccountRepository.processCode(codeType)
        when (result) {
            is Result.Success -> command.send(Command.LoginSuccess)
            is Result.Error -> {
                command.send(ShowMessage("Something went wrong"))
            }
        }
    }

    fun onClearHistoryBookmarkAddedDialogPromoClicked() {
        viewModelScope.launch {
            syncPromotionDataStore.clearPromoHistory(BookmarkAddedDialog)
            command.send(ShowMessage("'Bookmark added' promo history cleared"))
        }
    }

    fun onClearHistoryBookmarkScreenPromoClicked() {
        viewModelScope.launch {
            syncPromotionDataStore.clearPromoHistory(BookmarksScreen)
            command.send(ShowMessage("'Bookmark screen' promo history cleared"))
        }
    }

    fun onClearHistoryPasswordScreenPromoClicked() {
        viewModelScope.launch {
            syncPromotionDataStore.clearPromoHistory(PasswordsScreen)
            command.send(ShowMessage("'Password screen' promo history cleared"))
        }
    }

    private fun checkSyncAutoRestoreFlag() {
        val enabled = syncFeature.syncAutoRestore().isEnabled()
        viewState.update { state ->
            state.copy(syncAutoRestoreEnabled = enabled).withBlockStoreChecklistText(
                apiLevel = appBuildConfig.sdkInt,
                hasSecureLock = isDeviceSecureLockEnabled(),
            )
        }
    }

    private suspend fun checkBlockStoreAvailability() {
        when (val availability = persistentStorage.checkAvailability()) {
            is PersistentStorageAvailability.Unavailable -> {
                viewState.update { state ->
                    state.copy(
                        blockStoreAvailable = false,
                        blockStoreE2ESupported = false,
                    ).withBlockStoreChecklistText(
                        apiLevel = appBuildConfig.sdkInt,
                        hasSecureLock = isDeviceSecureLockEnabled(),
                    )
                }
            }
            is PersistentStorageAvailability.Available -> {
                viewState.update { state ->
                    state.copy(
                        blockStoreAvailable = true,
                        blockStoreE2ESupported = availability.isEndToEndEncryptionSupported,
                    ).withBlockStoreChecklistText(
                        apiLevel = appBuildConfig.sdkInt,
                        hasSecureLock = isDeviceSecureLockEnabled(),
                    )
                }
            }
        }
    }

    private suspend fun refreshBlockStoreValue() {
        val bytes = persistentStorage.retrieve(SyncRecoveryPersistentStorageKey).getOrNull()
        val blockStoreValue = when {
            bytes == null -> BlockStoreValue.NotSet
            else -> BlockStoreValue.HasValue(String(bytes, Charsets.UTF_8))
        }
        viewState.update { state ->
            state.copy(blockStoreCurrentValue = blockStoreValue).withBlockStoreChecklistText(
                apiLevel = appBuildConfig.sdkInt,
                hasSecureLock = isDeviceSecureLockEnabled(),
            )
        }
    }

    fun onBlockStoreWriteRecoveryCode() {
        viewModelScope.launch(dispatchers.io()) {
            if (!syncAutoRestoreManager.isAutoRestoreAvailable()) {
                command.send(ShowMessage("Block Store not available on this device"))
                return@launch
            }
            val recoveryCode = syncAccountRepository.getRecoveryCode().getOrNull()
            if (recoveryCode == null) {
                command.send(ShowMessage("No recovery code available"))
                return@launch
            }
            val deviceId = syncAccountRepository.getAccountInfo().deviceId
            val success = syncAutoRestoreManager.saveAutoRestoreData(recoveryCode.rawCode, deviceId)
            refreshBlockStoreValue()
            if (success) {
                command.send(ShowMessage("Recovery code stored successfully"))
            } else {
                command.send(ShowMessage("Store failed — unexpected error"))
            }
        }
    }

    fun onBlockStoreWriteClicked(recoveryCode: String, deviceId: String?) {
        viewModelScope.launch(dispatchers.io()) {
            if (!syncAutoRestoreManager.isAutoRestoreAvailable()) {
                command.send(ShowMessage("Block Store not available on this device"))
                return@launch
            }
            if (recoveryCode.isBlank()) {
                command.send(ShowMessage("Recovery code is required"))
                return@launch
            }
            val trimmedDeviceId = deviceId?.takeIf { it.isNotBlank() }
            if (trimmedDeviceId != null && trimmedDeviceId.length !in 8..64) {
                command.send(ShowMessage("Device ID must be 8–64 chars (or leave blank)"))
                return@launch
            }
            val success = syncAutoRestoreManager.saveAutoRestoreData(recoveryCode, deviceId?.takeIf { it.isNotBlank() })
            refreshBlockStoreValue()
            if (success) {
                command.send(ShowMessage("Stored successfully"))
            } else {
                command.send(ShowMessage("Store failed — unexpected error"))
            }
        }
    }

    fun onBlockStoreClearClicked() {
        viewModelScope.launch(dispatchers.io()) {
            if (!syncAutoRestoreManager.isAutoRestoreAvailable()) {
                command.send(ShowMessage("Block Store not available on this device"))
                return@launch
            }
            syncAutoRestoreManager.clearAutoRestoreData()
            refreshBlockStoreValue()
            command.send(ShowMessage("Cleared successfully"))
        }
    }

    private fun ViewState.withBlockStoreChecklistText(
        apiLevel: Int,
        hasSecureLock: Boolean,
    ): ViewState {
        val apiSupportsE2e = apiLevel >= 28
        val featureFlagText = if (syncAutoRestoreEnabled) {
            "✅ syncAutoRestore flag: enabled"
        } else {
            "❌ syncAutoRestore flag: disabled"
        }
        val availabilityText = when (blockStoreAvailable) {
            null -> "Checking..."
            true -> "✅ Block Store API: Available"
            false -> "❌ Block Store API: Unavailable (Play Services missing)"
        }
        val apiLevelText = if (blockStoreAvailable == null) {
            ""
        } else {
            val e2eApiLevelStatus = if (apiSupportsE2e) {
                "(supports E2E encryption)"
            } else {
                "(no E2E encryption support)"
            }
            "Android API level: $apiLevel $e2eApiLevelStatus"
        }
        val deviceLockText = when (blockStoreAvailable) {
            null -> ""
            true -> if (hasSecureLock) "✅ Device screen lock: Enabled" else "❌ Device screen lock: Disabled"
            false -> ""
        }
        val e2eText = when {
            blockStoreAvailable == null -> ""
            !blockStoreAvailable -> ""
            blockStoreE2ESupported == true -> "✅ E2E encryption: Available"
            else -> "❌ E2E encryption: Unavailable"
        }
        val inferredBackupText = when {
            blockStoreAvailable == null -> ""
            blockStoreE2ESupported == true -> "✅ Backup setting (inferred): Enabled"
            blockStoreAvailable && apiSupportsE2e && hasSecureLock ->
                "❌ Backup setting (inferred): Disabled\n(E2E unavailable despite meeting prerequisites)"
            else -> "❓ Backup setting (inferred): Unknown"
        }
        val currentValueText = when (val value = blockStoreCurrentValue) {
            is BlockStoreValue.Loading -> "Loading..."
            is BlockStoreValue.NotSet -> "(key not set)"
            is BlockStoreValue.HasValue -> value.value
        }
        return copy(
            blockStoreFeatureFlagText = featureFlagText,
            blockStoreAvailabilityText = availabilityText,
            blockStoreApiLevelText = apiLevelText,
            blockStoreDeviceLockText = deviceLockText,
            blockStoreE2eText = e2eText,
            blockStoreInferredBackupText = inferredBackupText,
            blockStoreCurrentValueText = currentValueText,
        )
    }

    private fun isDeviceSecureLockEnabled(): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return keyguardManager?.isDeviceSecure == true
    }
}
