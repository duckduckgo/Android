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
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2ExchangeSyncCodeBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.ui.showV1PairingError
import com.duckduckgo.sync.impl.ui.showV2PairingError
import com.duckduckgo.sync.impl.ui.syncV2ConfirmationMessage
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.RunAcknowledgmentAnimation
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.SetFailureResult
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.SetSuccessResult
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.ShowPairingAcknowledgement
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.SyncWithAnotherDeviceViewModel.Factory.Provider
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class ExchangeSyncCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2ExchangeSyncCodeBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val syncUrl
        get() = requireNotNull(intent.getStringExtra(SYNC_URL_EXTRA_KEY)) {
            "Missing intent extra: '$SYNC_URL_EXTRA_KEY'"
        }

    private val viewModel by viewModels<SyncWithAnotherDeviceViewModel> {
        Provider(vmFactory, syncUrl)
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

        configureAcknowledgementAnimation()
        configureConnectingLabel()

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.commands
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AskHostConfirmation -> showHostConfirmationDialog(command.peerName, command.peerKind)
            is AskJoinerConfirmation -> showJoinerConfirmationDialog(command.peerName, command.peerKind)
            is ShowPairingAcknowledgement -> showPairingAcknowledgmentDialog()
            is RunAcknowledgmentAnimation -> runAcknowledgementAnimation()
            is SetSuccessResult -> setResult(DisplayQrCodeContract.RESULT_SYNC_SUCCESS)
            is SetFailureResult -> setResult(DisplayQrCodeContract.RESULT_SYNC_FAILURE)
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
            .setTitle(R.string.sync_simplified_pairing_dialog_host_confirmation_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_host_positive_cta)
            .setNegativeButton(R.string.sync_simplified_pairing_dialog_host_negative_cta)
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
            .setTitle(R.string.sync_simplified_pairing_dialog_joiner_confirmation_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_joiner_positive_cta)
            .setNegativeButton(R.string.sync_simplified_pairing_dialog_joiner_negative_cta)
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

    private fun showPairingAcknowledgmentDialog() {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_simplified_pairing_dialog_confirm_title)
            .setPositiveButton(R.string.sync_simplified_pairing_dialog_confirm_cta)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onPairingAcknowledged()
                    }
                },
            )
            .show()
    }

    private fun runAcknowledgementAnimation() {
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
                    viewModel.onAnimationComplete()
                }
            },
        )
    }

    companion object {
        private const val SYNC_URL_EXTRA_KEY = "sync_url"
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"

        fun intent(
            context: Context,
            syncUrl: String,
            launchSource: String?,
        ): Intent {
            return Intent(context, ExchangeSyncCodeActivity::class.java).apply {
                putExtra(SYNC_URL_EXTRA_KEY, syncUrl)
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
