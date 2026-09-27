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

package com.duckduckgo.sync.impl.ui.pairing.show

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.ActivityDisplayQrCodeBinding
import com.duckduckgo.sync.impl.databinding.DialogSyncCheckOtherDeviceBinding
import com.duckduckgo.sync.impl.databinding.DialogSyncConnectingBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.pairing.SyncPairingResult
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2ConfirmationMessage
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShareCode
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.DialogType
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Factory
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.ViewState
import com.duckduckgo.sync.impl.ui.pairing.showExchangeV1PairingError
import com.duckduckgo.sync.impl.ui.pairing.showExchangeV2PairingError
import com.duckduckgo.sync.impl.ui.pairing.showLeadingProgressSpinner
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class DisplayQrCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivityDisplayQrCodeBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var shareAction: ShareAction

    private val launchSource get() = intent.getStringExtra(LAUNCH_SOURCE_EXTRA_KEY)

    private val syncEntryPoint
        get() = requireNotNull(IntentCompat.getSerializableExtra(intent, ORIGINAL_FLOW_EXTRA_KEY, SyncEntryPoint::class.java)) {
            "Missing intent extra: '$ORIGINAL_FLOW_EXTRA_KEY'"
        }

    private val viewModel by viewModels<DisplayQrCodeViewModel> {
        Provider(vmFactory, syncEntryPoint, launchSource)
    }

    private var visibleDialog: DaxAlertDialog? = null
    private var visibleDialogType: DialogType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureCodeButtons()

        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        visibleDialog?.dismiss()
        visibleDialog = null
    }

    private fun configureCodeButtons() {
        binding.copyCodeButton.setOnClickListener { viewModel.onCopyCodeClicked() }
        binding.shareButton.setOnClickListener { viewModel.onShareCodeClicked() }
    }

    private fun observeViewModel() {
        viewModel
            .viewState
            .flowWithLifecycle(lifecycle)
            .onEach { render(it) }
            .launchIn(lifecycleScope)

        viewModel
            .commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        val bitmapWrapper = viewState.bitmap
        if (bitmapWrapper != null) {
            binding.qrCodeImage.setImageBitmap(bitmapWrapper.bitmap)
            binding.qrCodeText.text = bitmapWrapper.displayCode
        }
        binding.loadingIndicator.isGone = bitmapWrapper != null
        binding.qrCodeContent.isVisible = bitmapWrapper != null

        renderDialog(viewState.dialog)
    }

    private fun renderDialog(dialogType: DialogType?) {
        if (dialogType == visibleDialogType) return

        visibleDialogType = dialogType
        visibleDialog?.dismiss()
        visibleDialog = when (dialogType) {
            is DialogType.HostConfirmation -> showHostConfirmationDialog(dialogType.peerName, dialogType.peerKind)
            is DialogType.JoinerConfirmation -> showJoinerConfirmationDialog(dialogType.peerName, dialogType.peerKind)
            is DialogType.Connecting -> showConnectingDialog()
            is DialogType.CheckOtherDevice -> showCheckOtherDeviceDialog()
            null -> null
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is ShowMessage -> showMessage(command.message)
            is ShareCode -> shareText(command.code)
            is SetPairingResult -> setResult(SyncPairingResult.RESULT_SYNC_COMPLETED, SyncPairingResult.resultIntent(command.result))
            is ShowV1Error -> showV1Error(command)
            is ShowV2Error -> showV2Error(command)
            is Close -> finish()
        }
    }

    private fun showHostConfirmationDialog(peerName: String?, peerKind: PeerKind?): DaxAlertDialog {
        val dialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_host_title)
            .setMessage(exchangeV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_host_primary_button)
            .setNegativeButton(R.string.sync_simplified_pairing_dialog_host_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onHostConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onHostDenied()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun showJoinerConfirmationDialog(peerName: String?, peerKind: PeerKind?): DaxAlertDialog {
        val dialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_joiner_title)
            .setMessage(exchangeV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_joiner_primary_button)
            .setNegativeButton(R.string.sync_simplified_pairing_dialog_joiner_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onJoinerConfirmed()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onJoinerDenied()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun showConnectingDialog(): DaxAlertDialog {
        val content = DialogSyncConnectingBinding.inflate(layoutInflater)
        content.connectingLabel.showLeadingProgressSpinner()

        val dialog = CustomAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_connecting_title)
            .setView(content)
            .setNegativeButton(CommonR.string.cancel)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onNegativeButtonClicked() {
                        viewModel.onConnectingCancelled()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun showCheckOtherDeviceDialog(): DaxAlertDialog {
        val content = DialogSyncCheckOtherDeviceBinding.inflate(layoutInflater)
        content.checkOtherDeviceLabel.showLeadingProgressSpinner()

        val dialog = CustomAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_check_other_device_title)
            .setView(content)
            .setNegativeButton(CommonR.string.cancel)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onNegativeButtonClicked() {
                        viewModel.onCheckOtherDeviceCancelled()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun showMessage(@StringRes message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun shareText(text: String) {
        shareAction.shareText(this, text)
    }

    private fun showV1Error(error: ShowV1Error) {
        showExchangeV1PairingError(error.content) {
            viewModel.onErrorDialogDismissed()
        }
    }

    private fun showV2Error(error: ShowV2Error) {
        showExchangeV2PairingError(error.content) {
            viewModel.onErrorDialogDismissed()
        }
    }

    private fun configureToolbar() {
        setSupportActionBar(binding.includeToolbar.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.includeToolbar.toolbar.setNavigationIcon(CommonR.drawable.ic_close_24)
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.includeToolbar.appBarLayout)
        edgeToEdgeHandler.applyScrollableNavigationBarInsets(binding.contentScrollView)
    }

    companion object {
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"
        private const val ORIGINAL_FLOW_EXTRA_KEY = "original_flow"

        fun intent(
            context: Context,
            syncEntryPoint: SyncEntryPoint,
            launchSource: String?,
        ): Intent {
            return Intent(context, DisplayQrCodeActivity::class.java).apply {
                putExtra(ORIGINAL_FLOW_EXTRA_KEY, syncEntryPoint)
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
