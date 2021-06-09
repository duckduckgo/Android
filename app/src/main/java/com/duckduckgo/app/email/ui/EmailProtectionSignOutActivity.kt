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
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.flowWithLifecycle
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.NonUnderlinedClickableSpan
import com.duckduckgo.app.global.view.html
import kotlinx.android.synthetic.main.activity_email_sign_out_protection.*
import kotlinx.android.synthetic.main.include_toolbar.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EmailProtectionSignOutActivity : DuckDuckGoActivity() {

    private val viewModel: EmailProtectionSignOutViewModel by bindViewModel()

    private val contactUsSpan = object : NonUnderlinedClickableSpan() {
        override fun onClick(widget: View) {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:support@duck.com")
            openExternalApp(intent)
        }
    }

    private fun openExternalApp(intent: Intent) {
        val title = getString(R.string.openExternalApp)
        val intentChooser = Intent.createChooser(intent, title)
        startActivity(intentChooser)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_sign_out_protection)

        viewModel.commands.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).onEach { executeCommand(it) }.launchIn(lifecycleScope)
        setupToolbar(toolbar)
        configureUiEventHandlers()
        configureUi()
    }

    private fun configureUi() {
        setClickableSpan()
        val primaryEmail = intent.getStringExtra(EMAIL_EXTRA)
        if (primaryEmail != null) {
            primaryAddress.setSubtitle(primaryEmail)
        } else {
            finish()
        }
    }

    private fun setClickableSpan() {
        val htmlString = getString(R.string.emailProtectionSignOutFooter).html(this)
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
        footerDescription.apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun configureUiEventHandlers() {
        signOutButton.setOnClickListener { viewModel.signOut() }
    }

    private fun executeCommand(command: EmailProtectionSignOutViewModel.Command) {
        when (command) {
            is EmailProtectionSignOutViewModel.Command.SignOut -> launchEmailSignOutDialog()
            is EmailProtectionSignOutViewModel.Command.CloseScreen -> finish()
        }
    }

    private fun launchEmailSignOutDialog() {
        val dialog = EmailLogoutDialog.create()
        dialog.show(supportFragmentManager, SIGN_OUT_DIALOG_TAG)
        dialog.onLogout = { viewModel.onEmailLogout() }
    }

    companion object {
        private const val EMAIL_EXTRA = "EMAIL_EXTRA"
        private const val SIGN_OUT_DIALOG_TAG = "SIGN_OUT_DIALOG_TAG"

        fun intent(context: Context, emailExtra: String): Intent {
            val intent = Intent(context, EmailProtectionSignOutActivity::class.java)
            intent.putExtra(EMAIL_EXTRA, emailExtra)
            return intent
        }
    }
}
