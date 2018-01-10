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
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.widget.TextView
import android.widget.Toast
import com.duckduckgo.app.browser.omnibar.OnBackKeyListener
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity.Companion.REQUEST_DASHBOARD
import com.duckduckgo.app.settings.SettingsActivity
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

        fun intent(context: Context, sharedText: String? = null): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(SHARED_TEXT_EXTRA, sharedText)
            return intent
        }

        private const val SHARED_TEXT_EXTRA = "SHARED_TEXT_EXTRA"
        private const val REQUEST_SETTINGS = 1001
    }

    private val privacyGradeMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.privacy_dashboard)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        viewModel.viewState.observe(this, Observer<BrowserViewModel.ViewState> {
            it?.let { render(it) }
        })

        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            it?.let { renderPrivacyGrade(it) }
        })

        viewModel.url.observe(this, Observer {
            it?.let { webView.loadUrl(it) }
        })

        viewModel.command.observe(this, Observer {
            when (it) {
                is BrowserViewModel.Command.Refresh -> webView.reload()
                is BrowserViewModel.Command.Navigate -> {
                    focusDummy.requestFocus()
                    webView.loadUrl(it.url)
                }
                is BrowserViewModel.Command.LandingPage -> finishActivityAnimated()
                is BrowserViewModel.Command.DialNumber -> {
                    val intent = Intent(Intent.ACTION_DIAL)
                    intent.data = Uri.parse("tel:${it.telephoneNumber}")
                    launchExternalActivity(intent)
                }
                is BrowserViewModel.Command.SendEmail -> {
                    val intent = Intent(Intent.ACTION_SENDTO)
                    intent.data = Uri.parse(it.emailAddress)
                    launchExternalActivity(intent)
                }
                is BrowserViewModel.Command.SendSms -> {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${it.telephoneNumber}"))
                    startActivity(intent)
                }
            }
        })

        configureToolbar()
        configureWebView()
        configureOmnibarTextInput()
        configureDummyViewTouchHandler()

        if (savedInstanceState == null) {
            consumeSharedTextExtra()
        }
    }

    private fun launchExternalActivity(intent: Intent) {
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.no_compatible_third_party_app_installed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun consumeSharedTextExtra() {
        val sharedText = intent.getStringExtra(SHARED_TEXT_EXTRA)
        if (sharedText != null) {
            viewModel.onSharedTextReceived(sharedText)
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

        if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
            omnibarTextInput.setText(viewState.omnibarText)
            appBarLayout.setExpanded(true, true)
        }

        pageLoadingIndicator.progress = viewState.progress

        when (viewState.showClearButton) {
            true -> showClearButton()
            false -> hideClearButton()
        }

        privacyGradeMenu?.isVisible = viewState.showPrivacyGrade
    }

    private fun showClearButton() {
        omnibarTextInput.post {
            clearOmnibarInputButton.show()
            omnibarTextInput.updatePadding(paddingEnd = 40.toPx())
        }
    }

    private fun hideClearButton() {
        omnibarTextInput.post {
            clearOmnibarInputButton.hide()
            omnibarTextInput.updatePadding(paddingEnd = 10.toPx())
        }
    }

    private fun renderPrivacyGrade(privacyGrade: PrivacyGrade?) {
        val resource = when (privacyGrade) {
            PrivacyGrade.A -> R.drawable.privacygrade_icon_a
            PrivacyGrade.B -> R.drawable.privacygrade_icon_b
            PrivacyGrade.C -> R.drawable.privacygrade_icon_c
            PrivacyGrade.D -> R.drawable.privacygrade_icon_d
            else -> R.drawable.privacygrade_icon_unknown
        }
        privacyGradeMenu?.icon = getDrawable(resource)
    }

    private fun shouldUpdateOmnibarTextInput(viewState: BrowserViewModel.ViewState, omnibarInput: String?) =
            viewState.omnibarText != null && !viewState.isEditing && omnibarTextInput.isDifferent(omnibarInput)

    private fun configureToolbar() {
        setSupportActionBar(toolbar)

        supportActionBar?.let {
            it.title = null
        }
    }

    private fun configureOmnibarTextInput() {
        omnibarTextInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus: Boolean ->
            viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), hasFocus)
        }

        omnibarTextInput.addTextChangedListener(object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), omnibarTextInput.hasFocus())
            }
        })

        omnibarTextInput.onBackKeyListener = object : OnBackKeyListener {
            override fun onBackKey(): Boolean {
                focusDummy.requestFocus()
                return viewModel.userDismissedKeyboard()
            }
        }

        clearOmnibarInputButton.setOnClickListener { omnibarTextInput.setText("") }
    }

    private fun userEnteredQuery() {
        viewModel.onUserSubmittedQuery(omnibarTextInput.text.toString())
        omnibarTextInput.hideKeyboard()
        focusDummy.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.webViewClient = webViewClient
        webView.webChromeClient = webChromeClient

        webView.settings.apply {
            javaScriptEnabled = true
            loadWithOverviewMode = true
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

        omnibarTextInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == IME_ACTION_DONE || keyEvent.keyCode == KEYCODE_ENTER) {
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
            R.id.settings_menu_item -> {
                launchSettingsView()
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
        startActivityForResult(PrivacyDashboardActivity.intent(this), REQUEST_DASHBOARD)
    }

    private fun launchSettingsView() {
        startActivityForResult(SettingsActivity.intent(this), REQUEST_SETTINGS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_DASHBOARD -> viewModel.receivedDashboardResult(resultCode)
            REQUEST_SETTINGS -> viewModel.receivedSettingsResult(resultCode)
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
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
        omnibarTextInput.text.clear()
        omnibarTextInput.hideKeyboard()
        webView.hide()
    }
}