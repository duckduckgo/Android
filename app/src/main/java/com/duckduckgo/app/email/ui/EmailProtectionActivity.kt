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
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.AppEmailManager
import com.duckduckgo.app.email.waitlist.WaitlistNotificationDialog
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.NonUnderlinedClickableSpan
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.show
import com.google.android.material.textview.MaterialTextView
import kotlinx.android.synthetic.main.activity_email_protection.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class EmailProtectionActivity : DuckDuckGoActivity() {

    private val viewModel: EmailProtectionViewModel by bindViewModel()

    override fun onStart() {
        super.onStart()

        viewModel.viewState.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { render(it) }.launchIn(lifecycleScope)
        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_protection)
        setupToolbar(toolbar)
        configureUiEventHandlers()
        configureClickableLink()
    }

    private fun configureUiEventHandlers() {
        inviteCodeButton.setOnClickListener { viewModel.haveAnInviteCode() }
        waitListButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.joinTheWaitlist()
            }
        }
        getStartedButton.setOnClickListener { viewModel.haveAnInviteCode() }
    }

    private fun render(viewState: EmailProtectionViewModel.ViewState) {
        when (viewState.waitlistState) {
            is AppEmailManager.WaitlistState.JoinedQueue -> renderJoinedQueue()
            is AppEmailManager.WaitlistState.InBeta -> renderInBeta()
            is AppEmailManager.WaitlistState.NotJoinedQueue -> renderNotJoinedQueue()
        }
    }

    private fun renderErrorMessage() {
        Toast.makeText(applicationContext, R.string.emailProtectionErrorJoiningWaitlist, Toast.LENGTH_LONG).show()
    }

    private fun executeCommand(command: EmailProtectionViewModel.Command) {
        when (command) {
            is EmailProtectionViewModel.Command.OpenUrl -> openWebsite(command.url, command.openInBrowser)
            is EmailProtectionViewModel.Command.ShowErrorMessage -> renderErrorMessage()
            is EmailProtectionViewModel.Command.ShowNotificationDialog -> showNotificationDialog()
        }
    }

    private fun showNotificationDialog() {
        val dialog = WaitlistNotificationDialog.create()
        dialog.show(supportFragmentManager, NOTIFICATION_DIALOG_TAG)
    }

    private fun renderInBeta() {
        headerImage.setImageResource(R.drawable.contact_us)
        waitListButton.gone()
        inviteCodeButton.gone()
        getStartedButton.show()
        statusTitle.text = getString(R.string.emailProtectionStatusTitleInBeta)
        setClickableSpan(emailPrivacyDescription, R.string.emailProtectionDescriptionInBeta, listOf(readBlogSpan))
    }

    private fun renderJoinedQueue() {
        headerImage.setImageResource(R.drawable.we_hatched)
        waitListButton.gone()
        inviteCodeButton.show()
        getStartedButton.gone()
        statusTitle.text = getString(R.string.emailProtectionStatusTitleJoined)
        setClickableSpan(emailPrivacyDescription, R.string.emailProtectionDescriptionJoined, listOf(readBlogSpan))
    }

    private fun renderNotJoinedQueue() {
        headerImage.setImageResource(R.drawable.contact_us)
        waitListButton.show()
        inviteCodeButton.show()
        getStartedButton.gone()
        statusTitle.text = getString(R.string.emailProtectionStatusTitleJoin)
        setClickableSpan(emailPrivacyDescription, R.string.emailProtectionDescriptionJoin, listOf(readBlogSpan))
    }

    private val readBlogSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.readBlogPost()
        }
    }

    private val privacyGuaranteeSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.readPrivacyGuarantees()
        }
    }

    private val signInSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            viewModel.haveADuckAddress()
        }
    }

    private fun configureClickableLink() {
        setClickableSpan(footerDescription, R.string.emailProtectionFooterDescription, listOf(privacyGuaranteeSpan, signInSpan))
    }

    private fun setClickableSpan(view: MaterialTextView, stringId: Int, spans: List<NonUnderlinedClickableSpan>) {
        val htmlString = getString(stringId).html(this)
        val spannableString = SpannableStringBuilder(htmlString)
        val urlSpans = htmlString.getSpans(0, htmlString.length, URLSpan::class.java)
        urlSpans?.forEachIndexed { index, urlSpan ->
            spannableString.apply {
                setSpan(
                    spans[index],
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

    private fun openWebsite(url: String, openInBrowser: Boolean) {
        if (openInBrowser) {
            startActivity(BrowserActivity.intent(this, url))
        } else {
            startActivity(EmailFaqActivity.intent(this, url))
        }
    }

    companion object {
        const val NOTIFICATION_DIALOG_TAG = "NOTIFICATION_DIALOG_FRAGMENT"

        fun intent(context: Context): Intent {
            return Intent(context, EmailProtectionActivity::class.java)
        }
    }
}
