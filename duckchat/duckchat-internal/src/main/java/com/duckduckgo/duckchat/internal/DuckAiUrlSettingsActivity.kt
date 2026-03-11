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

package com.duckduckgo.duckchat.internal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.internal.DuckAiUrlSettingsViewModel.Command.ShowMessage
import com.duckduckgo.duckchat.internal.databinding.ActivityDuckAiUrlSettingsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class DuckAiUrlSettingsActivity : DuckDuckGoActivity() {

    private val binding: ActivityDuckAiUrlSettingsBinding by viewBinding()
    private val viewModel: DuckAiUrlSettingsViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar)

        configureViews()
        viewModel.start()

        viewModel.viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { renderView(it) }
            .launchIn(lifecycleScope)

        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun configureViews() {
        binding.load.setOnClickListener {
            viewModel.onSaveClicked(binding.urlInput.text)
        }
        binding.reset.setOnClickListener {
            viewModel.onResetClicked()
        }
    }

    private fun renderView(viewState: DuckAiUrlSettingsViewModel.ViewState) {
        binding.urlInput.text = viewState.customUrl
    }

    private fun processCommand(command: DuckAiUrlSettingsViewModel.Command) {
        when (command) {
            is ShowMessage -> Toast.makeText(this, getString(command.messageResId), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DuckAiUrlSettingsActivity::class.java)
        }
    }
}
