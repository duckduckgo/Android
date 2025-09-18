/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.daxprompts.impl.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonNoParams
import com.duckduckgo.daxprompts.impl.databinding.ActivityDaxPromptBrowserComparisonBinding
import com.duckduckgo.daxprompts.impl.ui.DaxPromptBrowserComparisonViewModel.Command
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DaxPromptBrowserComparisonNoParams::class)
class DaxPromptBrowserComparisonActivity : DuckDuckGoActivity() {
    private val viewModel: DaxPromptBrowserComparisonViewModel by bindViewModel()
    private val binding: ActivityDaxPromptBrowserComparisonBinding by viewBinding()

    private val startBrowserComparisonChartActivityForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                logcat { "Received RESULT_OK from BrowserComparisonChart" }
                viewModel.onDefaultBrowserSet()
            } else {
                logcat { "Received non-OK result from BrowserComparisonChart" }
                viewModel.onDefaultBrowserNotSet()
            }
        }

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
        markAsShown()
    }

    private fun setupListeners() {
        binding.daxPromptBrowserComparisonCloseButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.daxPromptBrowserComparisonPrimaryButton.setOnClickListener {
            viewModel.onPrimaryButtonClicked()
        }

        binding.daxPromptBrowserComparisonGhostButton.setOnClickListener {
            viewModel.onGhostButtonClicked()
        }
    }

    private fun setupObservers() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is Command.CloseScreen -> {
                if (command.defaultBrowserSet == null) {
                    setResult(RESULT_OK)
                } else {
                    val resultIntent = Intent().apply {
                        putExtra(DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA, command.defaultBrowserSet)
                    }
                    setResult(RESULT_OK, resultIntent)
                }
                finish()
            }

            is Command.BrowserComparisonChart -> {
                startBrowserComparisonChartActivityForResult.launch(command.intent)
            }
        }
    }

    private fun applyFullScreenFlags() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.BLACK
        }
        ViewCompat.requestApplyInsets(binding.daxPromptBrowserComparisonContainer)
    }

    private fun markAsShown() {
        viewModel.markBrowserComparisonPromptAsShown()
    }

    companion object {
        const val DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA = "DAX_PROMPT_BROWSER_COMPARISON_SET_DEFAULT_EXTRA"
    }
}
