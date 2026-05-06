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

package com.duckduckgo.sync.internal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.ui.SyncActivity
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity
import com.duckduckgo.sync.internal.databinding.ActivityInternalSyncSettingsBinding
import com.duckduckgo.sync.internal.databinding.ItemConnectedDeviceBinding
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command.LoginSuccess
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command.ReadQR
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command.ShowMessage
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.Command.ShowQR
import com.duckduckgo.sync.internal.ui.SyncInternalSettingsViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat.QR_CODE
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class SyncInternalSettingsActivity : DuckDuckGoActivity() {
    private val binding: ActivityInternalSyncSettingsBinding by viewBinding()
    private val viewModel: SyncInternalSettingsViewModel by bindViewModel()

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var browserNav: BrowserNav

    private val barcodeLauncher = registerForActivityResult(
        ScanContract(),
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            viewModel.onQRScanned(result.contents)
        }
    }

    // Register the launcher and result handler
    private val barcodeConnectLauncher = registerForActivityResult(
        ScanContract(),
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            viewModel.onConnectQRScanned(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        configureListeners()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    private fun configureListeners() {
        binding.launchSyncSettingsButton.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
        }
        binding.openDuckAiDevScreenButton.setOnClickListener {
            // Launch via class name so sync-internal doesn't need a direct dependency on duckchat-internal.
            val intent = Intent().setClassName(packageName, "com.duckduckgo.duckchat.internal.DuckAiDevActivity")
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, "Duck.ai dev screen not available in this build", Toast.LENGTH_LONG).show()
            }
        }
        // this is temporary while testing
        binding.openDuckAiSetupUrlButton.setOnClickListener {
            startActivity(browserNav.openInNewTab(this, "https://euw-serp-dev-testing18.duck.ai/setup-aichat"))
        }
        binding.showQRCode.setOnClickListener {
            viewModel.onShowQRClicked()
        }
        binding.createAccountButton.setOnClickListener { viewModel.onCreateAccountClicked() }
        binding.readQRButton.setOnClickListener { viewModel.onReadQRClicked() }
        binding.resetButton.setOnClickListener { viewModel.onResetClicked() }
        binding.logoutButton.setOnClickListener { viewModel.onLogoutClicked() }
        binding.deleteAccountButton.setOnClickListener { viewModel.onDeleteAccountClicked() }
        binding.connectQRCode.setOnClickListener { viewModel.onConnectStart() }
        binding.readConnectQRCode.setOnClickListener { viewModel.onReadConnectQRClicked() }
        binding.syncRecoveryCodeCta.setOnClickListener {
            viewModel.useRecoveryCode(binding.syncRecoveryCode.text)
        }
        binding.syncFaviconsPromptCta.setOnClickListener {
            viewModel.resetFaviconsPrompt()
        }
        binding.clearHistoryBookmarkAddedDialogPromo.setOnClickListener { viewModel.onClearHistoryBookmarkAddedDialogPromoClicked() }
        binding.clearHistoryBookmarkScreenPromo.setOnClickListener { viewModel.onClearHistoryBookmarkScreenPromoClicked() }
        binding.clearHistoryPasswordScreenPromo.setOnClickListener { viewModel.onClearHistoryPasswordScreenPromoClicked() }
        binding.userIdTextView.setOnClickListener { copyToClipboard("User ID", binding.userIdTextView.text.toString()) }
        binding.deviceNameTextView.setOnClickListener { copyToClipboard("Device Name", binding.deviceNameTextView.text.toString()) }
        binding.deviceIdTextView.setOnClickListener { copyToClipboard("Device ID", binding.deviceIdTextView.text.toString()) }
        binding.secretKeyTextView.setOnClickListener { copyToClipboard("Secret Key", binding.secretKeyTextView.text.toString()) }
        binding.tokenTextView.setOnClickListener { copyToClipboard("Token", binding.tokenTextView.text.toString()) }
        binding.launchRecoverDataScreenButton.setOnClickListener {
            viewModel.onLaunchRecoverDataScreen()
        }
        binding.blockStoreWriteButton.setOnClickListener {
            viewModel.onBlockStoreWriteClicked(
                recoveryCode = binding.blockStoreRecoveryCodeInput.text,
                deviceId = binding.blockStoreDeviceIdInput.text.takeIf { it.isNotBlank() },
            )
        }
        binding.blockStoreClearButton.setOnClickListener { viewModel.onBlockStoreClearClicked() }
        binding.blockStoreWriteRecoveryCodeButton.setOnClickListener { viewModel.onBlockStoreWriteRecoveryCode() }
        binding.recoveryCodeTextView.setOnClickListener { copyToClipboard("Recovery Code", binding.recoveryCodeTextView.text.toString()) }
        binding.recoveryCodeDecodedTextView.setOnClickListener {
            copyToClipboard("Recovery Code (decoded JSON)", binding.recoveryCodeDecodedTextView.text.toString())
        }
        binding.fetchAccessCredentialsButton.setOnClickListener { viewModel.onFetchAccessCredentialsClicked() }
        binding.requestScopedTokenButton.setOnClickListener { viewModel.onRequestScopedTokenClicked() }
        binding.fetchKeysButton.setOnClickListener { viewModel.onFetchKeysClicked() }
        binding.createProtectedKeyButton.setOnClickListener {
            viewModel.onCreateProtectedKeyClicked(binding.createProtectedKeyPurposeInput.text)
        }
        binding.createThirdPartyCredentialButton.setOnClickListener { viewModel.onCreateThirdPartyCredentialClicked() }
        binding.refreshThirdPartyCredentialButton.setOnClickListener { viewModel.onRefreshThirdPartyCredentialClicked() }
        binding.showThirdPartyRecoveryQrButton.setOnClickListener { viewModel.onShowThirdPartyRecoveryQrClicked() }
        binding.thirdPartyRecoveryCodeTextView.setOnClickListener {
            copyToClipboard("3party Recovery Code", binding.thirdPartyRecoveryCodeTextView.text.toString())
        }
        binding.copyThirdPartyRecoveryCodeButton.setOnClickListener {
            copyToClipboard("3party Recovery Code", binding.thirdPartyRecoveryCodeTextView.text.toString())
        }
        binding.thirdPartyRecoveryCodeDecodedTextView.setOnClickListener {
            copyToClipboard("3party Recovery Code (decoded JSON)", binding.thirdPartyRecoveryCodeDecodedTextView.text.toString())
        }
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is ShowMessage -> {
                Toast.makeText(this, command.message, Toast.LENGTH_LONG).show()
            }

            ReadQR -> {
                barcodeLauncher.launch(getScanOptions())
            }

            is ShowQR -> {
                lifecycleScope.launch {
                    val bitmap = encodeQrAsync(command.string) ?: return@launch
                    binding.qrCodeImageView.show()
                    binding.qrCodeImageView.setImageBitmap(bitmap)
                    binding.showQRCode.hide()
                }
            }

            is Command.ShowThirdPartyRecoveryQR -> {
                lifecycleScope.launch {
                    val bitmap = encodeQrAsync(command.string) ?: return@launch
                    binding.thirdPartyRecoveryQrImageView.show()
                    binding.thirdPartyRecoveryQrImageView.setImageBitmap(bitmap)
                }
            }

            ReadConnectQR -> {
                barcodeConnectLauncher.launch(getScanOptions())
            }

            LoginSuccess -> {
                Snackbar.make(binding.syncRecoveryCodeCta, "Login Success", Snackbar.LENGTH_SHORT).show()
            }

            Command.LaunchRecoverDataScreen -> {
                startActivity(
                    Intent(this, SetupAccountActivity::class.java).apply {
                        putExtra(SetupAccountActivity.SETUP_ACCOUNT_SCREEN_EXTRA, SetupAccountActivity.Companion.Screen.RECOVERY_CODE)
                    },
                )
            }
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun decodeThirdPartyRecoveryCode(b64UrlEncoded: String): String {
        if (b64UrlEncoded.isEmpty()) return "(no 3party credential yet)"
        return runCatching {
            String(Base64.decode(b64UrlEncoded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrElse { "(decode failed: ${it.message})" }
    }

    private fun decodeStandardBase64(encoded: String, emptyPlaceholder: String): String {
        if (encoded.isEmpty()) return emptyPlaceholder
        return runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrElse { "(decode failed: ${it.message})" }
    }

    private suspend fun encodeQrAsync(contents: String): Bitmap? = withContext(dispatcherProvider.io()) {
        runCatching {
            BarcodeEncoder().encodeBitmap(contents, QR_CODE, 400, 400)
        }.getOrElse { e ->
            logcat(LogPriority.ERROR) { "Sync-ScopedToken: failed to encode QR: ${e.message}" }
            null
        }
    }

    private fun getScanOptions(): ScanOptions {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a barcode")
        options.setCameraId(0) // Use a specific camera of the device
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        return options
    }

    private fun renderViewState(viewState: ViewState) {
        binding.uuidsViewGroup.isVisible = viewState.isSignedIn
        binding.accountStateTextView.isVisible = viewState.isSignedIn
        binding.userIdTextView.text = viewState.userId
        binding.deviceIdTextView.text = viewState.deviceId
        binding.tokenTextView.text = viewState.token
        binding.deviceNameTextView.text = viewState.deviceName
        binding.primaryKeyTextView.text = viewState.primaryKey
        binding.secretKeyTextView.text = viewState.secretKey
        binding.recoveryCodeTextView.text = viewState.recoveryCode.ifEmpty { "(not signed in)" }
        binding.recoveryCodeDecodedTextView.text = decodeStandardBase64(viewState.recoveryCode, emptyPlaceholder = "(not signed in)")
        binding.thirdPartyRecoveryCodeTextView.text = viewState.thirdPartyRecoveryCode.ifEmpty { "(no 3party credential yet)" }
        binding.thirdPartyRecoveryCodeDecodedTextView.text = decodeThirdPartyRecoveryCode(viewState.thirdPartyRecoveryCode)
        binding.connectedDevicesList.removeAllViews()
        binding.blockStoreFeatureFlag.text = viewState.blockStoreFeatureFlagText
        binding.blockStoreAvailability.text = viewState.blockStoreAvailabilityText
        binding.blockStoreApiInUse.text = viewState.blockStoreApiLevelText
        binding.blockStoreDeviceLockStatus.text = viewState.blockStoreDeviceLockText
        binding.blockStoreE2eStatus.text = viewState.blockStoreE2eText
        binding.blockStoreInferredBackupStatus.text = viewState.blockStoreInferredBackupText
        binding.blockStoreCurrentValue.text = viewState.blockStoreCurrentValueText

        binding.syncInternalEnvironment.quietlySetIsChecked(viewState.useDevEnvironment) { _, enabled ->
            viewModel.onEnvironmentChanged(enabled)
        }
        binding.syncInternalEnvironment.setSecondaryText(viewState.environment)

        binding.canUseV2ConnectFlowToggle.quietlySetIsChecked(viewState.canUseV2ConnectFlowEnabled) { _, enabled ->
            viewModel.onCanUseV2ConnectFlowFlagChanged(enabled)
        }
        binding.canShowV2ConnectCodeToggle.quietlySetIsChecked(viewState.canShowV2ConnectCodeEnabled) { _, enabled ->
            viewModel.onCanShowV2ConnectCodeFlagChanged(enabled)
        }
        binding.accessCredentialsTextView.text = viewState.accessCredentialsText
        binding.scopedTokenResultTextView.text = viewState.scopedTokenResult
        binding.v2StoreFieldsTextView.text = viewState.v2StoreFieldsText
        binding.keysTextView.text = viewState.keysText
        if (viewState.isSignedIn) {
            viewState.connectedDevices.forEach { device ->
                val connectedBinding = ItemConnectedDeviceBinding.inflate(layoutInflater, binding.connectedDevicesList, true)
                connectedBinding.deviceName.text = "${device.deviceName} ${if (device.thisDevice) "(This Device)" else ""}"
                connectedBinding.logoutButton.setOnClickListener {
                    viewModel.onDeviceLogoutClicked(device.deviceId)
                }
            }
        }
    }
}
