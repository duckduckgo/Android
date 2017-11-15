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
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.trackerdetection.AdBlockPlus
import com.duckduckgo.app.trackerdetection.TrackerDataProvider
import com.duckduckgo.app.trackerdetection.TrackerDetector
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class BrowserActivity : DuckDuckGoActivity() {

    @Inject lateinit var webViewClient: BrowserWebViewClient
    @Inject lateinit var viewModelFactory: BrowserViewModel.BrowserViewModelFactory

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        webViewClient.trackerDetector = buildTrackerDetector()
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

    private fun buildTrackerDetector(): TrackerDetector {
        val dataProvider = TrackerDataProvider(applicationContext)
        Timber.d("Data provider has preprocessed data? %s", dataProvider.hasProcessedData)
        val easylistClient = AdBlockPlus(dataProvider.easylist, dataProvider.hasProcessedData)
        val easyprivacyClient = AdBlockPlus(dataProvider.easyprivacy, dataProvider.hasProcessedData)
        if (!dataProvider.hasProcessedData) {
            dataProvider.saveProcessedEasylistData(easylistClient.getProcessedData())
            dataProvider.saveProcessedEasyprivacyData(easyprivacyClient.getProcessedData())
        }
        return TrackerDetector(easylistClient, easyprivacyClient)
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
