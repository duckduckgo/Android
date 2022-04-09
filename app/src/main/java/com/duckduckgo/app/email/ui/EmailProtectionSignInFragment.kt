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

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentEmailProtectionSignInBinding
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.waitlist.email.WaitlistNotificationDialog
import com.duckduckgo.app.global.view.html
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class EmailProtectionSignInFragment : EmailProtectionFragment(R.layout.fragment_email_protection_sign_in) {

    private val viewModel by bindViewModel<EmailProtectionSignInViewModel>()

    private val binding: FragmentEmailProtectionSignInBinding by viewBinding()

    private val getNotificationSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            showNotificationDialog()
        }
    }

    private val readBlogSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.readBlogPost()
        }
    }

    override fun configureViewModelObservers() {
        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }.launchIn(lifecycleScope)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
    }

    override fun configureUi() {
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.inviteCodeButton.setOnClickListener { viewModel.haveAnInviteCode() }
        binding.duckAddressButton.setOnClickListener { viewModel.haveADuckAddress() }
        binding.waitListButton.setOnClickListener {
            it.isEnabled = false
            viewModel.joinTheWaitlist()
        }
        binding.getStartedButton.setOnClickListener { viewModel.getStarted() }
    }

    private fun render(signInViewState: EmailProtectionSignInViewModel.ViewState) {
        when (val state = signInViewState.waitlistState) {
            is AppEmailManager.WaitlistState.JoinedQueue -> renderJoinedQueue(state.notify)
            is AppEmailManager.WaitlistState.InBeta -> renderInBeta()
            is AppEmailManager.WaitlistState.NotJoinedQueue -> renderNotJoinedQueue()
        }
    }

    private fun renderErrorMessage() {
        binding.waitListButton.isEnabled = true
        Toast.makeText(context, R.string.emailProtectionErrorJoiningWaitlist, Toast.LENGTH_LONG).show()
    }

    private fun executeCommand(command: EmailProtectionSignInViewModel.Command) {
        when (command) {
            is EmailProtectionSignInViewModel.Command.OpenUrl -> openWebsite(command.url)
            is EmailProtectionSignInViewModel.Command.OpenUrlInBrowserTab -> openBrowserTab(command.url)
            is EmailProtectionSignInViewModel.Command.ShowErrorMessage -> renderErrorMessage()
            is EmailProtectionSignInViewModel.Command.ShowNotificationDialog -> showNotificationDialog()
        }
    }

    private fun showNotificationDialog() {
        activity?.supportFragmentManager?.let {
            val dialog = WaitlistNotificationDialog.create()
            dialog.show(it, NOTIFICATION_DIALOG_TAG)
            dialog.onNotifyClicked = { viewModel.onNotifyMeClicked() }
            dialog.onNoThanksClicked = { viewModel.onNoThanksClicked() }
            dialog.onDialogDismissed = { viewModel.onDialogDismissed() }
        }
    }

    private fun renderInBeta() {
        binding.headerImage.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_gift_large)
        binding.waitListButton.gone()
        binding.inviteCodeButton.gone()
        binding.getStartedButton.show()
        binding.duckAddressButton.show()
        binding.footerDescription.gone()
        binding.statusTitle.text = getString(R.string.emailProtectionStatusTitleInBeta)
        setClickableSpan(binding.emailPrivacyDescription, R.string.emailProtectionDescriptionInBeta, listOf(readBlogSpan))
    }

    private fun renderJoinedQueue(notify: Boolean) {
        binding.headerImage.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_list)
        binding.waitListButton.gone()
        binding.inviteCodeButton.show()
        binding.getStartedButton.gone()
        binding.duckAddressButton.show()
        binding.footerDescription.gone()
        binding.statusTitle.text = getString(R.string.emailProtectionStatusTitleJoined)
        if (notify) {
            setClickableSpan(binding.emailPrivacyDescription, R.string.emailProtectionDescriptionJoinedWithNotification, listOf(readBlogSpan))
        } else {
            setClickableSpan(
                binding.emailPrivacyDescription,
                R.string.emailProtectionDescriptionJoinedWithoutNotification,
                listOf(getNotificationSpan, readBlogSpan)
            )
        }
    }

    private fun renderNotJoinedQueue() {
        binding.headerImage.setImageResource(R.drawable.contact_us)
        binding.footerDescription.show()
        binding.waitListButton.show()
        binding.inviteCodeButton.show()
        binding.getStartedButton.gone()
        binding.duckAddressButton.show()
        binding.waitListButton.isEnabled = true
        binding.statusTitle.text = getString(R.string.emailProtectionStatusTitleJoin)
        setClickableSpan(binding.emailPrivacyDescription, R.string.emailProtectionDescriptionJoin, listOf(readBlogSpan))
    }

    private fun setClickableSpan(
        view: TextView,
        stringId: Int,
        span: List<DuckDuckGoClickableSpan>
    ) {
        context?.let {
            val htmlString = getString(stringId).html(it)
            val spannableString = SpannableStringBuilder(htmlString)
            val urlSpans = htmlString.getSpans(0, htmlString.length, URLSpan::class.java)
            urlSpans?.forEachIndexed { index, urlSpan ->
                spannableString.apply {
                    setSpan(
                        span[index],
                        spannableString.getSpanStart(urlSpan),
                        spannableString.getSpanEnd(urlSpan),
                        spannableString.getSpanFlags(urlSpan)
                    )
                    removeSpan(urlSpan)
                }
            }
            view.apply {
                text = spannableString
                movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    private fun openBrowserTab(url: String) {
        context?.let {
            startActivity(BrowserActivity.intent(it, url))
            activity?.finish()
        }
    }

    private fun openWebsite(url: String) {
        context?.let {
            startActivity(EmailWebViewActivity.intent(it, url))
        }
    }

    companion object {
        const val NOTIFICATION_DIALOG_TAG = "NOTIFICATION_DIALOG_FRAGMENT"

        fun instance(): EmailProtectionSignInFragment {
            return EmailProtectionSignInFragment()
        }
    }
}
