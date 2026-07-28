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

import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ShareAction
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2QrCodeBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels.PeerKind
import com.duckduckgo.sync.impl.ui.showV2PairingError
import com.duckduckgo.sync.impl.ui.syncV2ConfirmationMessage
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.SetFailureResult
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.SetSuccessResult
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShareCode
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Factory
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.Factory.Provider
import com.duckduckgo.sync.impl.ui.v2.SyncExchangeViewModel.ViewState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class QrCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2QrCodeBinding>()

    @Inject
    lateinit var vmFactory: Factory

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var shareAction: ShareAction

    private val viewModel by viewModels<SyncExchangeViewModel> {
        Provider(vmFactory, null)
    }

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
    }

    private fun processCommand(command: Command) {
        when (command) {
            is AskHostConfirmation -> showHostConfirmationDialog(command.peerName, command.peerKind)
            is AskJoinerConfirmation -> showJoinerConfirmationDialog(command.peerName, command.peerKind)
            is ShowMessage -> showMessage(command.message)
            is ShareCode -> shareText(command.code)
            is SetSuccessResult -> logcat { "TODO" }
            is SetFailureResult -> logcat { "TODO" }
            is ShowError -> showError(command)
            is ShowV2Error -> showV2Error(command)
            is Close -> finish()
        }
    }

    private fun showHostConfirmationDialog(peerName: String?, peerKind: PeerKind?) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_v2_host_confirmation_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_v2_host_confirmation_positive)
            .setNegativeButton(R.string.sync_v2_host_confirmation_negative)
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

    private fun showJoinerConfirmationDialog(peerName: String?, peerKind: PeerKind?) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_v2_joiner_confirmation_title)
            .setMessage(syncV2ConfirmationMessage(peerName, peerKind))
            .setPositiveButton(R.string.sync_v2_joiner_confirmation_positive)
            .setNegativeButton(R.string.sync_v2_joiner_confirmation_negative)
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

    private fun showMessage(@StringRes message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun shareText(text: String) {
        shareAction.shareText(this, text)
    }

    private fun showError(error: ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(error.message) + "\n" + error.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            )
            .show()
    }

    private fun showV2Error(error: ShowV2Error) {
        showV2PairingError(error.content) {
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
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentScrollView, drawBehindGestureNav = true)
    }
}
