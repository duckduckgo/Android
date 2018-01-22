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
import android.app.DownloadManager
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import android.webkit.CookieManager
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.widget.TextView
import android.widget.Toast
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.autoComplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.omnibar.OnBackKeyListener
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.renderer.icon
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity
import com.duckduckgo.app.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_browser.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider


class BrowserActivity : DuckDuckGoActivity() {

    @Inject lateinit var webViewClient: BrowserWebViewClient
    @Inject lateinit var webChromeClient: BrowserChromeClient
    @Inject lateinit var viewModelFactory: ViewModelFactory
    @Inject lateinit var cookieManagerProvider: Provider<CookieManager>

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private var acceptingRenderUpdates = true

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    private val privacyGradeMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.privacy_dashboard_menu_item)

    private val fireMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.fire_menu_item)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        popupMenu = BrowserPopupMenu(layoutInflater)

        viewModel.viewState.observe(this, Observer<BrowserViewModel.ViewState> {
            it?.let { render(it) }
        })

        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            it?.let {
                privacyGradeMenu?.icon = getDrawable(it.icon())
            }
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
        configureAutoComplete()

        if (savedInstanceState == null) {
            consumeSharedTextExtra()
        }
    }

    private fun configureAutoComplete() {
        autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(this)
        autoCompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
                immediateSearchClickListener = {
                    userEnteredQuery(it.phrase)
                },
                editableSearchClickListener = {
                    viewModel.onUserSelectedToEditQuery(it.phrase)
                }
        )
        autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun consumeSharedTextExtra() {
        val sharedText = intent.getStringExtra(SHARED_TEXT_EXTRA)
        if (sharedText != null) {
            viewModel.onSharedTextReceived(sharedText)
        }
    }

    private fun render(viewState: BrowserViewModel.ViewState) {

        Timber.v("Rendering view state: $viewState")

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

            // ensures caret sits at the end of the query
            omnibarTextInput.post { omnibarTextInput.setSelection(omnibarTextInput.text.length) }
            appBarLayout.setExpanded(true, true)
        }

        pageLoadingIndicator.progress = viewState.progress

        when (viewState.showClearButton) {
            true -> showClearButton()
            false -> hideClearButton()
        }

        privacyGradeMenu?.isVisible = viewState.showPrivacyGrade
        fireMenu?.isVisible = viewState.showFireButton
        popupMenu.contentView.backPopupMenuItem.isEnabled = viewState.browserShowing && webView.canGoBack()
        popupMenu.contentView.forwardPopupMenuItem.isEnabled = viewState.browserShowing && webView.canGoForward()
        popupMenu.contentView.refreshPopupMenuItem.isEnabled = viewState.browserShowing
        popupMenu.contentView.addBookmarksPopupMenuItem?.isEnabled = viewState.canAddBookmarks

        when (viewState.showAutoCompleteSuggestions) {
            false -> autoCompleteSuggestionsList.gone()
            true -> {
                autoCompleteSuggestionsList.show()
                val results = viewState.autoCompleteSearchResults.suggestions
                autoCompleteSuggestionsAdapter.updateData(results)
            }
        }
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

    private fun userEnteredQuery(query: String) {
        omnibarTextInput.hideKeyboard()
        focusDummy.requestFocus()
        viewModel.onUserSubmittedQuery(query)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.webViewClient = webViewClient
        webView.webChromeClient = webChromeClient

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(true)
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(applicationContext, getString(R.string.webviewDownload), Toast.LENGTH_LONG).show()
        }

        webView.setOnTouchListener { _, _ ->
            if (omnibarTextInput.isFocused) {
                focusDummy.requestFocus()
            }
            false
        }

        viewModel.registerWebViewListener(webViewClient, webChromeClient)

        omnibarTextInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == IME_ACTION_DONE || keyEvent.keyCode == KEYCODE_ENTER) {
                userEnteredQuery(omnibarTextInput.text.toString())
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
            R.id.privacy_dashboard_menu_item -> {
                launchPrivacyDashboard()
                return true
            }
            R.id.fire_menu_item -> {
                launchFire()
                return true
            }
            R.id.browser_popup_menu_item -> {
                launchPopupMenu()
            }
        }
        return false
    }

    private fun launchPrivacyDashboard() {
        startActivityForResult(PrivacyDashboardActivity.intent(this), DASHBOARD_REQUEST_CODE)
    }

    private fun launchFire() {
        FireDialog(context = this,
                clearStarted = { finishActivityAnimated() },
                clearComplete = { applicationContext.toast(R.string.fireDataCleared) },
                cookieManager = cookieManagerProvider.get()
        ).show()
    }

    private fun launchPopupMenu() {
        val anchorView = findViewById<View>(R.id.browser_popup_menu_item)
        popupMenu.show(rootView, anchorView)
    }

    fun onGoForwardClicked(view: View) {
        webView.goForward()
        popupMenu.dismiss()
    }

    fun onGoBackClicked(view: View) {
        webView.goBack()
        popupMenu.dismiss()
    }

    fun onRefreshClicked(view: View) {
        webView.reload()
        popupMenu.dismiss()
    }

    fun onBookmarksClicked(view: View) {
        launchBookmarksView()
        popupMenu.dismiss()
    }

    fun onAddBookmarkClicked(view: View) {
        addBookmark()
        popupMenu.dismiss()
    }

    fun onSettingsClicked(view: View) {
        launchSettingsView()
        popupMenu.dismiss()
    }

    private fun addBookmark() {
        val title = webView.title
        val url = webView.url
        doAsync {
            viewModel.addBookmark(title, url)
            uiThread {
                toast(R.string.bookmarkAddedFeedback)
            }
        }
    }

    private fun launchSettingsView() {
        startActivityForResult(SettingsActivity.intent(this), SETTINGS_REQUEST_CODE)
    }

    private fun launchBookmarksView() {
        startActivityForResult(BookmarksActivity.intent(this), BOOKMARKS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DASHBOARD_REQUEST_CODE -> viewModel.receivedDashboardResult(resultCode)
            SETTINGS_REQUEST_CODE -> viewModel.receivedSettingsResult(resultCode)
            BOOKMARKS_REQUEST_CODE -> viewModel.receivedBookmarksResult(resultCode, data?.action)
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

    private fun finishActivityAnimated() {
        clearViewPriorToAnimation()
        supportFinishAfterTransition()
    }

    private fun clearViewPriorToAnimation() {
        acceptingRenderUpdates = false
        omnibarTextInput.text.clear()
        omnibarTextInput.hideKeyboard()
        webView.hide()
    }

    override fun onDestroy() {
        popupMenu.dismiss()
        super.onDestroy()
    }

    companion object {

        fun intent(context: Context, sharedText: String? = null): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(SHARED_TEXT_EXTRA, sharedText)
            return intent
        }

        private const val SHARED_TEXT_EXTRA = "SHARED_TEXT_EXTRA"
        private const val SETTINGS_REQUEST_CODE = 100
        private const val DASHBOARD_REQUEST_CODE = 101
        private const val BOOKMARKS_REQUEST_CODE = 102
    }

}