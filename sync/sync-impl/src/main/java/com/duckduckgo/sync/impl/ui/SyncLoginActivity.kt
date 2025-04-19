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
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityLoginSyncBinding
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code.RECOVERY_CODE
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.Error
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.LoginSucess
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.ReadTextCode
import com.duckduckgo.sync.impl.ui.SyncLoginViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract
import com.duckduckgo.sync.impl.ui.setup.EnterCodeContract.EnterCodeContractOutput
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class SyncLoginActivity : DuckDuckGoActivity() {
    private val binding: ActivityLoginSyncBinding by viewBinding()
    private val viewModel: SyncLoginViewModel by bindViewModel()

    private val enterCodeLauncher = registerForActivityResult(
        EnterCodeContract(),
    ) { result ->
        if (result != EnterCodeContractOutput.Error) {
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
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(it: Command) {
        when (it) {
            ReadTextCode -> {
                enterCodeLauncher.launch(RECOVERY_CODE)
            }
            Error -> {
                setResult(RESULT_CANCELED)
                finish()
            }
            LoginSucess -> {
                setResult(RESULT_OK)
                finish()
            }

            is ShowError -> showError(it)
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

    private fun showError(it: ShowError) {
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
            return Intent(context, SyncLoginActivity::class.java)
        }
    }
}
