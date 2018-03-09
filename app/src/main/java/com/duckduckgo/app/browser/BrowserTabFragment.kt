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

import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.view.*
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.duckduckgo.app.bookmarks.ui.SaveBookmarkDialogFragment
import com.duckduckgo.app.browser.BrowserTabViewModel.*
import com.duckduckgo.app.browser.autoComplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.omnibar.KeyboardAwareEditText
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.renderer.icon
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_browser_tab.*
import kotlinx.android.synthetic.main.include_find_in_page.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import org.jetbrains.anko.share
import timber.log.Timber
import javax.inject.Inject


class BrowserTabFragment : Fragment(), FindListener {

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var webChromeClient: BrowserChromeClient

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    val tabId get() = arguments!![TAB_ID_ARG] as String

    lateinit var userAgentProvider: UserAgentProvider

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    private val viewModel: BrowserTabViewModel by lazy {
        val model = ViewModelProviders.of(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        model.registerTabId(tabId)
        model
    }

    private val browserActivity
        get() = activity as? BrowserActivity

    private val privacyGradeMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.privacy_dashboard_menu_item)

    private val fireMenu: MenuItem?
        get() = toolbar.menu.findItem(R.id.fire_menu_item)

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_browser_tab, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        createPopupMenu()
        configureObservers()
        configureToolbar()
        configureWebView()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        addTextChangedListeners()
    }

    override fun onAttach(context: Context?) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        val view = popupMenu.contentView
        popupMenu.apply {
            enableMenuOption(view.forwardPopupMenuItem) { webView?.goForward() }
            enableMenuOption(view.backPopupMenuItem) { webView?.goBack() }
            enableMenuOption(view.refreshPopupMenuItem) { webView?.reload() }
            enableMenuOption(view.bookmarksPopupMenuItem) { browserActivity?.launchBookmarks() }
            enableMenuOption(view.addBookmarksPopupMenuItem) { addBookmark() }
            enableMenuOption(view.settingsPopupMenuItem) { browserActivity?.launchSettings() }
            enableMenuOption(view.findInPageMenuItem) { viewModel.userRequestingToFindInPage() }
            enableMenuOption(view.requestDesktopSiteCheckMenuItem) {
                viewModel.desktopSiteModeToggled(
                    urlString = webView?.url,
                    desktopSiteRequested = view.requestDesktopSiteCheckMenuItem.isChecked
                )
            }
            enableMenuOption(view.sharePageMenuItem) { viewModel.userSharingLink(webView?.url) }
        }
    }

    private fun configureObservers() {
        viewModel.viewState.observe(this, Observer<ViewState> {
            it?.let { render(it) }
        })

        viewModel.url.observe(this, Observer {
            it?.let { webView?.loadUrl(it) }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })
    }

    fun navigate(url: String) {
        focusDummy.requestFocus()
        hideFindInPage()
        webView?.loadUrl(url)
    }

    fun refresh() {
        webView?.reload()
    }

    private fun processCommand(it: Command?) {
        when (it) {
            Command.Refresh -> refresh()
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
                omnibarTextInput.postDelayed({ omnibarTextInput.showKeyboard() }, 300)
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
            is Command.DownloadImage -> {
                browserActivity?.downloadImage(it.url)
            }
            is Command.FindInPageCommand -> webView?.findAllAsync(it.searchTerm)
            Command.DismissFindInPage -> webView?.findAllAsync(null)
            is Command.ShareLink -> launchSharePageChooser(it.url)
            is Command.DisplayMessage -> showToast(it.messageId)
        }
    }

    private fun showToast(messageId: Int) {
        Toast.makeText(context, messageId, Toast.LENGTH_LONG).show()
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

            // ensures caret sits at the end of the query
            omnibarTextInput.post { omnibarTextInput.setSelection(omnibarTextInput.text.length) }
            appBarLayout.setExpanded(true, true)
        }

        pageLoadingIndicator.progress = viewState.progress

        when (viewState.showClearButton) {
            true -> showClearButton()
            false -> hideClearButton()
        }

        renderToolbarButtons(viewState)
        renderPopupMenu(viewState)

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
    }

    private fun renderToolbarButtons(viewState: ViewState) {
        fireMenu?.isVisible = viewState.showFireButton
        privacyGradeMenu?.isVisible = viewState.showPrivacyGrade
    }

    private fun renderPopupMenu(viewState: ViewState) {
        popupMenu.contentView.backPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoBack() ?: false
        popupMenu.contentView.forwardPopupMenuItem.isEnabled = viewState.browserShowing && webView?.canGoForward() ?: false
        popupMenu.contentView.refreshPopupMenuItem.isEnabled = viewState.browserShowing
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
            findInPageInput.postDelayed({ findInPageInput.showKeyboard() }, 300)
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

    private fun shouldUpdateOmnibarTextInput(viewState: ViewState, omnibarInput: String?) =
        !viewState.isEditing && omnibarTextInput.isDifferent(omnibarInput)

    private fun configureToolbar() {
        toolbar.inflateMenu(R.menu.menu_browser_activity)

        toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.privacy_dashboard_menu_item -> {
                    browserActivity?.launchPrivacyDashboard()
                    return@setOnMenuItemClickListener true
                }
                R.id.fire_menu_item -> {
                    browserActivity?.launchFire()
                    return@setOnMenuItemClickListener true
                }
                R.id.browser_popup_menu_item -> {
                    launchPopupMenu()
                    return@setOnMenuItemClickListener true
                }
                else -> return@setOnMenuItemClickListener false
            }
        }

        viewModel.viewState.value?.let {
            renderToolbarButtons(it)
        }

        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            it?.let {
                privacyGradeMenu?.icon = context?.getDrawable(it.icon())
            }
        })
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
            if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
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
        webView = layoutInflater.inflate(
            R.layout.include_duckduckgo_browser_webview,
            webViewContainer,
            true
        ).findViewById(R.id.browserWebView) as WebView
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

            it.setDownloadListener { url, _, _, _, _ ->
                browserActivity?.downloadFile(url)
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
            if (URLUtil.isNetworkUrl(it.extra)) {
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

    private fun launchPopupMenu() {
        popupMenu.show(rootView, toolbar)
    }

    private fun launchSharePageChooser(url: String) {
        activity?.share(url, "")
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

    private fun hideKeyboard() {
        omnibarTextInput.hideKeyboard()
        focusDummy.requestFocus()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        webView?.saveState(bundle)
        super.onSaveInstanceState(bundle)
    }

    override fun onViewStateRestored(bundle: Bundle?) {
        super.onViewStateRestored(bundle)
        webView?.restoreState(bundle)
    }

    fun resetTabState() {
        omnibarTextInput.text.clear()
        viewModel.resetView()
        destroyWebView()
        configureWebView()
        omnibarTextInput.postDelayed({ omnibarTextInput.showKeyboard() }, 300)
    }

    fun onBackPressed() : Boolean {
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

    companion object {

        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val ADD_BOOKMARK_FRAGMENT_TAG = "ADD_BOOKMARK"

        fun newInstance(tabId: String): BrowserTabFragment {
            val fragment = BrowserTabFragment()
            val args = Bundle()
            args.putString(TAB_ID_ARG, tabId)
            fragment.setArguments(args)
            return fragment
        }
    }
}
