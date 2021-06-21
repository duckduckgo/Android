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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentEmailProtectionSignOutBinding
import com.duckduckgo.app.global.view.NonUnderlinedClickableSpan
import com.duckduckgo.app.global.view.html
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EmailProtectionSignOutFragment(val emailAddress: String) : EmailProtectionFragment() {

    private val viewModel by bindViewModel<EmailProtectionSignOutViewModel>()

    private var _binding: FragmentEmailProtectionSignOutBinding? = null
    private val binding get() = _binding!!

    private val contactUsSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:support@duck.com")
            openExternalApp(intent)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEmailProtectionSignOutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun configureViewModelObservers() {
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
    }

    override fun configureUi() {
        configureUiEventHandlers()
        setClickableSpan()
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

        fun instance(emailAddress: String): EmailProtectionSignOutFragment {
            return EmailProtectionSignOutFragment(emailAddress)
        }
    }
}
