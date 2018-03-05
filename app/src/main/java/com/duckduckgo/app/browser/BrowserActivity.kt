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

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.view.*
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import android.webkit.URLUtil
import android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
import android.webkit.WebView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.duckduckgo.app.bookmarks.ui.BookmarkAddEditDialogFragment
import com.duckduckgo.app.bookmarks.ui.BookmarkAddEditDialogFragment.BookmarkDialogCreationListener
import com.duckduckgo.app.bookmarks.ui.BookmarksActivity
import com.duckduckgo.app.browser.BrowserViewModel.Command
import com.duckduckgo.app.browser.autoComplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.omnibar.KeyboardAwareEditText
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.intentText
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.renderer.icon
import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.app.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_browser.*
import kotlinx.android.synthetic.main.include_find_in_page.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.share
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import timber.log.Timber
import javax.inject.Inject


class BrowserActivity : DuckDuckGoActivity(), BookmarkDialogCreationListener, WebView.FindListener {

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var webChromeClient: BrowserChromeClient

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    lateinit var userAgentProvider: UserAgentProvider

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private val viewModel: BrowserViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(BrowserViewModel::class.java)
    }

    private val privacyGradeMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.privacy_dashboard_menu_item)

    private val fireMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.fire_menu_item)

    // Used to represent a file to download, but might first require permission requesting
    private var pendingFileDownload: PendingFileDownload? = null

    private var webView: WebView? = null

    private val findInPageTextWatcher = object: TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.userFindingInPage(findInPageInput.text.toString())
        }
    }

    private val omnibarInputTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.onOmnibarInputStateChanged(
                    omnibarTextInput.text.toString(),
                    omnibarTextInput.hasFocus()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_browser)

        createPopupMenu()
        configureObservers()
        configureToolbar()
        configureWebView()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()

        if (savedInstanceState == null) {
            consumeSharedQuery(intent)
        }
    }

    override fun onResume() {
        super.onResume()

        /*
         * Want to delay adding text changed listeners until after state has been restored
         * Otherwise, the act of restoring the state will trigger these listeners
         */
        addTextChangedListeners()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (shouldClearActivityState(intent)) {
            resetActivityState()
        } else {
            consumeSharedQuery(intent)
        }
    }

    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        val view = popupMenu.contentView
        popupMenu.apply {
            enableMenuOption(view.forwardPopupMenuItem) { webView?.goForward() }
            enableMenuOption(view.backPopupMenuItem) { webView?.goBack() }
            enableMenuOption(view.refreshPopupMenuItem) { webView?.reload() }
            enableMenuOption(view.bookmarksPopupMenuItem) { launchBookmarks() }
            enableMenuOption(view.addBookmarksPopupMenuItem) { addBookmark() }
            enableMenuOption(view.settingsPopupMenuItem) { launchSettings() }
            enableMenuOption(view.findInPageMenuItem) { viewModel.userRequestingToFindInPage() }
            enableMenuOption(view.requestDesktopSiteCheckMenuItem) {
                viewModel.desktopSiteModeToggled(urlString = webView?.url, desktopSiteRequested = view.requestDesktopSiteCheckMenuItem.isChecked)
            }
            enableMenuOption(view.sharePageMenuItem) { launchSharePageChooser(webView?.url) }
        }
    }

    private fun configureObservers() {
        viewModel.viewState.observe(this, Observer<BrowserViewModel.ViewState> {
            it?.let { render(it) }
        })

        viewModel.url.observe(this, Observer {
            it?.let { webView?.loadUrl(it) }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    private fun processCommand(it: Command?) {
        when (it) {
            Command.Refresh -> webView?.reload()
            is Command.Navigate -> {
                focusDummy.requestFocus()
                hideFindInPage()
                webView?.loadUrl(it.url)
            }
            Command.LandingPage -> resetActivityState()
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
                omnibarTextInput.postDelayed({ omnibarTextInput.showKeyboard() }, 300)
            }
            Command.HideKeyboard -> {
                hideKeyboard()
            }
            is Command.ShowFullScreen -> {
                webViewFullScreenContainer.addView(it.view, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            }
            is Command.DownloadImage -> {
                pendingFileDownload = PendingFileDownload(it.url, Environment.DIRECTORY_PICTURES)
                downloadFileWithPermissionCheck()
            }
            is Command.FindInPageCommand -> webView?.findAllAsync(it.searchTerm)
            Command.DismissFindInPage -> webView?.findAllAsync(null)
            is Command.ShareLink -> launchSharePageChooser(it.url)
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

    private fun consumeSharedQuery(intent: Intent?) {
        if (intent == null) {
            return
        }

        val sharedText = intent.intentText
        if (sharedText != null) {
            viewModel.onSharedTextReceived(sharedText)
        }
    }

    private fun shouldClearActivityState(intent: Intent?) : Boolean{
        if (intent == null) return false

        return intent.getBooleanExtra(REPLACE_EXISTING_SEARCH_EXTRA, false) || intent.action == Intent.ACTION_ASSIST
    }

    private fun render(viewState: BrowserViewModel.ViewState) {

        Timber.v("Rendering view state: $viewState")

        when (viewState.browserShowing) {
            true -> webView?.show()
            false -> webView?.hide()
        }

        toggleDesktopSiteMode(viewState.isDesktopBrowsingMode)

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

        renderToolbarMenu(viewState)

        when (viewState.autoComplete.showSuggestions) {
            false -> autoCompleteSuggestionsList.gone()
            true -> {
                autoCompleteSuggestionsList.show()
                val results = viewState.autoComplete.searchResults.suggestions
                autoCompleteSuggestionsAdapter.updateData(results)
            }
        }

        val immersiveMode = isImmersiveModeEnabled()
        when (viewState.isFullScreen) {
            true -> if (!immersiveMode) goFullScreen()
            false -> if (immersiveMode) exitFullScreen()
        }

        renderFindInPageState(viewState.findInPage)
    }

    private fun renderToolbarMenu(viewState: BrowserViewModel.ViewState) {
        popupMenu.contentView.backPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoBack()?: false
        popupMenu.contentView.forwardPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoForward()?: false
        popupMenu.contentView.refreshPopupMenuItem.isEnabled = viewState.browserShowing
        popupMenu.contentView.addBookmarksPopupMenuItem?.isEnabled = viewState.canAddBookmarks
        popupMenu.contentView.sharePageMenuItem?.isEnabled = viewState.canSharePage
        invalidateOptionsMenu()
    }

    private fun renderFindInPageState(viewState: BrowserViewModel.FindInPage) {
        when (viewState.visible) {
            true -> showFindInPageView(viewState)
            false -> hideFindInPage()
        }

        popupMenu.contentView.findInPageMenuItem?.isEnabled = viewState.canFindInPage
    }

    private fun hideFindInPage() {
        if(findInPageContainer.visibility != GONE) {
            focusDummy.requestFocus()
            findInPageContainer.gone()
            findInPageInput.hideKeyboard()
        }
    }

    private fun showFindInPageView(viewState: BrowserViewModel.FindInPage) {
        if(findInPageContainer.visibility != VISIBLE) {
            findInPageContainer.show()
            findInPageInput.postDelayed({ findInPageInput.showKeyboard() }, 300)
        }

        when(viewState.showNumberMatches) {
            false -> findInPageMatches.hide()
            true -> {
                findInPageMatches.text = getString(R.string.findInPageMatches, viewState.activeMatchIndex, viewState.numberMatches)
                findInPageMatches.show()
            }
        }
    }

    private fun goFullScreen() {
        Timber.i("Entering full screen")

        webViewFullScreenContainer.show()

        toggleFullScreen()
    }

    private fun exitFullScreen() {
        Timber.i("Exiting full screen")

        webViewFullScreenContainer.removeAllViews()
        webViewFullScreenContainer.gone()

        this.toggleFullScreen()
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
            !viewState.isEditing && omnibarTextInput.isDifferent(omnibarInput)

    private fun configureToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.let {
            it.title = null
        }
    }

    private fun configureFindInPage() {
        findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && findInPageInput.text.toString() != viewModel.viewState.value?.findInPage?.searchTerm) {
                viewModel.userFindingInPage(findInPageInput.text.toString())
            }
        }

        previousSearchTermButton.setOnClickListener { webView?.findNext(false) }
        nextSearchTermButton.setOnClickListener { webView?.findNext(true) }
        closeFindInPagePanel.setOnClickListener {
            viewModel.dismissFindInView()
        }
    }

    private fun configureOmnibarTextInput() {
        omnibarTextInput.onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus: Boolean ->
                    viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), hasFocus)
                }

        omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                omnibarTextInput.hideKeyboard()
                focusDummy.requestFocus()
                return true
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
        webView = layoutInflater.inflate(R.layout.include_duckduckgo_browser_webview, webViewContainer, true).findViewById(R.id.browserWebView) as WebView
        webView?.let {
            userAgentProvider = UserAgentProvider(it.settings.userAgentString)

            it.webViewClient = webViewClient
            it.webChromeClient = webChromeClient

            it.settings.apply {
                userAgentString = userAgentProvider.getUserAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
            }

            it.setDownloadListener { url, _, _, _, _ ->
                pendingFileDownload = PendingFileDownload(url, Environment.DIRECTORY_DOWNLOADS)

                downloadFileWithPermissionCheck()
            }

            it.setOnTouchListener { _, _ ->
                if (omnibarTextInput.isFocused) {
                    focusDummy.requestFocus()
                }
                false
            }

            registerForContextMenu(it)

            it.setFindListener(this)
        }
    }

    private fun toggleDesktopSiteMode(isDesktopSiteMode: Boolean) {
        webView?.settings?.userAgentString = userAgentProvider.getUserAgent(isDesktopSiteMode)
    }

    private fun downloadFileWithPermissionCheck() {
        if (hasWriteStoragePermission()) {
            downloadFile()
        } else {
            requestStoragePermission()
        }
    }

    private fun downloadFile() {
        val pending = pendingFileDownload
        pending?.let {
            val uri = Uri.parse(pending.url)
            val guessedFileName = URLUtil.guessFileName(pending.url, null, null)
            Timber.i("Guessed filename of $guessedFileName for url ${pending.url}")
            val request = DownloadManager.Request(uri).apply {
                allowScanningByMediaScanner()
                setDestinationInExternalPublicDir(pending.directory, guessedFileName)
                setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            }
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            pendingFileDownload = null
            Toast.makeText(applicationContext, getString(R.string.webviewDownload), Toast.LENGTH_LONG).show()
        }
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when(requestCode) {
            PERMISSION_REQUEST_EXTERNAL_STORAGE -> {
                if((grantResults.isNotEmpty()) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.i("Permission granted")
                    downloadFile()
                } else {
                    Timber.i("Permission refused")
                    Snackbar.make(toolbar, R.string.permissionRequiredToDownload, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        webView?.saveState(bundle)
        super.onSaveInstanceState(bundle)
    }

    override fun onRestoreInstanceState(bundle: Bundle) {
        super.onRestoreInstanceState(bundle)
        webView?.restoreState(bundle)
    }

    private fun addTextChangedListeners() {
        findInPageInput.replaceTextChangedListener(findInPageTextWatcher)
        omnibarTextInput.replaceTextChangedListener(omnibarInputTextWatcher)
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        webView?.hitTestResult?.let {
            if(URLUtil.isNetworkUrl(it.extra)) {
                viewModel.userLongPressedInWebView(it, menu)
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        webView?.hitTestResult?.let {
            val url = it.extra
            if (viewModel.userSelectedItemFromLongPressMenu(url, item)) {
                return true
            }
        }

        return super.onContextItemSelected(item)
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

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val viewState = viewModel.viewState.value!!
        privacyGradeMenu?.isVisible = viewState.showPrivacyGrade
        fireMenu?.isVisible = viewState.showFireButton
        return super.onPrepareOptionsMenu(menu)
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
        startActivityForResult(PrivacyDashboardActivity.intent(this, viewModel.tabId), DASHBOARD_REQUEST_CODE)
    }

    private fun launchFire() {
        FireDialog(context = this,
                clearStarted = { resetActivityState() },
                clearComplete = { applicationContext.toast(R.string.fireDataCleared) }
        ).show()
    }

    private fun launchPopupMenu() {
        popupMenu.show(rootView, toolbar)
    }

    private fun launchSharePageChooser(url: String?) {
        if (url != null) {
            share(url, "")
        }
    }

    private fun addBookmark() {

        val addBookmarkDialog = BookmarkAddEditDialogFragment.createDialogCreationMode(
                existingTitle = webView?.title,
                existingUrl = webView?.url
        )

        addBookmarkDialog.show(supportFragmentManager, ADD_BOOKMARK_FRAGMENT_TAG)
    }

    private fun launchSettings() {
        startActivity(SettingsActivity.intent(this))
    }

    private fun launchBookmarks() {
        startActivity(BookmarksActivity.intent(this))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == DASHBOARD_REQUEST_CODE) {
            viewModel.receivedDashboardResult(resultCode)
        }
    }

    override fun onBackPressed() {
        when {
            webView?.canGoBack() == true -> webView?.goBack()
            webView?.visibility == VISIBLE -> resetActivityState()
            else -> super.onBackPressed()
        }
    }

    private fun resetActivityState() {
        omnibarTextInput.text.clear()
        viewModel.resetView()
        destroyWebView()
        configureWebView()
        omnibarTextInput.postDelayed({omnibarTextInput.showKeyboard()}, 300)
    }

    private fun destroyWebView() {
        webViewContainer.removeAllViews()
        webView?.destroy()
        webView = null
    }

    override fun onDestroy() {
        destroyWebView()

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

    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        viewModel.onFindResultsReceived(activeMatchOrdinal, numberOfMatches)
    }

    private fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
        removeTextChangedListener(textWatcher)
        addTextChangedListener(textWatcher)
    }

    private fun hideKeyboard() {
        omnibarTextInput.hideKeyboard()
        focusDummy.requestFocus()
    }

    private data class PendingFileDownload(
            val url: String,
            val directory: String
    )

    companion object {

        fun intent(context: Context, queryExtra: String? = null, replaceExistingSearch: Boolean = false): Intent {
            val intent = Intent(context, BrowserActivity::class.java)
            intent.putExtra(EXTRA_TEXT, queryExtra)
            intent.putExtra(REPLACE_EXISTING_SEARCH_EXTRA, replaceExistingSearch)
            return intent
        }

        private const val ADD_BOOKMARK_FRAGMENT_TAG = "ADD_BOOKMARK"
        private const val REPLACE_EXISTING_SEARCH_EXTRA = "REPLACE_EXISTING_SEARCH_EXTRA"
        private const val DASHBOARD_REQUEST_CODE = 100
        private const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 200
    }

}