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

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.*
import com.duckduckgo.di.scopes.*
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.sync.api.*
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.PermissionRequest
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.sync.impl.databinding.ActivitySyncBinding
import com.duckduckgo.sync.impl.databinding.DialogEditDeviceBinding
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsParams
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AddAnotherDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowDeviceUnsupported
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowRecoveryCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.SyncWithAnotherDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.ConnectFlowContract
import com.duckduckgo.sync.impl.ui.setup.ConnectFlowContractInput
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SyncIntroContract
import com.duckduckgo.sync.impl.ui.setup.SyncIntroContractInput
import com.duckduckgo.sync.impl.ui.setup.SyncWithAnotherDeviceContract
import com.duckduckgo.sync.impl.ui.setup.SyncWithAnotherDeviceContract.SyncWithAnotherDeviceContractOutput.DeviceConnected
import com.duckduckgo.sync.impl.ui.setup.SyncWithAnotherDeviceContract.SyncWithAnotherDeviceContractOutput.SwitchAccountSuccess
import com.google.android.material.snackbar.Snackbar
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(ActivityScope::class, delayGeneration = true)
@ContributeToActivityStarter(SyncActivityWithEmptyParams::class)
@ContributeToActivityStarter(SyncActivityWithSourceParams::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding: ActivitySyncBinding by viewBinding()
    private val viewModel: SyncActivityViewModel by bindViewModel()

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

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

    private val syncWithAnotherDeviceFlow = registerForActivityResult(SyncWithAnotherDeviceContract()) { result ->
        when (result) {
            DeviceConnected -> viewModel.onDeviceConnected()
            SwitchAccountSuccess -> viewModel.onLoginSuccess()
            else -> {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)

        binding.viewSyncDisabled.otherOptionsHeader.setText(R.string.sync_setup_other_options_title)

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

        binding.viewSyncEnabled.syncAnotherDeviceItem.setOnClickListener {
            viewModel.onAddAnotherDevice()
        }

        binding.viewSyncEnabled.syncSetupOtherPlatforms.setOnClickListener {
            viewModel.onGetOnOtherPlatformsClickedWhenSyncEnabled()
        }

        binding.viewSyncDisabled.syncSetupOtherPlatforms.setOnClickListener {
            viewModel.onGetOnOtherPlatformsClickedWhenSyncDisabled()
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
            is SyncWithAnotherDevice -> {
                authenticate {
                    connectFlow.launch(ConnectFlowContractInput(extractSource()))
                }
            }

            is IntroCreateAccount -> {
                authenticate {
                    syncIntroLauncher.launch(SyncIntroContractInput(SYNC_INTRO, extractSource()))
                }
            }

            is IntroRecoverSyncData -> {
                authenticate {
                    syncIntroLauncher.launch(SyncIntroContractInput(RECOVERY_INTRO, extractSource()))
                }
            }

            is ShowRecoveryCode -> {
                authenticate {
                    syncIntroLauncher.launch(SyncIntroContractInput(RECOVERY_CODE, extractSource()))
                }
            }

            is AskTurnOffSync -> askTurnOffSync(it.device)
            is AskDeleteAccount -> askDeleteAccount()
            is RecoveryCodePDFSuccess -> {
                authenticate {
                    shareAction.shareFile(this@SyncActivity, it.recoveryCodePDFFile)
                }
            }

            is CheckIfUserHasStoragePermission -> {
                storagePermission.invokeOrRequestPermission {
                    viewModel.generateRecoveryCode(this@SyncActivity)
                }
            }

            is AskRemoveDevice -> askRemoveDevice(it.device)
            is AskEditDevice -> askEditDevice(it.device)
            is AddAnotherDevice -> {
                authenticate {
                    syncWithAnotherDeviceFlow.launch(null)
                }
            }
            is ShowError -> showError(it)
            is ShowDeviceUnsupported -> {
                startActivity(DeviceUnsupportedActivity.intent(this))
                finish()
            }
            is RequestSetupAuthentication -> launchDeviceAuthEnrollment()
            is LaunchSyncGetOnOtherPlatforms -> launchSyncGetOnOtherPlatforms(it.source)
        }
    }

    private fun launchSyncGetOnOtherPlatforms(source: String) {
        globalActivityStarter.start(this, SyncGetOnOtherPlatformsParams(source))
    }

    private fun showError(it: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(it.message) + "\n" + it.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .show()
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
            .setTitle(R.string.sync_delete_server_data_dialog_title)
            .setMessage(getString(R.string.sync_delete_server_data_dialog_content))
            .setPositiveButton(R.string.sync_delete_server_data_dialog_primary_button, DESTRUCTIVE)
            .setNegativeButton(R.string.sync_delete_server_data_dialog_secondary_button, GHOST_ALT)
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

    private fun askTurnOffSync(device: ConnectedDevice) {
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

    private fun launchDeviceAuthEnrollment() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_require_device_passcode_dialog_title)
            .setMessage(getString(R.string.sync_require_device_passcode_dialog_body))
            .setPositiveButton(R.string.sync_require_device_passcode_dialog_action)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        deviceAuthenticator.launchDeviceAuthEnrollment(this@SyncActivity)
                    }
                },
            )
            .setCancellable(true)
            .show()
    }

    private fun renderViewState(viewState: ViewState) {
        binding.viewSwitcher.displayedChild = if (viewState.showAccount) 1 else 0

        if (viewState.showAccount) {
            syncedDevicesAdapter.updateData(viewState.syncedDevices)
        } else {
            with(binding.viewSyncDisabled) {
                syncSetupWithAnotherDevice.isEnabled = !viewState.disabledSetupFlows.contains(SetupFlows.CreateAccountFlow)
                syncSetupSyncThisDevice.isEnabled = !viewState.disabledSetupFlows.contains(SetupFlows.CreateAccountFlow)
                syncSetupRecoverData.isEnabled = !viewState.disabledSetupFlows.contains(SetupFlows.SignInFlow)
            }
        }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        if (deviceAuthenticator.hasValidDeviceAuthentication()) {
            deviceAuthenticator.authenticate(config = AuthConfiguration(), fragmentActivity = this) {
                when (it) {
                    Success -> onSuccess()
                    else -> { }
                }
            }
        } else {
            onSuccess()
        }
    }

    private fun extractSource(): String? {
        return intent.getActivityParams(SyncActivityWithSourceParams::class.java)?.source
    }
}

data class SyncActivityWithSourceParams(val source: String?) : GlobalActivityStarter.ActivityParams
