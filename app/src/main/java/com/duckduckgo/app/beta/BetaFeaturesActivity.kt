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
import androidx.lifecycle.addRepeatingJob
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.ui.EmailProtectionActivity
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.settings.SettingsEmailLogoutDialog
import kotlinx.android.synthetic.main.activity_beta_features.emailSetting
import kotlinx.android.synthetic.main.include_toolbar.*

class BetaFeaturesActivity : DuckDuckGoActivity() {

    private val viewModel: BetaFeaturesViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beta_features)
        setupToolbar(toolbar)

        configureUiEventHandlers()
        observeViewModel()
    }

    private fun observeViewModel() {
        addRepeatingJob(Lifecycle.State.CREATED) {
            viewModel.loadInitialData()
        }

        viewModel.viewState.observe(
            this,
            { viewState ->
                viewState?.let {
                    setEmailSetting(it.emailSetting)
                }
            }
        )

        viewModel.command.observe(
            this,
            {
                processCommand(it)
            }
        )
    }

    private fun setEmailSetting(emailData: BetaFeaturesViewModel.EmailSetting) {
        when (emailData) {
            is BetaFeaturesViewModel.EmailSetting.EmailSettingOff -> {
                emailSetting.setSubtitle(getString(R.string.settingsEmailProtectionDisabled))
            }
            is BetaFeaturesViewModel.EmailSetting.EmailSettingOn -> {
                emailSetting.setSubtitle(getString(R.string.settingsEmailProtectionEnabledFor, emailData.emailAddress))
            }
        }
    }

    private fun configureUiEventHandlers() {
        emailSetting.setOnClickListener { viewModel.onEmailSettingClicked() }
    }

    private fun launchEmailDialog() {
        val dialog = SettingsEmailLogoutDialog.create()
        dialog.show(supportFragmentManager, EMAIL_DIALOG_TAG)
        dialog.onLogout = { viewModel.onEmailLogout() }
    }

    private fun launchEmailProtection() {
        val options = ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
        startActivity(EmailProtectionActivity.intent(this), options)
    }

    private fun processCommand(it: BetaFeaturesViewModel.Command) {
        when (it) {
            is BetaFeaturesViewModel.Command.LaunchEmailSignOut -> launchEmailDialog()
            is BetaFeaturesViewModel.Command.LaunchEmailSignIn -> launchEmailProtection()
        }
    }

    companion object {
        private const val EMAIL_DIALOG_TAG = "EMAIL_DIALOG_FRAGMENT"

        fun intent(context: Context): Intent {
            return Intent(context, BetaFeaturesActivity::class.java)
        }
    }

}
