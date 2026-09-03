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

package com.duckduckgo.sync.impl.ui.pairing.process

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityProcessSyncCodeBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.pairing.SyncPairingResult
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2ConfirmationMessage
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command.RunAcknowledgmentAnimation
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.DialogType
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Factory
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.pairing.process.ProcessSyncCodeViewModel.ViewState
import com.duckduckgo.sync.impl.ui.pairing.showExchangeV1PairingError
import com.duckduckgo.sync.impl.ui.pairing.showExchangeV2PairingError
import com.duckduckgo.sync.impl.ui.pairing.showLeadingProgressSpinner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@InjectWith(ActivityScope::class)
class ProcessSyncCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivityProcessSyncCodeBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val source
        get() = requireNotNull(IntentCompat.getParcelableExtra(intent, SYNC_CODE_SOURCE_EXTRA_KEY, SyncCodeSource::class.java)) {
            "Missing intent extra: '$SYNC_CODE_SOURCE_EXTRA_KEY'"
        }

    private val viewModel by viewModels<ProcessSyncCodeViewModel> {
        Provider(vmFactory, source)
    }

    private var visibleDialog: DaxAlertDialog? = null
    private var visibleDialogType: DialogType? = null

    private var isAcknowledgementAnimationExecuted = false

    private val headlineLabel
        get() = if (source.entryPoint == SyncEntryPoint.RECOVER_SYNCED_DATA) {
            R.string.sync_simplified_pairing_headline_recovery
        } else {
            R.string.sync_simplified_pairing_headline
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

        configureHeadline()
        configureAcknowledgementAnimation()
        configureConnectingLabel()
        configureBackHandling()

        observeViewModel()
    }

    override fun onDestroy() {
        visibleDialog?.dismiss()
        visibleDialog = null
        super.onDestroy()
    }

    private fun observeViewModel() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)

        viewModel.viewState
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { renderViewState(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        val isWaiting = viewState.isWaitingForOtherDevice
        binding.lockAnimation.isGone = isWaiting
        binding.checkOtherDeviceImage.isVisible = isWaiting
        binding.headlineText.setText(if (isWaiting) R.string.sync_simplified_pairing_headline_check_other_device else headlineLabel)

        renderDialog(viewState.dialog)
    }

    private fun renderDialog(dialogType: DialogType?) {
        if (dialogType == visibleDialogType) return

        visibleDialogType = dialogType
        visibleDialog?.dismiss()
        visibleDialog = when (dialogType) {
            is DialogType.HostConfirmation -> showHostConfirmationDialog(dialogType.peerName, dialogType.peerKind)
            is DialogType.JoinerConfirmation -> showJoinerConfirmationDialog(dialogType.peerName, dialogType.peerKind)
            is DialogType.SwitchAccount -> showSwitchAccountDialog(dialogType.encodedStringCode)
            is DialogType.PairingAcknowledgment -> showPairingAcknowledgmentDialog()
            null -> null
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is RunAcknowledgmentAnimation -> runAcknowledgementAnimation()
            is SetPairingResult -> setResult(SyncPairingResult.RESULT_SYNC_COMPLETED, SyncPairingResult.resultIntent(command.result))
            is ShowV1Error -> showV1Error(command)
            is ShowV2Error -> showV2Error(command)
            is Close -> finish()
        }
    }

    private fun showHostConfirmationDialog(
        peerName: String?,
        peerKind: PeerKind?,
    ): DaxAlertDialog {
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

    private fun showJoinerConfirmationDialog(
        peerName: String?,
        peerKind: PeerKind?,
    ): DaxAlertDialog {
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

    private fun showSwitchAccountDialog(encodedStringCode: String): DaxAlertDialog {
        val dialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_switch_account_header)
            .setMessage(R.string.sync_dialog_switch_account_description)
            .setPositiveButton(R.string.sync_dialog_switch_account_primary_button)
            .setNegativeButton(R.string.sync_dialog_switch_account_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserAcceptedSwitchingAccount(encodedStringCode)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserCancelledSwitchingAccount()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun showPairingAcknowledgmentDialog(): DaxAlertDialog {
        val dialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_acknowledgment_title)
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_acknowledgment_primary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onAcknowledgmentConfirmed()
                    }
                },
            )
        dialog.show()
        return dialog
    }

    private fun runAcknowledgementAnimation() {
        if (isAcknowledgementAnimationExecuted) return
        isAcknowledgementAnimationExecuted = true
        binding.lockAnimation.playAnimation()
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

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applySystemBarInsets(binding.root)
    }

    private fun configureBackHandling() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.onUserCanceled()
            finish()
        }
    }

    private fun configureHeadline() {
        binding.headlineText.setText(headlineLabel)
    }

    private fun configureConnectingLabel() {
        binding.connectingLabel.showLeadingProgressSpinner()
    }

    private fun configureAcknowledgementAnimation() {
        binding.lockAnimation.addAnimatorListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lifecycleScope.launch {
                        delay(1.seconds)
                        viewModel.onAnimationComplete()
                    }
                }
            },
        )
    }

    companion object {
        private const val SYNC_CODE_SOURCE_EXTRA_KEY = "code_source"

        fun intent(
            context: Context,
            codeSource: SyncCodeSource,
        ): Intent {
            return Intent(context, ProcessSyncCodeActivity::class.java).apply {
                putExtra(SYNC_CODE_SOURCE_EXTRA_KEY, codeSource)
            }
        }
    }
}
