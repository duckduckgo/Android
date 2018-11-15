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

import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.model.Survey
import com.duckduckgo.app.feedback.ui.UserSurveyViewModel.Command
import com.duckduckgo.app.feedback.ui.UserSurveyViewModel.Command.Close
import com.duckduckgo.app.feedback.ui.UserSurveyViewModel.Command.LoadSurvey
import com.duckduckgo.app.global.DuckDuckGoActivity
import kotlinx.android.synthetic.main.activity_user_survey.*


class UserSurveyActivity : DuckDuckGoActivity() {

    private val viewModel: UserSurveyViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_survey)
        configureListeners()

        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.cornflowerBlue))
        webView.webViewClient = SurveyWebChromeClient()

        if (savedInstanceState == null) {
            configureObservers()
            consumeIntentExtra()
        }
    }

    private fun consumeIntentExtra() {
        val survey = intent.getSerializableExtra(SURVEY_EXTRA) as Survey
        viewModel.start(survey)
    }

    private fun configureListeners() {
        dismissButton.setOnClickListener { _ ->
            viewModel.onSurveyDismissed()
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer {
            it?.let { processCommand(it) }
        })
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LoadSurvey -> webView.loadUrl(command.url)
            is Close -> finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    companion object {

        fun intent(context: Context, survey: Survey): Intent {
            val intent = Intent(context, UserSurveyActivity::class.java)
            intent.putExtra(SURVEY_EXTRA, survey)
            return intent
        }

        const val SURVEY_EXTRA = "SURVEY_EXTRA"
    }

    inner class SurveyWebChromeClient : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (request.isForMainFrame && request.url.host == "www.duckduckgo.com") {
                runOnUiThread {
                    viewModel.onSurveyCompleted()
                }
            }
            return null
        }
    }

}


