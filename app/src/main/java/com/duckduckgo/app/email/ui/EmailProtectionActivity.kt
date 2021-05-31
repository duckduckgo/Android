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

package com.duckduckgo.app.email.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.show
import kotlinx.android.synthetic.main.content_email_protection.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class EmailProtectionActivity : DuckDuckGoActivity() {

    private val viewModel: EmailProtectionViewModel by bindViewModel()

    override fun onStart() {
        super.onStart()

        viewModel.viewFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }.launchIn(lifecycleScope)
        viewModel.commandsFlow.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_protection)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        duckAddressButton.setOnClickListener { viewModel.haveDuckAddress() }
        inviteCodeButton.setOnClickListener { viewModel.haveInviteCode() }
        waitListButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.joinWaitlist()
            }
        }
        blogPostButton.setOnClickListener { viewModel.readBlogPost() }
        readPrivacyButton.setOnClickListener { viewModel.readPrivacyGuarantees() }
    }

    private fun render(uiState: EmailProtectionViewModel.UiState) {
        when (uiState) {
            is EmailProtectionViewModel.UiState.InBeta -> renderInBeta(uiState.code)
            is EmailProtectionViewModel.UiState.JoinedQueue -> renderJoinedQueue(uiState.timestamp)
            is EmailProtectionViewModel.UiState.NotJoinedQueue -> renderNotJoinedQueue()
        }
    }

    private fun renderErrorMessage(error: String) {
        Toast.makeText(applicationContext, error, Toast.LENGTH_LONG).show()
    }

    private fun executeCommand(command: EmailProtectionViewModel.Command) {
        when (command) {
            is EmailProtectionViewModel.Command.OpenUrl -> {
                openWebsite(command.url)
            }
            is EmailProtectionViewModel.Command.ShowErrorMessage -> renderErrorMessage(command.error)
        }
    }

    private fun renderInBeta(code: String) {
        waitListButton.gone()
        inLineTitle.show()
        inLineTitle.text = getString(R.string.emailProtectionCodeTitle)
        inLineDescription.show()
        inLineDescription.text = getString(R.string.emailProtectionCodeDescription, code)
    }

    private fun renderJoinedQueue(timestamp: String) {
        waitListButton.gone()
        inLineTitle.show()
        inLineTitle.text = getString(R.string.emailProtectionLineTitle)
        inLineDescription.show()
        inLineDescription.text = getString(R.string.emailProtectionLineDescription, timestamp)
    }

    private fun renderNotJoinedQueue() {
        waitListButton.show()
        inLineTitle.gone()
        inLineDescription.show()
        inLineDescription.text = getString(R.string.emailProtectionJoiningDescription)
    }

    private fun openWebsite(url: String) {
        startActivity(BrowserActivity.intent(this, url))
        finish()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, EmailProtectionActivity::class.java)
        }
    }
}
