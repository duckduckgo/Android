/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.survey.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ActivityUserSurveyBinding
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.pixels.AppPixelName.SURVEY_SURVEY_DISMISSED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyViewModel.Command
import com.duckduckgo.app.survey.ui.SurveyViewModel.Command.*
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import javax.inject.Inject

class SurveyActivity : DuckDuckGoActivity() {

    private val viewModel: SurveyViewModel by bindViewModel()

    @Inject lateinit var pixel: Pixel

    private val binding: ActivityUserSurveyBinding by viewBinding()

    private val webView
        get() = binding.webView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configureListeners()

        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.cornflowerBlue))
        webView.webViewClient = SurveyWebViewClient()

        configureObservers()

        val lastCommand = viewModel.command.value
        if (lastCommand != null) {
            processCommand(lastCommand)
        } else {
            consumeIntentExtra()
        }
    }

    private fun consumeIntentExtra() {
        val survey = intent.getSerializableExtra(SURVEY_EXTRA) as Survey
        viewModel.start(survey)
    }

    private fun configureListeners() {
        binding.dismissButton.setOnClickListener { onSurveyDismissed() }
    }

    private fun configureObservers() {
        viewModel.command.observe(this, Observer { it?.let { command -> processCommand(command) } })
    }

    private fun processCommand(command: Command) {
        when (command) {
            is LoadSurvey -> loadSurvey(command.url)
            is ShowSurvey -> showSurvey()
            is ShowError -> showError()
            is Close -> finish()
        }
    }

    private fun loadSurvey(url: String) {
        binding.progress.show()
        webView.loadUrl(url)
    }

    private fun showSurvey() {
        binding.progress.gone()
        webView.show()
    }

    private fun showError() {
        binding.progress.gone()
        binding.errorView.show()
        destroyWebView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onSurveyDismissed()
        }
    }

    private fun onSurveyDismissed() {
        pixel.fire(SURVEY_SURVEY_DISMISSED)
        viewModel.onSurveyDismissed()
    }

    private fun destroyWebView() {
        webView.gone()
        binding.surveyActivityContainerViewGroup.removeView(webView)
        webView.destroy()
    }

    companion object {

        fun intent(context: Context, survey: Survey): Intent {
            val intent = Intent(context, SurveyActivity::class.java)
            intent.putExtra(SURVEY_EXTRA, survey)
            return intent
        }

        const val SURVEY_EXTRA = "SURVEY_EXTRA"
    }

    inner class SurveyWebViewClient : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            viewModel.onSurveyLoaded()
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (request.url.host == "duckduckgo.com") {
                runOnUiThread { viewModel.onSurveyCompleted() }
            }
            return null
        }

        @Suppress("OverridingDeprecatedMember")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String
        ) {
            viewModel.onSurveyFailedToLoad()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                viewModel.onSurveyFailedToLoad()
            }
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?
        ): Boolean {
            viewModel.onSurveyFailedToLoad()
            return true
        }
    }
}
