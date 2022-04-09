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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentEmailProtectionSignOutBinding
import com.duckduckgo.app.global.view.html
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.spans.DuckDuckGoClickableSpan
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class EmailProtectionSignOutFragment() : EmailProtectionFragment(R.layout.fragment_email_protection_sign_out) {

    private val viewModel by bindViewModel<EmailProtectionSignOutViewModel>()

    private val binding: FragmentEmailProtectionSignOutBinding by viewBinding()

    private val contactUsSpan = object : DuckDuckGoClickableSpan() {
        override fun onClick(widget: View) {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:support@duck.com")
            openExternalApp(intent)
        }
    }

    override fun configureViewModelObservers() {
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
    }

    override fun configureUi() {
        configureUiEventHandlers()
        setClickableSpan()

        val emailAddress = requireArguments()[EMAIL_ADDRESS_EXTRA] as String
        binding.primaryAddress.setSubtitle(emailAddress)
    }

    private fun openExternalApp(intent: Intent) {
        val title = getString(R.string.openExternalApp)
        val intentChooser = Intent.createChooser(intent, title)
        startActivity(intentChooser)
    }

    private fun setClickableSpan() {
        context?.let {
            val htmlString = getString(R.string.emailProtectionSignOutFooter).html(it)
            val spannableString = SpannableStringBuilder(htmlString)
            val urlSpans = htmlString.getSpans(0, htmlString.length, URLSpan::class.java)
            urlSpans?.forEach { urlSpan ->
                spannableString.apply {
                    setSpan(
                        contactUsSpan,
                        spannableString.getSpanStart(urlSpan),
                        spannableString.getSpanEnd(urlSpan),
                        spannableString.getSpanFlags(urlSpan)
                    )
                    removeSpan(urlSpan)
                }
            }
            binding.footerDescription.apply {
                text = spannableString
                movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    private fun configureUiEventHandlers() {
        binding.signOutButton.setOnClickListener { viewModel.onSignOutButtonClicked() }
    }

    private fun executeCommand(command: EmailProtectionSignOutViewModel.Command) {
        when (command) {
            is EmailProtectionSignOutViewModel.Command.SignOut -> launchEmailSignOutDialog()
        }
    }

    private fun launchEmailSignOutDialog() {
        activity?.supportFragmentManager?.let {
            val dialog = EmailLogoutDialog.create()
            dialog.show(it, SIGN_OUT_DIALOG_TAG)
            dialog.onLogout = { viewModel.onEmailLogoutConfirmed() }
        }
    }

    companion object {
        private const val SIGN_OUT_DIALOG_TAG = "SIGN_OUT_DIALOG_TAG"
        private const val EMAIL_ADDRESS_EXTRA = "EMAIL_ADDRESS_EXTRA"

        fun instance(emailAddress: String): EmailProtectionSignOutFragment {
            val fragment = EmailProtectionSignOutFragment()
            fragment.arguments = Bundle().also {
                it.putString(EMAIL_ADDRESS_EXTRA, emailAddress)
            }
            return fragment
        }
    }
}
