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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityConnectSyncBinding
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.ViewState
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncWithAnotherDeviceActivity : DuckDuckGoActivity() {
    private val binding: ActivityConnectSyncBinding by viewBinding()
    private val viewModel: SyncWithAnotherActivityViewModel by bindViewModel()

    private val enterCodeLauncher = registerForActivityResult(
        EnterCodeContract(),
    ) { resultOk ->
        if (resultOk) {
            viewModel.onLoginSuccess()
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
        binding.qrCodeReader.resume()
    }

    override fun onPause() {
        super.onPause()
        binding.qrCodeReader.pause()
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun render(viewState: ViewState) {
        viewState.qrCodeBitmap?.let {
            binding.qrCodeImageView.show()
            binding.qrCodeImageView.setImageBitmap(it)
            binding.copyCodeButton.setOnClickListener {
                viewModel.onCopyCodeClicked()
            }
        }
    }

    private fun processCommand(it: Command) {
        when (it) {
            Command.ReadTextCode -> {
                enterCodeLauncher.launch(RECOVERY_CODE)
            }
            Command.LoginSuccess -> {
                setResult(RESULT_OK)
                finish()
            }
            Command.FinishWithError -> {
                setResult(RESULT_CANCELED)
                finish()
            }

            is Command.ShowMessage -> Snackbar.make(binding.root, it.messageId, Snackbar.LENGTH_SHORT).show()
            is Command.ShowError -> showError(it)
        }
    }

    private fun configureListeners() {
        binding.qrCodeReader.apply {
            decodeSingle { result -> viewModel.onQRCodeScanned(result) }
            onCtaClicked {
                viewModel.onReadTextCodeClicked()
            }
        }
    }

    private fun showError(it: Command.ShowError) {
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_error_title)
            .setMessage(getString(it.message) + "\n" + it.reason)
            .setPositiveButton(R.string.sync_dialog_error_ok)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onErrorDialogDismissed()
                    }
                },
            ).show()
    }

    companion object {
        internal fun intent(context: Context): Intent {
            return Intent(context, SyncWithAnotherDeviceActivity::class.java)
        }
    }
}
