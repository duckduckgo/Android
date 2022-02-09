/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.macos_api.MacOsWaitlistState.InBeta
import com.duckduckgo.macos_api.MacOsWaitlistState.JoinedWaitlist
import com.duckduckgo.macos_api.MacOsWaitlistState.NotJoinedQueue
import com.duckduckgo.macos_impl.R
import com.duckduckgo.macos_impl.databinding.ActivityMacosWaitlistBinding
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowErrorMessage
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.Command.ShowNotificationDialog
import com.duckduckgo.macos_impl.waitlist.ui.MacOsWaitlistViewModel.ViewState
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.android.synthetic.main.activity_macos_waitlist.view.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MacOsWaitlistActivity : DuckDuckGoActivity() {

    private val viewModel: MacOsWaitlistViewModel by bindViewModel()
    private val binding: ActivityMacosWaitlistBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }
            .launchIn(lifecycleScope)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }
            .launchIn(lifecycleScope)

        setContentView(binding.root)
        setupToolbar(toolbar)
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.waitListButton.setOnClickListener {
            it.isEnabled = false
            viewModel.joinTheWaitlist()
        }
        // binding.getStartedButton.setOnClickListener { viewModel.getStarted() }
    }

    private fun render(viewState: ViewState) {
        when (val state = viewState.waitlist) {
            is NotJoinedQueue -> renderNotJoinedQueue()
            is JoinedWaitlist -> renderJoinedQueue(state.notify)
            is InBeta -> renderInBeta(state.inviteCode)
        }
    }

    private fun executeCommand(command: Command) {
        when (command) {
            is ShowErrorMessage -> renderErrorMessage()
            is ShowNotificationDialog -> showNotificationDialog()
            // is ShowOnboarding -> showOnboarding()
            // is EnterInviteCode -> openRedeemCode()
        }
    }

    private fun renderInBeta(inviteCode: String) {
        binding.headerImage.setImageResource(R.drawable.ic_donations_large)
        binding.statusTitle.text = getString(R.string.macos_waitlist_code_title)
        binding.waitlistDescription.text = getText(R.string.macos_waitlist_code_description)
        binding.waitListButton.gone()
        binding.footerDescription.gone()
        binding.notifyMeButton.gone()
        binding.codeFrame.show()
        binding.codeFrame.inviteCode.text = inviteCode
    }

    private fun renderJoinedQueue(notify: Boolean) {
        binding.waitListButton.gone()
        binding.footerDescription.gone()
        binding.codeFrame.gone()
        binding.headerImage.setImageResource(R.drawable.ic_list)
        binding.statusTitle.text = getString(R.string.macos_waitlist_on_the_list_title)
        if (notify) {
            binding.waitlistDescription.text = getText(R.string.macos_waitlist_on_the_list_notification)
            binding.notifyMeButton.gone()
        } else {
            binding.waitlistDescription.text = getText(R.string.macos_waitlist_on_the_list_no_notification)
            binding.notifyMeButton.show()
        }
    }

    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(R.drawable.ic_privacy_simplified)
        binding.waitListButton.show()
        binding.footerDescription.show()
        binding.notifyMeButton.gone()
        binding.codeFrame.gone()
        binding.waitlistDescription.text = getText(R.string.macos_waitlist_description)
    }

    private fun renderErrorMessage() {
        binding.waitListButton.isEnabled = true
        Toast.makeText(this, R.string.macos_join_waitlist, Toast.LENGTH_LONG).show()
    }

    private fun showNotificationDialog() {
        supportFragmentManager.let {
            val dialog = MacOsWaitlistNotificationDialog.create().apply {
                onNotifyClicked = { viewModel.onNotifyMeClicked() }
                onNoThanksClicked = { viewModel.onNoThanksClicked() }
                onDialogDismissed = { viewModel.onDialogDismissed() }
            }
            dialog.show(it, NOTIFICATION_DIALOG_TAG)
        }
    }

    companion object {
        const val NOTIFICATION_DIALOG_TAG = "NOTIFICATION_DIALOG_FRAGMENT"

        fun intent(context: Context): Intent {
            return Intent(context, MacOsWaitlistActivity::class.java)
        }
    }
}
