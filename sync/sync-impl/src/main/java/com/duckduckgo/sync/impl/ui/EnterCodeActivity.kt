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
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.databinding.ActivityEnterCodeBinding
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Idle
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Loading
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSucess
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
            }
            Idle -> {
                binding.loadingIndicatorContainer.hide()
                binding.errorAuthStateHint.hide()
            }
            Loading -> {
                binding.loadingIndicatorContainer.show()
                binding.errorAuthStateHint.hide()
            }
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            LoginSucess -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    companion object {
        enum class Code {
            RECOVERY_CODE,
            CONNECT_CODE,
        }

        private const val EXTRA_CODE_TYPE = "codeType"

        internal fun intent(context: Context, codeType: Code): Intent {
            return Intent(context, EnterCodeActivity::class.java).apply {
                putExtra(EXTRA_CODE_TYPE, codeType)
            }
        }
    }
}
