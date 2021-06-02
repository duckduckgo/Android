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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.ui.EmailProtectionActivity
import com.duckduckgo.app.email.ui.EmailProtectionSignOutActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.activity_beta_features.emailSetting
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class BetaFeaturesActivity : DuckDuckGoActivity() {

    private val viewModel: BetaFeaturesViewModel by bindViewModel()

    override fun onStart() {
        super.onStart()
        viewModel.viewFlow.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).onEach { setEmailSetting(it.emailState) }.launchIn(lifecycleScope)
        viewModel.commandsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { processCommand(it) }.launchIn(lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beta_features)
        setupToolbar(toolbar)

        configureUiEventHandlers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    private fun setEmailSetting(emailData: BetaFeaturesViewModel.EmailState) {
        when (emailData) {
            is BetaFeaturesViewModel.EmailState.Disabled -> emailSetting.setSubtitle(getString(R.string.betaFeaturesEmailProtectionSubtitleDisabled))
            is BetaFeaturesViewModel.EmailState.Enabled -> emailSetting.setSubtitle(getString(R.string.betaFeaturesEmailProtectionSubtitleEnabled))
            is BetaFeaturesViewModel.EmailState.JoinWaitlist -> emailSetting.setSubtitle(getString(R.string.betaFeaturesEmailProtectionSubtitleWaitlist))
        }
    }

    private fun configureUiEventHandlers() {
        emailSetting.setOnClickListener { viewModel.onEmailSettingClicked() }
    }

    private fun launchEmailProtectionSignOut(email: String) {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionSignOutActivity.intent(this, email), options)
    }

    private fun launchEmailProtection() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionActivity.intent(this), options)
    }

    private fun processCommand(it: BetaFeaturesViewModel.Command) {
        when (it) {
            is BetaFeaturesViewModel.Command.LaunchEmailSignOut -> launchEmailProtectionSignOut(it.emailAddress)
            is BetaFeaturesViewModel.Command.LaunchEmailSignIn -> launchEmailProtection()
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, BetaFeaturesActivity::class.java)
        }
    }

}
