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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.widget.TextView
import com.duckduckgo.app.browser.BrowserViewModel.NavigationCommand.LANDING_PAGE
import com.duckduckgo.app.browser.omnibar.OnBackKeyListener
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacydashboard.PrivacyDashboardActivity
import kotlinx.android.synthetic.main.activity_browser.*
import javax.inject.Inject

class BrowserActivity : DuckDuckGoActivity() {

    @Inject lateinit var webViewClient: BrowserWebViewClient
    @Inject lateinit var webChromeClient: BrowserChromeClient
    @Inject lateinit var viewModelFactory: ViewModelFactory

    private var acceptingRenderUpdates = true

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, BrowserActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        viewModel.viewState.observe(this, Observer<BrowserViewModel.ViewState> {
            it?.let { render(it) }
        })

        viewModel.query.observe(this, Observer {
            it?.let { webView.loadUrl(it) }
        })

        viewModel.navigation.observe(this, Observer {
            if (it == LANDING_PAGE) {
                finishActivityAnimated()
            }
        })

        configureToolbar()
        configureWebView()
        configureUrlInput()
        configureDummyViewTouchHandler()

        if (shouldShowKeyboard()) {
            urlInput.showKeyboard()
        }
    }

    private fun render(viewState: BrowserViewModel.ViewState) {

        if (!acceptingRenderUpdates) return

        when (viewState.browserShowing) {
            true -> webView.show()
            false -> webView.hide()
        }

        when (viewState.isLoading) {
            true -> pageLoadingIndicator.show()
            false -> pageLoadingIndicator.hide()
        }

        if (shouldUpdateUrl(viewState, viewState.url)) {
            urlInput.setText(viewState.url)
            appBarLayout.setExpanded(true, true)
        }

        pageLoadingIndicator.progress = viewState.progress

        when (viewState.showClearButton) {
            true -> showClearButton()
            false -> hideClearButton()
        }
    }

    private fun showClearButton() {
        urlInput.post {
            clearUrlButton.show()
            urlInput.updatePadding(paddingEnd = 40.toPx())
        }
    }

    private fun hideClearButton() {
        urlInput.post {
            clearUrlButton.hide()
            urlInput.updatePadding(paddingEnd = 10.toPx())
        }
    }

    private fun shouldUpdateUrl(viewState: BrowserViewModel.ViewState, url: String?) =
            viewState.url != null && !viewState.isEditing && urlInput.isDifferent(url)

    private fun configureToolbar() {
        setSupportActionBar(toolbar)

        supportActionBar?.let {
            it.title = null
        }
    }

    private fun configureUrlInput() {
        urlInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus: Boolean ->
            viewModel.onUrlInputStateChanged(urlInput.text.toString(), hasFocus)
        }

        urlInput.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onUrlInputStateChanged(urlInput.text.toString(), urlInput.hasFocus())
            }
        })

        urlInput.onBackKeyListener = object : OnBackKeyListener {
            override fun onBackKey(): Boolean {
                focusDummy.requestFocus()
                return viewModel.userDismissedKeyboard()
            }
        }

        clearUrlButton.setOnClickListener { urlInput.setText("") }
    }

    private fun userEnteredQuery() {
        viewModel.onUserSubmittedQuery(urlInput.text.toString())
        urlInput.hideKeyboard()
        focusDummy.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.webViewClient = webViewClient
        webView.webChromeClient = webChromeClient

        webView.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(true)
        }

        webView.setOnTouchListener { _, _ ->
            focusDummy.requestFocus()
            false
        }

        viewModel.registerWebViewListener(webViewClient, webChromeClient)

        urlInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                userEnteredQuery()
                return@OnEditorActionListener true
            }
            false
        })
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        webView.saveState(bundle)
        super.onSaveInstanceState(bundle)
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        webView.restoreState(bundle)
    }

    /**
     * Dummy view captures touches on areas outside of the toolbar, before the WebView is visible
     */
    private fun configureDummyViewTouchHandler() {
        focusDummy.setOnTouchListener { _, _ ->
            finishActivityAnimated()
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_browser_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.privacy_dashboard -> {
                launchPrivacyDashboard()
                return true
            }
            R.id.refresh_menu_item -> {
                webView.reload()
                return true
            }
            R.id.back_menu_item -> {
                webView.goBack()
                return true
            }
            R.id.forward_menu_item -> {
                webView.goForward()
                return true
            }
        }
        return false
    }

    private fun finishActivityAnimated() {
        clearViewPriorToAnimation()
        supportFinishAfterTransition()
    }

    private fun launchPrivacyDashboard() {
        startActivity(PrivacyDashboardActivity.intent(this))
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        clearViewPriorToAnimation()
        super.onBackPressed()
    }

    private fun clearViewPriorToAnimation() {
        acceptingRenderUpdates = false
        urlInput.text.clear()
        urlInput.hideKeyboard()
        webView.hide()
    }

    private fun shouldShowKeyboard(): Boolean =
            viewModel.viewState.value?.browserShowing ?: false ?: true
}
