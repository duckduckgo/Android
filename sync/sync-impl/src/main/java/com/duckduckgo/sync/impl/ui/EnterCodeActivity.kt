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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivityEnterCodeBinding
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Idle
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Loading
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.SwitchAccountSuccess
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class EnterCodeActivity : DuckDuckGoActivity() {
    private val binding: ActivityEnterCodeBinding by viewBinding()
    private val viewModel: EnterCodeViewModel by bindViewModel()

    private lateinit var codeType: Code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        codeType = intent.getSerializableExtra(EXTRA_CODE_TYPE) as? Code ?: Code.RECOVERY_CODE
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        observeUiEvents()
        configureListeners()
        if (savedInstanceState == null) {
            viewModel.onEnterManualCodeScreenShown(codeType)
        }
    }

    private fun configureListeners() {
        binding.pasteCodeButton.setOnClickListener {
            viewModel.onPasteCodeClicked()
        }
    }

    private fun observeUiEvents() {
        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel.commands().flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).onEach { processCommand(it) }.launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        binding.enterCodeHint.isVisible = viewState.code.isEmpty()
        binding.pastedCode.isVisible = viewState.code.isNotEmpty()
        binding.pastedCode.text = viewState.code

        when (viewState.authState) {
            AuthState.Error -> {
                binding.loadingIndicatorContainer.hide()
                binding.errorAuthStateHint.show()
                binding.pasteCodeButton.isEnabled = true
            }
            Idle -> {
                binding.loadingIndicatorContainer.hide()
                binding.errorAuthStateHint.hide()
                binding.pasteCodeButton.isEnabled = true
            }
            Loading -> {
                binding.loadingIndicatorContainer.show()
                binding.errorAuthStateHint.hide()
                binding.pasteCodeButton.isEnabled = false
            }
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            LoginSuccess -> {
                setResult(RESULT_OK)
                finish()
            }

            is ShowError -> {
                showError(command)
            }

            is AskToSwitchAccount -> askUserToSwitchAccount(command)
            SwitchAccountSuccess -> {
                val resultIntent = Intent()
                resultIntent.putExtra(EXTRA_USER_SWITCHED_ACCOUNT, true)
                setResult(RESULT_OK, resultIntent)
                finish()
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
                    }
                },
            ).show()
    }

    private fun askUserToSwitchAccount(it: AskToSwitchAccount) {
        viewModel.onUserAskedToSwitchAccount()
        TextAlertDialogBuilder(this)
            .setTitle(R.string.sync_dialog_switch_account_header)
            .setMessage(R.string.sync_dialog_switch_account_description)
            .setPositiveButton(R.string.sync_dialog_switch_account_primary_button)
            .setNegativeButton(R.string.sync_dialog_switch_account_secondary_button)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserAcceptedJoiningNewAccount(it.encodedStringCode)
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserCancelledJoiningNewAccount()
                    }
                },
            ).show()
    }

    companion object {
        enum class Code {
            RECOVERY_CODE,
            CONNECT_CODE,
        }

        private const val EXTRA_CODE_TYPE = "codeType"

        const val EXTRA_USER_SWITCHED_ACCOUNT = "userSwitchedAccount"

        internal fun intent(context: Context, codeType: Code): Intent {
            return Intent(context, EnterCodeActivity::class.java).apply {
                putExtra(EXTRA_CODE_TYPE, codeType)
            }
        }
    }
}
