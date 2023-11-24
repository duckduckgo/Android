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

import android.app.Activity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.*
import com.duckduckgo.di.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.ActivitySyncBinding
import com.duckduckgo.sync.impl.databinding.DialogEditDeviceBinding
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowRecoveryCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.SyncWithAnotherDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowTextCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.SyncIntroCompleted
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.ConnectFlowContract
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract
import com.duckduckgo.sync.impl.ui.setup.LoginContract
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SETUP_COMPLETE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SyncIntroContract
import com.google.android.material.snackbar.Snackbar
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.*

@InjectWith(ActivityScope::class, delayGeneration = true)
@ContributeToActivityStarter(SyncActivityWithEmptyParams::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncBinding by viewBinding()
    private val viewModel: SyncActivityViewModel by bindViewModel()

    private val syncedDevicesAdapter = SyncedDevicesAdapter(
        object : ConnectedDeviceClickListener {
            override fun onEditDeviceClicked(device: ConnectedDevice) {
                viewModel.onEditDeviceClicked(device)
            }

            override fun onRemoveDeviceClicked(device: ConnectedDevice) {
                viewModel.onRemoveDeviceClicked(device)
            }
        },
    )

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var storagePermission: PermissionRequest

    @Inject
    lateinit var shareAction: ShareAction

    @Inject
    lateinit var syncSettingsPlugin: DaggerMap<Int, SyncSettingsPlugin>

    @Inject
    lateinit var syncFeatureMessagesPlugin: DaggerSet<SyncMessagePlugin>

    private val syncIntroLauncher = registerForActivityResult(
        SyncIntroContract(),
    ) { resultOk ->
        if (resultOk) {
            viewModel.onDeviceConnected()
        } else {
            viewModel.onConnectionCancelled()
        }
    }

    private val connectFlow = registerForActivityResult(ConnectFlowContract()) { resultOk ->
        if (resultOk) {
            viewModel.onLoginSuccess()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        registerForPermission()
        configureSettings()
        configureMessageWarnings()

        setupClickListeners()
        setupRecyclerView()
    }

    private fun registerForPermission() {
        storagePermission.registerResultsCallback(this) {
            binding.root.makeSnackbarWithNoBottomInset(R.string.sync_permission_required_store_recovery_code, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun configureSettings() {
        if (syncSettingsPlugin.isEmpty()) {
            Timber.i("configureSettings: plugins empty")
        } else {
            syncSettingsPlugin.keys.toSortedSet().forEach {
                syncSettingsPlugin[it]?.let { plugin ->
                    binding.viewSyncEnabled.syncSettingsOptions.addView(plugin.getView(this))
                }
            }
        }
    }

    private fun configureMessageWarnings() {
        if (syncFeatureMessagesPlugin.isEmpty()) {
            binding.viewSyncEnabled.syncFeatureWarningsContainer.isVisible = false
        } else {
            syncFeatureMessagesPlugin.forEach { plugin ->
                plugin.getView(this)?.let { view ->
                    binding.viewSyncEnabled.syncFeatureWarningsContainer.addView(view)
                }
                binding.viewSyncEnabled.syncFeatureWarningsContainer.isVisible = true
            }
        }
    }

    private fun setupClickListeners() {
        binding.viewSyncDisabled.syncSetupWithAnotherDevice.setClickListener {
            viewModel.onSyncWithAnotherDevice()
        }

        binding.viewSyncDisabled.syncSetupSyncThisDevice.setClickListener {
            viewModel.onSyncThisDevice()
        }

        binding.viewSyncDisabled.syncSetupRecoverData.setClickListener {
            viewModel.onRecoverYourSyncedData()
        }

        binding.viewSyncEnabled.disableSyncButton.setClickListener {
            viewModel.onTurnOffClicked()
        }

        binding.viewSyncEnabled.saveRecoveryCodeItem.setOnClickListener {
            viewModel.onSaveRecoveryCodeClicked()
        }

        binding.viewSyncEnabled.deleteAccountButton.setOnClickListener {
            viewModel.onDeleteAccountClicked()
        }

        binding.viewSyncEnabled.scanQrCodeItem.setOnClickListener {
            viewModel.onSyncWithAnotherDevice()
        }

        binding.viewSyncEnabled.showTextCodeItem.setOnClickListener {
            viewModel.onShowTextCodeClicked()
        }
    }

    private fun setupRecyclerView() {
        with(binding.viewSyncEnabled.syncedDevicesRecyclerView) {
            adapter = syncedDevicesAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
    }

    private fun observeUiEvents() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands().flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).onEach { processCommand(it) }.launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is SyncWithAnotherDevice -> connectFlow.launch(null)
            is IntroCreateAccount -> syncIntroLauncher.launch(SYNC_INTRO)
            is IntroRecoverSyncData -> syncIntroLauncher.launch(RECOVERY_INTRO)
            is ShowRecoveryCode -> syncIntroLauncher.launch(RECOVERY_CODE)
            is SyncIntroCompleted -> syncIntroLauncher.launch(SETUP_COMPLETE)
            is AskTurnOffSync -> askTurnOffsync(it.device)
            is AskDeleteAccount -> askDeleteAccount()
            is RecoveryCodePDFSuccess -> {
                shareAction.shareFile(this@SyncActivity, it.recoveryCodePDFFile)
            }

            is CheckIfUserHasStoragePermission -> {
                storagePermission.invokeOrRequestPermission {
                    viewModel.generateRecoveryCode(this@SyncActivity)
                }
            }

            is AskRemoveDevice -> askRemoveDevice(it.device)
            is AskEditDevice -> askEditDevice(it.device)
            is ShowTextCode -> startActivity(ShowCodeActivity.intent(this))
            else -> {}
        }
    }

    private fun askEditDevice(device: ConnectedDevice) {
        val inputBinding = DialogEditDeviceBinding.inflate(layoutInflater)
        inputBinding.customDialogTextInput.text = device.deviceName
        CustomAlertDialogBuilder(this)
            .setTitle(R.string.edit_device_dialog_title)
            .setPositiveButton(R.string.edit_device_dialog_primary_button)
            .setNegativeButton(R.string.edit_device_dialog_secondary_button)
            .setView(inputBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        val newText = inputBinding.customDialogTextInput.text
                        viewModel.onDeviceEdited(device.copy(deviceName = newText))
                    }
                },
            )
            .show()
    }

    private fun askRemoveDevice(device: ConnectedDevice) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.remove_device_dialog_title)
            .setMessage(getString(R.string.remove_device_dialog_content, device.deviceName))
            .setPositiveButton(R.string.remove_device_dialog_primary_button)
            .setNegativeButton(R.string.remove_device_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onRemoveDeviceConfirmed(device)
                    }
                },
            ).show()
    }

    private fun askDeleteAccount() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .setDestructiveButtons(true)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onDeleteAccountConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onDeleteAccountCancelled()
                    }
                },
            ).show()
    }

    private fun askTurnOffsync(device: ConnectedDevice) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.turn_off_sync_dialog_title)
            .setMessage(getString(R.string.turn_off_sync_dialog_content))
            .setPositiveButton(R.string.turn_off_sync_dialog_primary_button)
            .setNegativeButton(R.string.turn_off_sync_dialog_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onTurnOffSyncConfirmed(device)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onTurnOffSyncCancelled()
                    }
                },
            ).show()
    }

    private fun renderViewState(viewState: ViewState) {
        binding.viewSwitcher.displayedChild = if (viewState.showAccount) 1 else 0

        if (viewState.showAccount) {
            if (viewState.loginQRCode != null) {
                binding.viewSyncEnabled.qrCodeImageView.show()
                binding.viewSyncEnabled.qrCodeImageView.setImageBitmap(viewState.loginQRCode)
            }
        }

        syncedDevicesAdapter.updateData(viewState.syncedDevices)
    }
}
