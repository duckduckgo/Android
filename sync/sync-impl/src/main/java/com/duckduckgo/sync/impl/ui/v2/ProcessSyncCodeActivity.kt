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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2ProcessSyncCodeBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.showV1PairingError
import com.duckduckgo.sync.impl.ui.showV2PairingError
import com.duckduckgo.sync.impl.ui.syncV2ConfirmationMessage
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskSwitchAccount
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.RunAcknowledgmentAnimation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowPairingAcknowledgement
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.ViewState
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class ProcessSyncCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2ProcessSyncCodeBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val syncCode
        get() = requireNotNull(intent.getStringExtra(SYNC_CODE_EXTRA_KEY)) {
            "Missing intent extra: '$SYNC_CODE_EXTRA_KEY'"
        }

    private val syncEntryPoint
        get() = requireNotNull(IntentCompat.getSerializableExtra(intent, ORIGINAL_FLOW_EXTRA_KEY, SyncEntryPoint::class.java)) {
            "Missing intent extra: '$ORIGINAL_FLOW_EXTRA_KEY'"
        }

    private val viewModel by viewModels<ProcessSyncCodeViewModel> {
        Provider(vmFactory, syncCode, syncEntryPoint)
    }

    private var acknowledgementDialog: TextAlertDialogBuilder? = null

    private var isAcknowledgementAnimationExecuted = false

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

        observeViewModel()
    }

    override fun onDestroy() {
        acknowledgementDialog = null
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
        if (viewState.isLoggedIn) {
            dismissPairingAcknowledgementDialog()
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AskHostConfirmation -> showHostConfirmationDialog(command.peerName, command.peerKind)
            is AskJoinerConfirmation -> showJoinerConfirmationDialog(command.peerName, command.peerKind)
            is AskSwitchAccount -> showSwitchAccountDialog(command.encodedStringCode)
            is ShowPairingAcknowledgement -> showPairingAcknowledgmentDialog()
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
    ) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_host_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
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
            .show()
    }

    private fun showJoinerConfirmationDialog(
        peerName: String?,
        peerKind: PeerKind?,
    ) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_joiner_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
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
            .show()
    }

    private fun showSwitchAccountDialog(encodedStringCode: String) {
        TextAlertDialogBuilder(this)
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
            .show()
    }

    private fun showPairingAcknowledgmentDialog() {
        if (viewModel.viewState.value.isLoggedIn) {
            viewModel.runAcknowledgementAnimation()
            return
        }
        acknowledgementDialog = TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_acknowledgment_title)
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_acknowledgment_primary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onDialogDismissed() {
                        viewModel.runAcknowledgementAnimation()
                    }
                },
            )
        acknowledgementDialog?.show()
    }

    private fun dismissPairingAcknowledgementDialog() {
        acknowledgementDialog?.dismiss()
        acknowledgementDialog = null
    }

    private fun runAcknowledgementAnimation() {
        if (isAcknowledgementAnimationExecuted) return
        isAcknowledgementAnimationExecuted = true
        binding.lockAnimation.playAnimation()
    }

    private fun showV1Error(error: ShowV1Error) {
        showV1PairingError(error.content) {
            viewModel.onErrorDialogDismissed()
        }
    }

    private fun showV2Error(error: ShowV2Error) {
        showV2PairingError(error.content) {
            viewModel.onErrorDialogDismissed()
        }
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applySystemBarInsets(binding.root)
    }

    private fun configureHeadline() {
        val text = if (syncEntryPoint == SyncEntryPoint.RECOVER_SYNCED_DATA) {
            R.string.sync_simplified_pairing_headline_recovery
        } else {
            R.string.sync_simplified_pairing_headline
        }
        binding.headlineText.setText(text)
    }

    private fun configureConnectingLabel() {
        val progressDrawableSpec = CircularProgressIndicatorSpec(this, null, 0).apply {
            indicatorSize = 20.toPx()
            indicatorInset = 0
            trackThickness = 3.toPx()
            indicatorColors = intArrayOf(getColorFromAttr(CommonR.attr.daxColorAccentBlue))
        }
        val progressDrawable = IndeterminateDrawable.createCircularDrawable(this, progressDrawableSpec).apply {
            setVisible(true, false)
        }
        binding.connectingLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(progressDrawable, null, null, null)
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
        private const val SYNC_CODE_EXTRA_KEY = "sync_code"
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"
        private const val ORIGINAL_FLOW_EXTRA_KEY = "original_flow"

        fun intent(
            context: Context,
            syncCode: String,
            syncEntryPoint: SyncEntryPoint,
            launchSource: String?,
        ): Intent {
            return Intent(context, ProcessSyncCodeActivity::class.java).apply {
                putExtra(SYNC_CODE_EXTRA_KEY, syncCode)
                putExtra(ORIGINAL_FLOW_EXTRA_KEY, syncEntryPoint)
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
