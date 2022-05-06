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

package com.duckduckgo.app.waitlist.trackerprotection.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.vpn.R as VpnR
import com.duckduckgo.app.browser.databinding.ActivityAppTpWaitlistBinding
import com.duckduckgo.app.browser.webview.WebViewActivity
import com.duckduckgo.app.email.ui.EmailProtectionSignInFragment
import com.duckduckgo.app.waitlist.email.WaitlistNotificationDialog
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.mobile.android.ui.view.addClickableLink
import com.duckduckgo.mobile.android.ui.view.addClickableSpan
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnOnboardingActivity
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ActivityScope::class)
class AppTPWaitlistActivity : DuckDuckGoActivity() {

    private val viewModel: AppTPWaitlistViewModel by bindViewModel()
    private val binding: ActivityAppTpWaitlistBinding by viewBinding()

    private val toolbar
        get() = binding.includeToolbar.toolbar

    private val getNotificationSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            showNotificationDialog()
        }
    }

    private val readBlogSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.learnMore()
        }
    }

    private val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        viewModel.onCodeRedeemed(result.resultCode)
    }

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
        binding.inviteCodeButton.setOnClickListener { viewModel.haveAnInviteCode() }
        binding.footerInviteCodeButton.setOnClickListener { viewModel.haveAnInviteCode() }
        binding.waitListButton.setOnClickListener {
            it.isEnabled = false
            viewModel.joinTheWaitlist()
        }
        binding.getStartedButton.setOnClickListener { viewModel.getStarted() }
    }

    private fun render(viewState: AppTPWaitlistViewModel.ViewState) {
        when (val state = viewState.waitlist) {
            is WaitlistState.NotJoinedQueue -> renderNotJoinedQueue()
            is WaitlistState.JoinedWaitlist -> renderJoinedQueue(state.notify)
            is WaitlistState.InBeta -> renderInBeta()
            is WaitlistState.CodeRedeemed -> renderCodeRedeemed()
        }
    }

    private fun executeCommand(command: AppTPWaitlistViewModel.Command) {
        when (command) {
            is AppTPWaitlistViewModel.Command.LaunchBetaInstructions -> openWebsite()
            is AppTPWaitlistViewModel.Command.ShowErrorMessage -> renderErrorMessage()
            is AppTPWaitlistViewModel.Command.ShowNotificationDialog -> showNotificationDialog()
            is AppTPWaitlistViewModel.Command.ShowOnboarding -> showOnboarding()
            is AppTPWaitlistViewModel.Command.EnterInviteCode -> openRedeemCode()
        }
    }

    private fun renderInBeta() {
        binding.headerImage.setImageResource(R.drawable.ic_apptp_icon)
        binding.statusTitle.text = getString(VpnR.string.atp_WaitlistStatusInBeta)
        binding.waitListButton.gone()
        binding.getStartedButton.show()
        binding.inviteCodeButton.gone()
        binding.footerInviteCodeButton.gone()
        binding.footerDescription.gone()
        binding.appTPDescription.addClickableLink("beta_link", getText(VpnR.string.atp_WaitlistInBetaDescription)) {
            viewModel.learnMore()
        }
    }

    private fun renderJoinedQueue(notify: Boolean) {
        binding.headerImage.setImageResource(R.drawable.we_hatched)
        binding.waitListButton.gone()
        binding.inviteCodeButton.gone()
        binding.getStartedButton.gone()
        binding.footerDescription.gone()
        binding.footerInviteCodeButton.show()
        binding.statusTitle.text = getString(VpnR.string.atp_WaitlistStatusJoined)
        if (notify) {
            binding.appTPDescription.addClickableSpan(
                getText(VpnR.string.atp_WaitlistJoinedWithNotification),
                listOf(Pair("beta_link", readBlogSpan))
            )
        } else {
            binding.appTPDescription.addClickableSpan(
                getText(VpnR.string.atp_WaitlistJoinedWithoutNotification),
                listOf(Pair("notify_me_link", getNotificationSpan), Pair("beta_link", readBlogSpan))
            )
        }
    }

    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(R.drawable.ic_apptp_icon)
        binding.waitListButton.show()
        binding.getStartedButton.gone()
        binding.inviteCodeButton.show()
        binding.footerInviteCodeButton.gone()
        binding.appTPDescription.addClickableLink("beta_link", getText(VpnR.string.atp_WaitlistDescription)) {
            viewModel.learnMore()
        }
    }

    private fun renderCodeRedeemed() {
        binding.statusTitle.text = getString(VpnR.string.atp_WaitlistRedeemedCodeStatus)
        binding.headerImage.setImageResource(R.drawable.ic_dragon)
        binding.waitListButton.gone()
        binding.getStartedButton.show()
        binding.inviteCodeButton.gone()
        binding.footerInviteCodeButton.gone()
        binding.footerDescription.gone()
        binding.appTPDescription.addClickableLink("beta_link", getText(VpnR.string.atp_WaitlistInBetaDescription)) {
            viewModel.learnMore()
        }
    }

    private fun renderErrorMessage() {
        binding.waitListButton.isEnabled = true
        Toast.makeText(this, VpnR.string.atp_WaitlistErrorJoining, Toast.LENGTH_LONG).show()
    }

    private fun showNotificationDialog() {
        supportFragmentManager.let {
            val dialog = WaitlistNotificationDialog.create().apply {
                onNotifyClicked = { viewModel.onNotifyMeClicked() }
                onNoThanksClicked = { viewModel.onNoThanksClicked() }
                onDialogDismissed = { viewModel.onDialogDismissed() }
            }
            dialog.show(it, EmailProtectionSignInFragment.NOTIFICATION_DIALOG_TAG)
        }
    }

    private fun openRedeemCode() {
        startForResult.launch(AppTPWaitlistRedeemCodeActivity.intent(this))
    }

    private fun showOnboarding() {
        startActivity(VpnOnboardingActivity.intent(this))
        finish()
    }

    private fun openWebsite() {
        startActivity(WebViewActivity.intent(this, getString(VpnR.string.atp_WaitlistBetaBlogPost), getString(VpnR.string.atp_WaitlistActivityTitle)))
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, AppTPWaitlistActivity::class.java)
        }
    }
}
