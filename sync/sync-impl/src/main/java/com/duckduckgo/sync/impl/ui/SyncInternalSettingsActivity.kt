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

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.hide
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.view.text.TextInput.Action
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.sync.impl.databinding.ActivityInternalSyncSettingsBinding
import com.duckduckgo.sync.impl.databinding.ItemConnectedDeviceBinding
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ReadConnectQR
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ReadQR
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.Command.ShowQR
import com.duckduckgo.sync.impl.ui.SyncInitialSetupViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat.QR_CODE
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncInternalSettingsActivity : DuckDuckGoActivity() {
    private val binding: ActivityInternalSyncSettingsBinding by viewBinding()
    private val viewModel: SyncInitialSetupViewModel by bindViewModel()

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

    private fun configureListeners() {
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
            viewModel.copyRecoveryCode(binding.syncRecoveryCode.text)
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

    private fun processCommand(command: SyncInitialSetupViewModel.Command) {
        when (command) {
            is ShowMessage -> {
                Toast.makeText(this, command.message, Toast.LENGTH_LONG).show()
            }

            ReadQR -> {
                barcodeLauncher.launch(getScanOptions())
            }

            is ShowQR -> {
                try {
                    val barcodeEncoder = BarcodeEncoder()
                    val bitmap: Bitmap = barcodeEncoder.encodeBitmap(command.string, QR_CODE, 400, 400)
                    binding.qrCodeImageView.show()
                    binding.qrCodeImageView.setImageBitmap(bitmap)
                    binding.showQRCode.hide()
                } catch (e: Exception) {
                }
            }

            ReadConnectQR -> {
                barcodeConnectLauncher.launch(getScanOptions())
            }
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
        binding.connectedDevicesList.removeAllViews()

        binding.syncInternalEnvironment.quietlySetIsChecked(viewState.useDevEnvironment) { _, enabled ->
            viewModel.onEnvironmentChanged(enabled)
        }
        binding.syncInternalEnvironment.setSecondaryText(viewState.environment)
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
