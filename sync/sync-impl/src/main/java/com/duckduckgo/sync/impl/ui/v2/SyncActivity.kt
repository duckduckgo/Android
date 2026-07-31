/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.v2

import android.Manifest
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.plusAssign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.common.ui.view.addClickableSpan
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.DaggerMap
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.settings.api.SettingsWebViewScreenWithParams
import com.duckduckgo.sync.api.SyncActivityWithAnotherDevice
import com.duckduckgo.sync.api.SyncMessagePlugin
import com.duckduckgo.sync.api.SyncSettingsPlugin
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthConfiguration
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.Error
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.Success
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.AuthResult.UserCancelled
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2Binding
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsParams
import com.duckduckgo.sync.impl.ui.DeviceUnsupportedActivity
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AddAnotherDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskDeleteAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskRemoveDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskSetupSyncDeepLink
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskToCopyRecoveryCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.DeepLinkIntoSetup
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchLearnMore
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchOriginalFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowDeviceUnsupported
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowPreviousSessionReady
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowRecoveryCode
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.SyncWithAnotherDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.OriginalFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.CreateAccountFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.SignInFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.SyncActivityWithSourceParams
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class SyncActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2Binding>()

    private val viewModel by bindViewModel<SyncActivityViewModel>()

    @Inject
    lateinit var syncSettingsPlugin: DaggerMap<Int, SyncSettingsPlugin>

    @Inject
    lateinit var syncMessagesPlugin: DaggerSet<SyncMessagePlugin>

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var deviceAuthenticator: DeviceAuthenticator

    @Inject
    lateinit var syncSetupWideEvent: SyncSetupWideEvent

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var shareAction: ShareAction

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val launchSource
        get() = intent.getActivityParams(SyncActivityWithSourceParams::class.java)?.source
            ?: intent.getActivityParams(SyncActivityWithAnotherDevice::class.java)?.source

    private val syncThisDeviceLauncher = registerForActivityResult(
        SyncThisDeviceContract(),
    ) { output ->
        when (output) {
            is SyncThisDeviceContract.Output.BackedUp -> {
                viewModel.onDeviceConnected()
                recoveryCodeLauncher.launch(RecoveryCodeContract.Input(output.device.deviceName))
            }

            is SyncThisDeviceContract.Output.SyncedWithAnotherDevice -> {
                handlePairingResult(output.result)
            }

            is SyncThisDeviceContract.Output.Dismissed -> {
                viewModel.onSyncThisDeviceCanceled()
                viewModel.onConnectionCancelled()
            }
        }
    }

    private val editDeviceLauncher = registerForActivityResult(
        EditDeviceContract(),
    ) { result ->
        when (result) {
            is EditDeviceContract.Output.DeviceEdited -> {
                viewModel.onDevicesUpdated()
            }

            is EditDeviceContract.Output.RemoveDeviceConfirmed -> {
                viewModel.onRemoveDeviceConfirmed(result.device)
            }

            is EditDeviceContract.Output.TurnOffSyncConfirmed -> {
                viewModel.onTurnOffSyncConfirmed(result.device)
            }

            is EditDeviceContract.Output.Dismissed -> Unit
        }
    }

    private val recoveryCodeLauncher = registerForActivityResult(
        RecoveryCodeContract(),
    ) { isSuccess ->
        if (!isSuccess) {
            viewModel.onSyncThisDeviceCanceled()
            viewModel.onConnectionCancelled()
        }
    }

    private val syncWithAnotherDeviceLauncher = registerForActivityResult(
        ReadSyncCodeContract(),
    ) { output ->
        when (output) {
            is ReadSyncCodeContract.Output.SyncCompleted -> {
                handlePairingResult(output.result)
            }

            is ReadSyncCodeContract.Output.Dismissed -> Unit
        }
    }

    private val addAnotherDeviceLauncher = registerForActivityResult(
        ReadSyncCodeContract(),
    ) { output ->
        when (output) {
            is ReadSyncCodeContract.Output.SyncCompleted -> {
                handlePairingResult(output.result)
            }

            is ReadSyncCodeContract.Output.Dismissed -> Unit
        }
    }

    private val recoverSyncedDataLauncher = registerForActivityResult(
        RecoverSyncedDataContract(),
    ) { output ->
        when (output) {
            is RecoverSyncedDataContract.Output.SyncCompleted -> {
                handlePairingResult(output.result)
            }

            is RecoverSyncedDataContract.Output.Dismissed -> Unit
        }
    }

    private val previousSessionReadyLauncher = registerForActivityResult(
        PreviousSessionReadyContract(),
    ) { output ->
        when (output) {
            is PreviousSessionReadyContract.Output.Resume -> {
                exchangeSyncCodeLauncher.launch(
                    ExchangeSyncCodeContract.Input(
                        syncUrl = output.recoveryCode,
                        launchSource = launchSource,
                        originalFlow = OriginalFlow.RECOVER_SYNCED_DATA,
                    ),
                )
            }

            is PreviousSessionReadyContract.Output.ContinueSetup -> {
                viewModel.onContinueSetupAfterSkipRestore(output.originalFlow)
            }

            is PreviousSessionReadyContract.Output.Dismissed -> {
                viewModel.onSyncThisDeviceCanceled()
                viewModel.onConnectionCancelled()
            }
        }
    }

    private val exchangeSyncCodeLauncher = registerForActivityResult(
        ExchangeSyncCodeContract(),
    ) { output ->
        when (output) {
            is ExchangeSyncCodeContract.Output.SyncCompleted -> {
                handlePairingResult(output.result)
            }

            is ExchangeSyncCodeContract.Output.Dismissed -> {
                viewModel.onSyncThisDeviceCanceled()
                viewModel.onConnectionCancelled()
            }
        }
    }

    private val downloadPdfPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
        if (isGranted) {
            viewModel.generateRecoveryCode(this)
        } else {
            Snackbar.make(binding.root, R.string.sync_simplified_settings_storage_permission_message, Snackbar.LENGTH_LONG).show()
        }
    }

    private val syncedDeviceAdapter = SyncedDeviceAdapter(
        object : SyncedDeviceAdapter.Listener {
            override fun onDeviceClicked(device: ConnectedDevice) {
                viewModel.onEditDeviceClicked(device, requireAuth = true)
            }
        },
    )

    private val syncThisDeviceListener = OnCheckedChangeListener { _, isChecked ->
        if (isChecked) viewModel.onSyncThisDevice(launchSource)
    }

    private val autoRestoreListener = OnCheckedChangeListener { _, isChecked ->
        viewModel.onAutoRestoreToggleChanged(isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEdgeToEdge = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (isEdgeToEdge) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (isEdgeToEdge) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureSyncThisDeviceCta()
        configureSyncWithAnotherDeviceCta()
        configureRecoverDataCta()
        configureDevicesRecyclerView()
        configureBookmarksSection()
        configureWarningMessages()
        configureRecoverySection()
        configureGetOnOtherPlatformsItem()
        configureDataExpirationNotice()
        configureDataDeletionItem()

        observeViewModel()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onScreenExit()
    }

    private fun observeViewModel() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.notifyMeContainer.isVisible = viewState.showAccount
        binding.syncHeader.setState(
            isSyncEnabled = viewState.showAccount,
            isDuckAiAvailable = viewState.aiChatSyncEnabled,
        )
        renderEnabledState(viewState)
        renderDisabledState(viewState)
    }

    private fun renderEnabledState(viewState: ViewState) {
        syncedDeviceAdapter.submitList(viewState.syncedDevices)
        binding.includeEnabledView.apply {
            root.isVisible = viewState.showAccount
            getOnOtherPlatformsItem.setState(
                isNewDesktopBrowserAvailable = viewState.newDesktopBrowserSettingEnabled,
            )
            restoreOnReinstallItem.isVisible = viewState.showAutoRestoreToggle
            if (viewState.showAutoRestoreToggle) {
                restoreOnReinstallItem.quietlySetIsChecked(viewState.autoRestoreEnabled, autoRestoreListener)
                restoreOnReinstallItem.setLeadingIconResource(
                    if (viewState.autoRestoreEnabled) R.drawable.device_default_24 else R.drawable.device_soft_alert_24,
                )
            }
        }
    }

    private fun renderDisabledState(viewState: ViewState) {
        binding.includeDisabledView.apply {
            root.isGone = viewState.showAccount
            syncThisDeviceToggle.quietlySetIsChecked(viewState.isThisDeviceSyncing, syncThisDeviceListener)
            getOnOtherPlatformsItem.setState(
                isNewDesktopBrowserAvailable = viewState.newDesktopBrowserSettingEnabled,
            )
            syncWithAnotherDeviceButton.isEnabled = CreateAccountFlow !in viewState.disabledSetupFlows
            syncThisDeviceToggle.isEnabled = CreateAccountFlow !in viewState.disabledSetupFlows
            recoverDataItem.isEnabled = SignInFlow !in viewState.disabledSetupFlows
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AddAnotherDevice -> {
                authenticate {
                    addAnotherDeviceLauncher.launch(ReadSyncCodeContract.Input(launchSource, originalFlow = OriginalFlow.SYNC_WITH_ANOTHER))
                }
            }

            is AskDeleteAccount -> {
                authenticate {
                    showDeleteAccountDialog()
                }
            }

            is AskEditDevice -> {
                authenticate {
                    editDeviceLauncher.launch(EditDeviceContract.Input(command.device))
                }
            }

            // No-op in the simplified flow.
            is AskRemoveDevice -> Unit

            // No-op in the simplified flow.
            is AskSetupSyncDeepLink -> Unit

            is AskToCopyRecoveryCode -> {
                authenticate {
                    viewModel.onCopyRecoveryCodeAuthenticated()
                }
            }

            // No-op in the simplified flow.
            is AskTurnOffSync -> Unit

            is CheckIfUserHasStoragePermission -> {
                if (appBuildConfig.sdkInt < 30) {
                    downloadPdfPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.generateRecoveryCode(this)
                }
            }

            is DeepLinkIntoSetup -> {
                logcat { "TODO: Handle ${command.javaClass.simpleName} command" }
            }

            is IntroCreateAccount -> {
                authenticate(
                    onCancelled = {
                        viewModel.onSyncThisDeviceCanceled()
                        lifecycleScope.launch { syncSetupWideEvent.onUserAuthCancelled() }
                    },
                    onError = { message ->
                        viewModel.onSyncThisDeviceCanceled()
                        lifecycleScope.launch { syncSetupWideEvent.onUserAuthCancelled() }
                        showError(ShowError(R.string.sync_simplified_error_dialog_generic_body, message))
                    },
                    onSuccess = { hasValidAuth ->
                        if (hasValidAuth) {
                            // authenticate() also passes if device is not enrolled into auth,
                            // so only notify that auth was successful if it actually happened
                            lifecycleScope.launch { syncSetupWideEvent.onUserAuthSuccess() }
                        }
                        syncThisDeviceLauncher.launch(SyncThisDeviceContract.Input(launchSource))
                    },
                )
            }

            is IntroRecoverSyncData -> {
                authenticate {
                    recoverSyncedDataLauncher.launch(RecoverSyncedDataContract.Input(launchSource))
                }
            }

            is LaunchLearnMore -> {
                globalActivityStarter.start(
                    this,
                    SettingsWebViewScreenWithParams(
                        url = command.url,
                        screenTitle = getString(R.string.sync_simplified_settings_learn_more_screen_title),
                    ),
                )
            }

            is LaunchOriginalFlow -> {
                when (command.originalFlow) {
                    OriginalFlow.SYNC_THIS_DEVICE -> syncThisDeviceLauncher.launch(SyncThisDeviceContract.Input(launchSource))
                    OriginalFlow.SYNC_WITH_ANOTHER ->
                        syncWithAnotherDeviceLauncher.launch(ReadSyncCodeContract.Input(launchSource, originalFlow = OriginalFlow.SYNC_WITH_ANOTHER))
                    OriginalFlow.RECOVER_SYNCED_DATA -> recoverSyncedDataLauncher.launch(RecoverSyncedDataContract.Input(launchSource))
                }
            }

            is LaunchSyncGetOnOtherPlatforms -> {
                globalActivityStarter.start(this, SyncGetOnOtherPlatformsParams(command.source))
            }

            is RecoveryCodePDFSuccess -> {
                authenticate {
                    shareAction.shareFile(this, command.recoveryCodePDFFile)
                }
            }

            is RequestSetupAuthentication -> {
                launchDeviceAuthEnrollment(command.forSyncThisDevice)
            }

            is ShowDeviceUnsupported -> {
                startActivity(DeviceUnsupportedActivity.intent(this))
                finish()
            }

            is ShowError -> {
                showError(command)
            }

            is ShowMessage -> {
                Snackbar.make(binding.root, command.message, Snackbar.LENGTH_LONG).show()
            }

            is ShowPreviousSessionReady -> {
                authenticate(
                    onCancelled = {
                        viewModel.onSyncThisDeviceCanceled()
                        lifecycleScope.launch { syncSetupWideEvent.onUserAuthCancelled() }
                    },
                    onError = { message ->
                        viewModel.onSyncThisDeviceCanceled()
                        lifecycleScope.launch { syncSetupWideEvent.onUserAuthCancelled() }
                        showError(ShowError(R.string.sync_simplified_error_dialog_generic_body, message))
                    },
                    onSuccess = { hasValidAuth ->
                        if (hasValidAuth) {
                            // authenticate() also passes if device is not enrolled into auth,
                            // so only notify that auth was successful if it actually happened
                            lifecycleScope.launch { syncSetupWideEvent.onUserAuthSuccess() }
                        }
                        previousSessionReadyLauncher.launch(PreviousSessionReadyContract.Input(command.originalFlow))
                    },
                )
            }

            // No-op in the simplified flow.
            is ShowRecoveryCode -> Unit

            is SyncWithAnotherDevice -> {
                authenticate {
                    syncWithAnotherDeviceLauncher.launch(ReadSyncCodeContract.Input(launchSource, originalFlow = OriginalFlow.SYNC_THIS_DEVICE))
                }
            }
        }
    }

    private fun handlePairingResult(result: SyncPairingResult) {
        when (result) {
            is SyncPairingResult.Success -> {
                viewModel.onDeviceConnected()
                when (result.originalFlow) {
                    OriginalFlow.SYNC_THIS_DEVICE, OriginalFlow.RECOVER_SYNCED_DATA ->
                        recoveryCodeLauncher.launch(RecoveryCodeContract.Input(result.device.name))

                    OriginalFlow.SYNC_WITH_ANOTHER -> Unit
                }
            }

            is SyncPairingResult.Failure -> {
                viewModel.onSyncThisDeviceCanceled()
                viewModel.onConnectionCancelled()
            }
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }

    private fun configureToolbar() {
        setSupportActionBar(binding.includeToolbar.toolbar)
        binding.includeToolbar.toolbar.setNavigationIcon(CommonR.drawable.ic_arrow_left_24)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun configureSyncThisDeviceCta() {
        binding.includeDisabledView.syncThisDeviceToggle.setOnCheckedChangeListener(syncThisDeviceListener)
    }

    private fun configureSyncWithAnotherDeviceCta() {
        binding.includeDisabledView.syncWithAnotherDeviceButton.setOnClickListener {
            viewModel.onSyncWithAnotherDevice()
        }
        binding.includeEnabledView.syncWithAnotherDeviceItem.setOnClickListener {
            viewModel.onAddAnotherDevice()
        }
    }

    private fun configureRecoverDataCta() {
        binding.includeDisabledView.recoverDataItem.setOnClickListener {
            viewModel.onRecoverYourSyncedData()
        }
    }

    private fun configureDevicesRecyclerView() {
        binding.includeEnabledView.devicesRecycler.apply {
            layoutManager = LinearLayoutManager(this@SyncActivity)
            adapter = syncedDeviceAdapter
        }
    }

    private fun configureBookmarksSection() {
        binding.includeEnabledView.apply {
            val hasPlugins = syncSettingsPlugin.isNotEmpty()
            bookmarksSectionHeader.isVisible = hasPlugins
            bookmarksSectionContainer.isVisible = hasPlugins
            bookmarksSectionDivider.isVisible = hasPlugins

            if (hasPlugins) {
                syncSettingsPlugin.toSortedMap().forEach { (_, plugin) ->
                    bookmarksSectionContainer += plugin.getView(this@SyncActivity)
                }
            }
        }
    }

    private fun configureWarningMessages() {
        binding.includeEnabledView.apply {
            val hasPlugins = syncMessagesPlugin.isNotEmpty()
            warningsContainer.isVisible = hasPlugins

            if (hasPlugins) {
                syncMessagesPlugin.forEach { plugin ->
                    warningsContainer += plugin.getView(this@SyncActivity)
                }
            }
        }
    }

    private fun configureRecoverySection() {
        binding.includeEnabledView.apply {
            restoreOnReinstallItem.setOnCheckedChangeListener(autoRestoreListener)
            downloadRecoveryCodeItem.setOnClickListener {
                viewModel.onSaveRecoveryCodeClicked()
            }
            copyRecoveryCodeItem.setOnClickListener {
                viewModel.onCopyRecoveryCodeClicked()
            }
        }
    }

    private fun configureGetOnOtherPlatformsItem() {
        binding.includeEnabledView.getOnOtherPlatformsItem.setListener(
            object : GetOnOtherPlatformsListItem.Listener {
                override fun onClickGetDesktopBrowser() {
                    viewModel.onGetOnOtherPlatformsClickedWhenSyncEnabled()
                }

                override fun onClickGetOnOtherPlatforms() {
                    viewModel.onGetOnOtherPlatformsClickedWhenSyncEnabled()
                }
            },
        )
        binding.includeDisabledView.getOnOtherPlatformsItem.setListener(
            object : GetOnOtherPlatformsListItem.Listener {
                override fun onClickGetDesktopBrowser() {
                    viewModel.onGetOnOtherPlatformsClickedWhenSyncDisabled()
                }

                override fun onClickGetOnOtherPlatforms() {
                    viewModel.onGetOnOtherPlatformsClickedWhenSyncDisabled()
                }
            },
        )
    }

    private fun configureDataExpirationNotice() {
        binding.includeEnabledView.expirationNoticeLabel.addClickableSpan(
            textSequence = getText(R.string.sync_simplified_settings_data_expiration_notice),
            spans = listOf(
                "learn_more_link" to object : DuckDuckGoClickableSpan() {
                    override fun onClick(widget: View) {
                        viewModel.onLearnMoreClicked()
                    }
                },
            ),
        )
    }

    private fun configureDataDeletionItem() {
        val color = ColorStateList.valueOf(getColorFromAttr(CommonR.attr.daxColorDestructive))
        binding.includeEnabledView.deleteAccountItem.apply {
            leadingIcon().imageTintList = color
            setPrimaryTextColorStateList(color)
            setOnClickListener { viewModel.onDeleteAccountClicked(requireAuth = true) }
        }
    }

    private fun launchDeviceAuthEnrollment(forSyncThisDevice: Boolean) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_settings_require_passcode_dialog_title)
            .setMessage(getString(R.string.sync_simplified_settings_require_passcode_dialog_body))
            .setPositiveButton(R.string.sync_simplified_settings_require_passcode_dialog_primary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onDialogShown() {
                        lifecycleScope.launch { syncSetupWideEvent.onEnrollDeviceAuthDialogShown() }
                    }

                    override fun onPositiveButtonClicked() {
                        deviceAuthenticator.launchDeviceAuthEnrollment(this@SyncActivity)
                    }

                    override fun onDialogDismissed() {
                        // Only the Sync This Device flow uses a toggle that must be reset; other flows must not touch it.
                        if (forSyncThisDevice) viewModel.onSyncThisDeviceCanceled()
                    }
                },
            )
            .setCancellable(true)
            .show()
    }

    private fun showDeleteAccountDialog() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_settings_delete_server_data_dialog_title)
            .setMessage(getString(R.string.sync_simplified_settings_delete_server_data_dialog_body))
            .setPositiveButton(R.string.sync_simplified_settings_delete_server_data_dialog_primary_button, DESTRUCTIVE)
            .setNegativeButton(R.string.sync_simplified_settings_delete_server_data_dialog_secondary_button, GHOST_ALT)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onDeleteAccountConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onDeleteAccountCancelled()
                    }

                    override fun onDialogCancelled() {
                        viewModel.onDeleteAccountCancelled()
                    }
                },
            )
            .setCancellable(true)
            .show()
    }

    private fun showError(error: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_error_dialog_title)
            .setMessage(getString(error.message) + "\n" + error.reason)
            .setPositiveButton(R.string.sync_simplified_error_dialog_primary_button)
            .show()
    }

    private fun authenticate(
        config: AuthConfiguration = AuthConfiguration(),
        onError: (reason: String) -> Unit = { reason ->
            showError(ShowError(R.string.sync_simplified_error_dialog_generic_body, reason))
        },
        onCancelled: () -> Unit = {},
        onSuccess: (hasValidAuth: Boolean) -> Unit,
    ) {
        if (deviceAuthenticator.hasValidDeviceAuthentication()) {
            deviceAuthenticator.authenticate(this, config) { result ->
                when (result) {
                    is Success -> onSuccess(true)
                    is Error -> onError(result.reason)
                    is UserCancelled -> onCancelled()
                }
            }
        } else {
            onSuccess(false)
        }
    }
}
