/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.FeedbackViewModel.Command
import com.duckduckgo.app.feedback.ui.FeedbackViewModel.ViewState
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.TextChangedWatcher
import kotlinx.android.synthetic.main.activity_feedback.*
import org.jetbrains.anko.longToast


class FeedbackActivity : DuckDuckGoActivity() {

    private val viewModel: FeedbackViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)
        configureListeners()
        configureObservers()

        if (savedInstanceState == null) {
            consumeIntentExtra()
        }
    }

    private fun consumeIntentExtra() {
        val brokenSite = intent.getBooleanExtra(BROKEN_SITE_EXTRA, false)
        if (brokenSite) {
            val url = intent.getStringExtra(URL_EXTRA)
            viewModel.setInitialBrokenSite(url)
        }
    }

    private fun configureListeners() {
        brokenSiteSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onBrokenSiteChanged(isChecked)
        }
        feedbackMessage.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onFeedbackMessageChanged(editable.toString())
            }
        })
        brokenSiteUrl.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onBrokenSiteUrlChanged(editable.toString())
            }
        })
        submitButton.setOnClickListener {
            viewModel.onSubmitPressed()
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            it?.let { processCommand(it) }
        })
        viewModel.viewState.observe(this, Observer<ViewState> {
            it?.let { render(it) }
        })
    }

    private fun processCommand(command: Command) {
        when (command) {
            Command.FocusUrl -> brokenSiteUrl.requestFocus()
            Command.FocusMessage -> feedbackMessage.requestFocus()
            Command.ConfirmAndFinish -> confirmAndFinish()
        }
    }

    private fun confirmAndFinish() {
        longToast(R.string.feedbackSubmitted)
        finishAfterTransition()
    }

    private fun render(viewState: ViewState) {
        val messageHint = if (viewState.isBrokenSite) R.string.feedbackBrokenSiteHint else R.string.feedbackMessageHint
        brokenSiteSwitch.isChecked = viewState.isBrokenSite
        brokenSiteUrl.isVisible = viewState.showUrl
        brokenSiteUrl.updateText(viewState.url ?: "")
        feedbackMessage.setHint(messageHint)
        submitButton.isEnabled = viewState.submitAllowed
    }

    private fun EditText.updateText(newText: String) {
        if (text.toString() != newText) {
            setText(newText)
        }
    }

    companion object {

        private const val BROKEN_SITE_EXTRA = "BROKEN_SITE_EXTRA"
        private const val URL_EXTRA = "URL_EXTRA"

        fun intent(context: Context, brokenSite: Boolean = false, url: String? = null): Intent {
            val intent = Intent(context, FeedbackActivity::class.java)
            intent.putExtra(BROKEN_SITE_EXTRA, brokenSite)
            if (url != null) {
                intent.putExtra(URL_EXTRA, url)
            }
            return intent
        }

    }
}
