/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import dagger.android.AndroidInjection
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class BrowserActivity : AppCompatActivity() {

    @Inject lateinit var requestRewriter: DuckDuckGoRequestRewriter
    @Inject lateinit var webViewClient: BrowserWebViewClient
    @Inject lateinit var viewModelFactory: BrowserViewModelFactory

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel.query.observe(this, Observer {
            if (savedInstanceState == null) {
                Timber.v("Webview loading url $it")
                webView.loadUrl(it)
            }
        })

        loadUrlButton.setOnClickListener({
            userEnteredQuery()
        })

        configureWebView()
    }

    private fun userEnteredQuery() {
        viewModel.onQueryEntered(urlInput.text.toString())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.webViewClient = webViewClient
        webView.settings.javaScriptEnabled = true
        refreshWebViewButton.setOnClickListener({ webView.reload() })
        navigateBackButton.setOnClickListener({ webView.goBack() })
        navigateForward.setOnClickListener({ webView.goForward() })

        urlInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                userEnteredQuery()
                return@OnEditorActionListener true
            }
            false
        })
    }

    override fun onSaveInstanceState(bundle: Bundle?) {
        super.onSaveInstanceState(bundle)
        webView.saveState(bundle)
    }

    override fun onRestoreInstanceState(bundle: Bundle?) {
        super.onRestoreInstanceState(bundle)
        webView.restoreState(bundle)
    }
}
