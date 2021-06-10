/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.beta

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.beta.BetaFeaturesViewModel.Command
import com.duckduckgo.app.beta.BetaFeaturesViewModel.Command.LaunchEmailProtection
import com.duckduckgo.app.browser.databinding.ActivityBetaFeaturesBinding
import com.duckduckgo.app.email.ui.EmailProtectionActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BetaFeaturesActivity : DuckDuckGoActivity() {

    private val viewModel: BetaFeaturesViewModel by bindViewModel()
    private lateinit var binding: ActivityBetaFeaturesBinding

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBetaFeaturesBinding.inflate(layoutInflater)

        viewModel.commandsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { processCommand(it) }.launchIn(lifecycleScope)
        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.emailSetting.setOnClickListener { viewModel.onEmailSettingClicked() }
    }

    private fun launchEmailProtection() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionActivity.intent(this), options)
    }

    private fun processCommand(it: Command) {
        when (it) {
            is LaunchEmailProtection -> launchEmailProtection()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, BetaFeaturesActivity::class.java)
        }
    }

}
