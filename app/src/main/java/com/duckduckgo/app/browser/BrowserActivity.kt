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
import android.os.Build
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
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import com.duckduckgo.app.bookmarks.ui.BookmarkAddEditDialogFragment
import com.duckduckgo.app.bookmarks.ui.BookmarkAddEditDialogFragment.BookmarkDialogCreationListener
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command
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


class BrowserActivity : DuckDuckGoActivity(), BookmarkDialogCreationListener {

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

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_browser)

        createWebView()
        createPopupMenu()
        configureObservers()
        configureToolbar()
        configureWebView()
        configureOmnibarTextInput()
        configureDummyViewTouchHandler()
        configureAutoComplete()

        if (savedInstanceState == null) {
            consumeSharedQuery()
        }
    }

    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
    }

    private fun createWebView() {
        webView = NestedWebView(this)
        webView.gone()
        webView.isFocusableInTouchMode = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.focusable = View.FOCUSABLE
        }

        webViewContainer.addView(webView)
    }

    private fun configureObservers() {
        viewModel.viewState.observe(this, Observer<BrowserViewModel.ViewState> {
            it?.let { render(it) }
        })

        viewModel.url.observe(this, Observer {
            it?.let { webView.loadUrl(it) }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun processCommand(it: Command?) {
        when (it) {
            Command.Refresh -> webView.reload()
            is Command.Navigate -> {
                focusDummy.requestFocus()
                webView.loadUrl(it.url)
            }
            Command.LandingPage -> finishActivityAnimated()
            is Command.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${it.telephoneNumber}")
                launchExternalActivity(intent)
            }
            is Command.SendEmail -> {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse(it.emailAddress)
                launchExternalActivity(intent)
            }
            is Command.SendSms -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${it.telephoneNumber}"))
                startActivity(intent)
            }
            Command.ShowKeyboard -> {
                omnibarTextInput.postDelayed({omnibarTextInput.showKeyboard()}, 300)
            }
            Command.HideKeyboard -> {
                omnibarTextInput.hideKeyboard()
                focusDummy.requestFocus()
            }
            Command.ReinitialiseWebView -> {
                webView.clearHistory()
            }
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

    private fun consumeSharedQuery() {
        val sharedText = intent.getStringExtra(QUERY_EXTRA)
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
        omnibarTextInput.onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus: Boolean ->
                    viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), hasFocus)
                }

        omnibarTextInput.addTextChangedListener(object : TextChangedWatcher() {

            override fun afterTextChanged(editable: Editable) {
                viewModel.onOmnibarInputStateChanged(
                        omnibarTextInput.text.toString(),
                        omnibarTextInput.hasFocus()
                )
            }
        })

        omnibarTextInput.onBackKeyListener = object : OnBackKeyListener {
            override fun onBackKey(): Boolean {
                focusDummy.requestFocus()
                return viewModel.userDismissedKeyboard()
            }
        }

        omnibarTextInput.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == IME_ACTION_DONE || keyEvent?.keyCode == KEYCODE_ENTER) {
                userEnteredQuery(omnibarTextInput.text.toString())
                return@OnEditorActionListener true
            }
            false
        })

        clearOmnibarInputButton.setOnClickListener { omnibarTextInput.setText("") }
    }

    private fun userEnteredQuery(query: String) {
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

        webView.setDownloadListener { url, _, _, _, _ ->
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
        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            it?.let {
                privacyGradeMenu?.icon = getDrawable(it.icon())
            }
        })
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
        popupMenu.show(rootView, toolbar)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onGoForwardClicked(view: View) {
        webView.goForward()
        popupMenu.dismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onGoBackClicked(view: View) {
        webView.goBack()
        popupMenu.dismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRefreshClicked(view: View) {
        webView.reload()
        popupMenu.dismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBookmarksClicked(view: View) {
        launchBookmarksView()
        popupMenu.dismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAddBookmarkClicked(view: View) {
        addBookmark()
        popupMenu.dismiss()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSettingsClicked(view: View) {
        launchSettingsView()
        popupMenu.dismiss()
    }

    private fun addBookmark() {

        val addBookmarkDialog = BookmarkAddEditDialogFragment.createDialogCreationMode(
                existingTitle = webView.title,
                existingUrl = webView.url
        )

        addBookmarkDialog.show(supportFragmentManager, ADD_BOOKMARK_FRAGMENT_TAG)
    }

    private fun launchSettingsView() {
        startActivity(SettingsActivity.intent(this))
    }

    private fun launchBookmarksView() {
        startActivity(BookmarksActivity.intent(this))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DASHBOARD_REQUEST_CODE ) {
            viewModel.receivedDashboardResult(resultCode)
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
        privacyGradeMenu?.isVisible = false
        omnibarTextInput.text.clear()
        omnibarTextInput.hideKeyboard()
        webView.hide()
    }

    override fun onDestroy() {
        webViewContainer.removeAllViews()
        webView.destroy()

        popupMenu.dismiss()
        super.onDestroy()
    }

    override fun userWantsToCreateBookmark(title: String, url: String) {
        doAsync {
            viewModel.addBookmark(title, url)
            uiThread {
                toast(R.string.bookmarkAddedFeedback)
            }
        }
    }

    companion object {

        fun intent(context: Context, queryExtra: String? = null): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(QUERY_EXTRA, queryExtra)
            return intent
        }

        private const val ADD_BOOKMARK_FRAGMENT_TAG = "ADD_BOOKMARK"
        private const val QUERY_EXTRA = "QUERY_EXTRA"
        private const val DASHBOARD_REQUEST_CODE = 100
    }

}