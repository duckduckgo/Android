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

package com.duckduckgo.app.browser

import android.Manifest
import android.animation.LayoutTransition.CHANGING
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityOptions
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.annotation.AnyThread
import android.support.annotation.StringRes
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.view.*
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.duckduckgo.app.bookmarks.ui.SaveBookmarkDialogFragment
import com.duckduckgo.app.browser.BrowserTabViewModel.*
import com.duckduckgo.app.browser.autoComplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserInfoActivity
import com.duckduckgo.app.browser.downloader.FileDownloadNotificationManager
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.filechooser.FileChooserIntentBuilder
import com.duckduckgo.app.browser.omnibar.KeyboardAwareEditText
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.renderer.icon
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabIconRenderer
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_browser_tab.*
import kotlinx.android.synthetic.main.include_banner_notification.*
import kotlinx.android.synthetic.main.include_find_in_page.*
import kotlinx.android.synthetic.main.include_omnibar_toolbar.*
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.share
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.concurrent.thread


class BrowserTabFragment : Fragment(), FindListener {

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var webChromeClient: BrowserChromeClient

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var fileChooserIntentBuilder: FileChooserIntentBuilder

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var fileDownloadNotificationManager: FileDownloadNotificationManager

    val tabId get() = arguments!![TAB_ID_ARG] as String

    val initialUrl get() = arguments!![URL_EXTRA_ARG] as String?

    lateinit var userAgentProvider: UserAgentProvider

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProviders.of(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl)
        viewModel
    }

    private val browserActivity
        get() = activity as? BrowserActivity

    private val tabsButton: MenuItem?
        get() = toolbar.menu.findItem(R.id.tabs)

    private val fireMenuButton: MenuItem?
        get() = toolbar.menu.findItem(R.id.fire)

    private val menuButton: MenuItem?
        get() = toolbar.menu.findItem(R.id.browserPopup)

    private var webView: WebView? = null

    private val findInPageTextWatcher = object : TextChangedWatcher() {
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

    override fun onAttach(context: Context?) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_browser_tab, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        createPopupMenu()
        configureObservers()
        configureToolbar()
        configureBannerNotification()
        configureWebView()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        configureKeyboardAwareLogoAnimation()

        if (savedInstanceState == null) {
            viewModel.onViewReady()
        }
    }

    override fun onResume() {
        super.onResume()
        addTextChangedListeners()
        viewModel.onViewVisible()
    }

    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        val view = popupMenu.contentView
        popupMenu.apply {
            onMenuItemClicked(view.forwardPopupMenuItem) { webView?.goForward() }
            onMenuItemClicked(view.backPopupMenuItem) { webView?.goBack() }
            onMenuItemClicked(view.refreshPopupMenuItem) { webView?.reload() }
            onMenuItemClicked(view.newTabPopupMenuItem) { browserActivity?.launchNewTab() }
            onMenuItemClicked(view.bookmarksPopupMenuItem) { browserActivity?.launchBookmarks() }
            onMenuItemClicked(view.addBookmarksPopupMenuItem) { addBookmark() }
            onMenuItemClicked(view.findInPageMenuItem) { viewModel.userRequestingToFindInPage() }
            onMenuItemClicked(view.brokenSitePopupMenuItem) { browserActivity?.launchBrokenSiteFeedback(viewModel.url.value) }
            onMenuItemClicked(view.settingsPopupMenuItem) { browserActivity?.launchSettings() }
            onMenuItemClicked(view.requestDesktopSiteCheckMenuItem) {
                viewModel.desktopSiteModeToggled(
                        urlString = webView?.url,
                        desktopSiteRequested = view.requestDesktopSiteCheckMenuItem.isChecked
                )
            }
            onMenuItemClicked(view.sharePageMenuItem) { viewModel.userSharingLink(webView?.url) }
        }
    }

    private fun configureObservers() {
        viewModel.viewState.observe(this, Observer<ViewState> {
            it?.let { render(it) }
        })

        viewModel.tabs.observe(this, Observer<List<TabEntity>> {
            it?.let { renderTabIcon(it)}
        })

        viewModel.url.observe(this, Observer {
            it?.let { navigate(it) }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    fun submitQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    private fun navigate(url: String) {
        hideKeyboard()
        hideFindInPage()
        webView?.loadUrl(url)
    }

    fun refresh() {
        webView?.reload()
    }

    private fun processCommand(it: Command?) {
        when (it) {
            Command.Refresh -> refresh()
            is Command.OpenInNewTab -> {
                browserActivity?.openInNewTab(it.query)
            }
            is Command.Navigate -> {
                navigate(it.url)
            }
            Command.LandingPage -> resetTabState()
            is Command.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${it.telephoneNumber}")
                activity?.launchExternalActivity(intent)
            }
            is Command.SendEmail -> {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse(it.emailAddress)
                activity?.launchExternalActivity(intent)
            }
            is Command.SendSms -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${it.telephoneNumber}"))
                startActivity(intent)
            }
            Command.ShowKeyboard -> {
                showKeyboard()
            }
            Command.HideKeyboard -> {
                hideKeyboard()
            }
            is Command.ShowFullScreen -> {
                webViewFullScreenContainer.addView(
                        it.view, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
                )
            }
            is Command.DownloadImage -> requestImageDownload(it.url)
            is Command.FindInPageCommand -> webView?.findAllAsync(it.searchTerm)
            Command.DismissFindInPage -> webView?.findAllAsync(null)
            is Command.ShareLink -> launchSharePageChooser(it.url)
            is Command.DisplayMessage -> showToast(it.messageId)
            is Command.ShowFileChooser -> {
                launchFilePicker(it)
            }
            is Command.LaunchDefaultAppSystemSettings -> { launchDefaultAppSystemSettings() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
            handleFileUploadResult(resultCode, data)
        }
    }

    private fun handleFileUploadResult(resultCode: Int, intent: Intent?) {
        if (resultCode != RESULT_OK || intent == null) {
            Timber.i("Received resultCode $resultCode (or received null intent) indicating user did not select any files")
            pendingUploadTask?.onReceiveValue(null)
            return
        }

        val uris = fileChooserIntentBuilder.extractSelectedFileUris(intent)
        pendingUploadTask?.onReceiveValue(uris)
    }

    private fun showToast(@StringRes messageId: Int) {
        context?.applicationContext?.longToast(messageId)
    }

    private fun configureAutoComplete() {
        val context = context ?: return
        autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(context)
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

    private fun render(viewState: ViewState) {

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
            appBarLayout.setExpanded(true, true)
        }

        pageLoadingIndicator.progress = viewState.progress
        renderToolbarButtons(viewState)
        renderPopupMenu(viewState)

        when (viewState.isEditing) {
            true -> omniBarContainer.setBackgroundResource(R.drawable.omnibar_editing_background)
            false -> omniBarContainer.background = null
        }

        when (viewState.autoComplete.showSuggestions) {
            false -> autoCompleteSuggestionsList.gone()
            true -> {
                autoCompleteSuggestionsList.show()
                val results = viewState.autoComplete.searchResults.suggestions
                autoCompleteSuggestionsAdapter.updateData(results)
            }
        }

        activity?.isImmersiveModeEnabled()?.let {
            when (viewState.isFullScreen) {
                true -> if (!it) goFullScreen()
                false -> if (it) exitFullScreen()
            }
        }

        renderFindInPageState(viewState.findInPage)

        when(viewState.showDefaultBrowserBanner) {
            true -> bannerNotification.show()
            false -> bannerNotification.gone()
        }
    }

    private fun renderToolbarButtons(viewState: ViewState) {
        privacyGradeButton?.isVisible = viewState.showPrivacyGrade
        clearTextButton?.isVisible = viewState.showClearButton
        tabsButton?.isVisible = viewState.showTabsButton
        fireMenuButton?.isVisible = viewState.showFireButton
        menuButton?.isVisible = viewState.showMenuButton
    }

    private fun renderPopupMenu(viewState: ViewState) {
        popupMenu.contentView.backPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoBack() ?: false
        popupMenu.contentView.forwardPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoForward() ?: false
        popupMenu.contentView.refreshPopupMenuItem.isEnabled = viewState.browserShowing
        popupMenu.contentView.newTabPopupMenuItem.isEnabled = viewState.browserShowing
        popupMenu.contentView.addBookmarksPopupMenuItem?.isEnabled = viewState.canAddBookmarks
        popupMenu.contentView.sharePageMenuItem?.isEnabled = viewState.canSharePage
    }

    private fun renderFindInPageState(viewState: FindInPage) {
        when (viewState.visible) {
            true -> showFindInPageView(viewState)
            false -> hideFindInPage()
        }

        popupMenu.contentView.findInPageMenuItem?.isEnabled = viewState.canFindInPage
    }

    private fun renderTabIcon(tabs: List<TabEntity>) {
        context?.let {
            tabsButton?.icon = TabIconRenderer.icon(it, tabs.count())
        }
    }

    private fun hideFindInPage() {
        if (findInPageContainer.visibility != View.GONE) {
            focusDummy.requestFocus()
            findInPageContainer.gone()
            findInPageInput.hideKeyboard()
        }
    }

    private fun showFindInPageView(viewState: FindInPage) {
        if (findInPageContainer.visibility != View.VISIBLE) {
            findInPageContainer.show()
            findInPageInput.postDelayed(KEYBOARD_DELAY) { findInPageInput?.showKeyboard() }
        }

        when (viewState.showNumberMatches) {
            false -> findInPageMatches.hide()
            true -> {
                findInPageMatches.text = getString(
                        R.string.findInPageMatches,
                        viewState.activeMatchIndex,
                        viewState.numberMatches
                )
                findInPageMatches.show()
            }
        }
    }

    private fun goFullScreen() {
        Timber.i("Entering full screen")
        webViewFullScreenContainer.show()
        activity?.toggleFullScreen()
    }

    private fun exitFullScreen() {
        Timber.i("Exiting full screen")
        webViewFullScreenContainer.removeAllViews()
        webViewFullScreenContainer.gone()
        activity?.toggleFullScreen()
    }

    private fun shouldUpdateOmnibarTextInput(viewState: ViewState, omnibarInput: String?) =
            !viewState.isEditing && omnibarTextInput.isDifferent(omnibarInput)

    private fun configureToolbar() {
        toolbar.inflateMenu(R.menu.menu_browser_activity)

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.tabs -> {
                    browserActivity?.launchTabSwitcher()
                    return@setOnMenuItemClickListener true
                }
                R.id.fire -> {
                    browserActivity?.launchFire()
                    return@setOnMenuItemClickListener true
                }
                R.id.browserPopup -> {
                    hideKeyboardImmediately()
                    launchPopupMenu()
                    return@setOnMenuItemClickListener true
                }
                else -> return@setOnMenuItemClickListener false
            }
        }

        toolbar.privacyGradeButton.setOnClickListener {
            browserActivity?.launchPrivacyDashboard()
        }

        viewModel.viewState.value?.let {
            renderToolbarButtons(it)
        }

        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            it?.let {
                val drawable = context?.getDrawable(it.icon()) ?: return@let
                privacyGradeButton?.setImageDrawable(drawable)
            }
        })
    }

    private fun configureBannerNotification() {
        dismissBannerButton.setOnClickListener {
            viewModel.userDeclinedToSetAsDefaultBrowser()
        }
        bannerNotification.setOnClickListener {
            viewModel.userAcceptedToSetAsDefaultBrowser()
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
            if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                userEnteredQuery(omnibarTextInput.text.toString())
                return@OnEditorActionListener true
            }
            false
        })

        clearTextButton.setOnClickListener { omnibarTextInput.setText("") }
    }

    private fun configureKeyboardAwareLogoAnimation() {
        logoParent.layoutTransition.enableTransitionType(CHANGING)
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
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
            }

            it.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                requestFileDownload(url, contentDisposition, mimeType)
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

    private fun addTextChangedListeners() {
        findInPageInput.replaceTextChangedListener(findInPageTextWatcher)
        omnibarTextInput.replaceTextChangedListener(omnibarInputTextWatcher)
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        webView?.hitTestResult?.let {
            viewModel.userLongPressedInWebView(it, menu)
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

    private fun launchPopupMenu() {
        popupMenu.show(rootView, toolbar)
    }

    private fun launchSharePageChooser(url: String) {
        activity?.share(url, "")
    }

    private fun launchDefaultAppSystemSettings() {
        activity?.let {
            val options = ActivityOptions.makeSceneTransitionAnimation(it, bannerNotification, "defaultBrowserBannerTransition")
            val intent = DefaultBrowserInfoActivity.intent(it)
            startActivity(intent, options.toBundle())
        }
    }

    private fun addBookmark() {
        val addBookmarkDialog = SaveBookmarkDialogFragment.createDialogCreationMode(
                existingTitle = webView?.title,
                existingUrl = webView?.url
        )
        addBookmarkDialog.show(childFragmentManager, ADD_BOOKMARK_FRAGMENT_TAG)
        addBookmarkDialog.listener = viewModel
    }

    override fun onFindResultReceived(activeMatchOrdinal: Int, numberOfMatches: Int, isDoneCounting: Boolean) {
        viewModel.onFindResultsReceived(activeMatchOrdinal, numberOfMatches)
    }

    private fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
        removeTextChangedListener(textWatcher)
        addTextChangedListener(textWatcher)
    }

    private fun hideKeyboardImmediately() {
        if (!isHidden) {
            Timber.v("Keyboard now hiding")
            omnibarTextInput.hideKeyboard()
            focusDummy.requestFocus()
        }
    }

    private fun hideKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now hiding")
            omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibarTextInput?.hideKeyboard() }
            focusDummy.requestFocus()
        }
    }

    private fun showKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibarTextInput?.showKeyboard() }
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        webView?.saveState(bundle)
        super.onSaveInstanceState(bundle)
    }

    override fun onViewStateRestored(bundle: Bundle?) {
        super.onViewStateRestored(bundle)
        webView?.restoreState(bundle)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            webView?.onPause()
        } else {
            webView?.onResume()
            viewModel.onViewVisible()
        }
    }

    /**
     * We don't destroy the activity on config changes like orientation, so we need to ensure we update resources which might change based on config
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ddgLogo.setImageResource(R.drawable.full_logo)
    }

    private fun resetTabState() {
        omnibarTextInput.text.clear()
        viewModel.resetView()
        destroyWebView()
        configureWebView()
        showKeyboard()
    }

    fun onBackPressed(): Boolean {
        return when {
            webView?.canGoBack() == true -> {
                webView?.goBack()
                true
            }
            webView?.visibility == VISIBLE -> {
                resetTabState()
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        popupMenu.dismiss()
        destroyWebView()
        super.onDestroy()
    }

    private fun destroyWebView() {
        webViewContainer?.removeAllViews()
        webView?.destroy()
        webView = null
    }

    private fun requestFileDownload(url: String, contentDisposition: String, mimeType: String) {
        pendingFileDownload = PendingFileDownload(
                url = url,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                subfolder = Environment.DIRECTORY_DOWNLOADS)

        downloadFileWithPermissionCheck()
    }

    private fun requestImageDownload(url: String) {
        pendingFileDownload = PendingFileDownload(
                url = url,
                subfolder = Environment.DIRECTORY_PICTURES)

        downloadFileWithPermissionCheck()
    }

    private fun downloadFileWithPermissionCheck() {
        if (hasWriteStoragePermission()) {
            downloadFile()
        } else {
            requestWriteStoragePermission()
        }
    }

    @AnyThread
    private fun downloadFile() {
        val pendingDownload = pendingFileDownload
        pendingFileDownload = null
        thread {
            fileDownloader.download(pendingDownload, object : FileDownloader.FileDownloadListener {
                override fun downloadStarted() {
                    fileDownloadNotificationManager.showDownloadInProgressNotification()
                }

                override fun downloadFinished(file: File, mimeType: String?) {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, { _, uri ->
                        fileDownloadNotificationManager.showDownloadFinishedNotification(file.name, uri, mimeType)
                    })
                }

                override fun downloadFailed(message: String) {
                    Timber.w("Failed to download file [$message]")
                    fileDownloadNotificationManager.showDownloadFailedNotification()
                }
            })
        }
    }

    private fun launchFilePicker(command: Command.ShowFileChooser) {
        pendingUploadTask = command.filePathCallback
        val canChooseMultipleFiles = command.fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(command.fileChooserParams.acceptTypes, canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if ((grantResults.isNotEmpty()) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.i("Write external storage permission granted")
                downloadFile()
            } else {
                Timber.i("Write external storage permission refused")
                Snackbar.make(toolbar, R.string.permissionRequiredToDownload, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val ADD_BOOKMARK_FRAGMENT_TAG = "ADD_BOOKMARK"
        private const val URL_EXTRA_ARG = "URL_EXTRA_ARG"
        private const val KEYBOARD_DELAY = 200L

        private const val REQUEST_CODE_CHOOSE_FILE = 100
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200

        fun newInstance(tabId: String, query: String? = null): BrowserTabFragment {
            val fragment = BrowserTabFragment()
            val args = Bundle()
            args.putString(TAB_ID_ARG, tabId)
            query.let {
                args.putString(URL_EXTRA_ARG, query)
            }
            fragment.arguments = args
            return fragment
        }
    }


}
