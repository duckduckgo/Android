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

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.daxprompts.api.DaxPromptDuckPlayerNoParams
import com.duckduckgo.daxprompts.impl.R
import com.duckduckgo.daxprompts.impl.databinding.ActivityDaxPromptDuckPlayerBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(DaxPromptDuckPlayerNoParams::class)
class DaxPromptDuckPlayerActivity : DuckDuckGoActivity() {
    private val viewModel: DaxPromptDuckPlayerViewModel by bindViewModel()
    private val binding: ActivityDaxPromptDuckPlayerBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)
        if (isDarkThemeEnabled()) {
            renderDarkUi()
        } else {
            renderLightUi()
        }
        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        applyFullScreenFlags()
        markAsShown()
    }

    private fun renderDarkUi() {
        binding.orangeShape.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.background_shape_dark))
        binding.daxPromptDuckPlayerContainer.setBackgroundColor(getColor(R.color.daxPromptBackgroundDark))
        binding.daxPromptDuckPlayerMessageContainer.background = ContextCompat.getDrawable(this, R.drawable.background_dax_message_dark)
        binding.daxPromptDuckPlayerPrimaryButton.background = ContextCompat.getDrawable(this, R.drawable.background_button_dark_with_ripple)
    }

    private fun renderLightUi() {
        binding.orangeShape.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.background_shape))
        binding.daxPromptDuckPlayerContainer.setBackgroundColor(getColor(R.color.daxPromptBackground))
        binding.daxPromptDuckPlayerMessageContainer.background = ContextCompat.getDrawable(this, R.drawable.background_dax_message)
        binding.daxPromptDuckPlayerPrimaryButton.background = ContextCompat.getDrawable(this, R.drawable.background_button_with_ripple)
    }

    private fun setupListeners() {
        binding.daxPromptDuckPlayerCloseButton.setOnClickListener {
            viewModel.onCloseButtonClicked()
        }
        binding.daxPromptDuckPlayerPrimaryButton.setOnClickListener {
            viewModel.onPrimaryButtonClicked()
        }
    }

    private fun setupObservers() {
        viewModel.commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun processCommand(command: DaxPromptDuckPlayerViewModel.Command) {
        when (command) {
            is DaxPromptDuckPlayerViewModel.Command.CloseScreen -> {
                setResult(RESULT_OK)
                finish()
            }

            is DaxPromptDuckPlayerViewModel.Command.TryDuckPlayer -> {
                val resultIntent = Intent().apply {
                    putExtra(DAX_PROMPT_DUCK_PLAYER_ACTIVITY_URL_EXTRA, command.url)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
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
        ViewCompat.requestApplyInsets(binding.daxPromptDuckPlayerContainer)
    }

    private fun markAsShown() {
        viewModel.markDuckPlayerPromptAsShown()
    }

    companion object {
        const val DAX_PROMPT_DUCK_PLAYER_ACTIVITY_URL_EXTRA = "DAX_PROMPT_DUCK_PLAYER_ACTIVITY_URL_EXTRA"
    }
}
