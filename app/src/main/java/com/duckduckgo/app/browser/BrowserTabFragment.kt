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
import android.animation.LayoutTransition.DISAPPEARING
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.text.Editable
import android.view.*
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.webkit.WebView.FindListener
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.HitTestResult.*
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.bookmarks.ui.EditBookmarkDialogFragment
import com.duckduckgo.app.browser.BrowserTabViewModel.*
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.downloader.FileDownloadNotificationManager
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.filechooser.FileChooserIntentBuilder
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.KeyboardAwareEditText
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.ui.HttpAuthenticationDialogFragment
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.global.view.*
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.renderer.icon
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
import com.duckduckgo.widget.SearchWidgetLight
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_browser_tab.*
import kotlinx.android.synthetic.main.include_cta_buttons.view.*
import kotlinx.android.synthetic.main.include_dax_dialog_cta.*
import kotlinx.android.synthetic.main.include_find_in_page.*
import kotlinx.android.synthetic.main.include_new_browser_tab.*
import kotlinx.android.synthetic.main.include_omnibar_toolbar.*
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.*
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import kotlinx.coroutines.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.share
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext


class BrowserTabFragment : Fragment(), FindListener, CoroutineScope {

    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = supervisorJob + Dispatchers.Main

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var webChromeClient: BrowserChromeClient

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var deviceInfo: DeviceInfo

    @Inject
    lateinit var fileChooserIntentBuilder: FileChooserIntentBuilder

    @Inject
    lateinit var fileDownloader: FileDownloader

    @Inject
    lateinit var fileDownloadNotificationManager: FileDownloadNotificationManager

    @Inject
    lateinit var webViewSessionStorage: WebViewSessionStorage

    @Inject
    lateinit var shortcutBuilder: ShortcutBuilder

    @Inject
    lateinit var clipboardManager: ClipboardManager

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var ctaViewModel: CtaViewModel

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var previewGenerator: WebViewPreviewGenerator

    @Inject
    lateinit var previewPersister: WebViewPreviewPersister

    @Inject
    lateinit var variantManager: VariantManager

    @Inject
    lateinit var privacySettingsStore: PrivacySettingsStore

    val tabId get() = arguments!![TAB_ID_ARG] as String

    lateinit var userAgentProvider: UserAgentProvider

    var messageFromPreviousTab: Message? = null

    private val initialUrl get() = arguments!!.getString(URL_EXTRA_ARG)

    private val skipHome get() = arguments!!.getBoolean(SKIP_HOME_ARG)

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private lateinit var renderer: BrowserTabFragmentRenderer

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProviders.of(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl, skipHome)
        viewModel
    }

    private val animatorHelper by lazy { BrowserTrackersAnimatorHelper() }

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    // Optimization to prevent against excessive work generating WebView previews; an existing job will be cancelled if a new one is launched
    private var bitmapGeneratorJob: Job? = null

    private val browserActivity
        get() = activity as? BrowserActivity

    private val tabsButton: MenuItem?
        get() = toolbar.menu.findItem(R.id.tabs)

    private val fireMenuButton: MenuItem?
        get() = toolbar.menu.findItem(R.id.fire)

    private val menuButton: ViewGroup?
        get() = appBarLayout.browserMenu

    private var webView: WebView? = null

    private val errorSnackbar: Snackbar by lazy {
        Snackbar.make(browserLayout, R.string.crashedWebViewErrorMessage, Snackbar.LENGTH_INDEFINITE)
            .setBehavior(NonDismissibleBehavior())
    }

    private val findInPageTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.userFindingInPage(findInPageInput.text.toString())
        }
    }

    private val omnibarInputTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), omnibarTextInput.hasFocus(), true)
        }
    }

    private val logoHidingListener by lazy { LogoHidingLayoutChangeLifecycleListener(ddgLogo) }

    private var alertDialog: AlertDialog? = null
    private var daxDialog: DaxDialog? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = BrowserTabFragmentRenderer()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_browser_tab, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        createPopupMenu()
        configureObservers()
        configureAppBar()
        configureWebView()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        configureKeyboardAwareLogoAnimation()
        configureShowTabSwitcherListener()
        configureLongClickOpensNewTabListener()

        if (savedInstanceState == null) {
            viewModel.onViewReady()
            messageFromPreviousTab?.let {
                processMessage(it)
            }
        }

        lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
            fun onStop() {
                if (isVisible) {
                    updateOrDeleteWebViewPreview()
                }
            }
        })
    }

    private fun processMessage(message: Message) {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()
        val tabsButton = tabsButton?.actionView as TabSwitcherButton
        tabsButton.animateCount()
        viewModel.onMessageProcessed()
    }

    private fun updateOrDeleteWebViewPreview() {
        val url = viewModel.url
        Timber.d("Updating or deleting WebView preview for $url")
        if (url == null) {
            viewModel.deleteTabPreview(tabId)
        } else {
            generateWebViewPreviewImage()
        }
    }

    private fun configureShowTabSwitcherListener() {
        tabsButton?.actionView?.setOnClickListener {
            launch { viewModel.userLaunchingTabSwitcher() }
        }
    }

    private fun configureLongClickOpensNewTabListener() {
        tabsButton?.actionView?.setOnLongClickListener {
            launch { viewModel.userRequestedOpeningNewTab() }
            return@setOnLongClickListener true
        }
    }

    private fun launchTabSwitcher() {
        val activity = activity ?: return
        startActivity(TabSwitcherActivity.intent(activity, tabId))
        activity.overridePendingTransition(R.anim.tab_anim_fade_in, R.anim.slide_to_bottom)
    }

    override fun onResume() {
        super.onResume()
        addTextChangedListeners()
        appBarLayout.setExpanded(true)
        viewModel.onViewResumed()
        logoHidingListener.onResume()

        // onResume can be called for a hidden/backgrounded fragment, ensure this tab is visible.
        if (fragmentIsVisible()) {
            viewModel.onViewVisible()
        }
    }

    override fun onPause() {
        daxDialog = null
        logoHidingListener.onPause()
        super.onPause()
    }

    private fun createPopupMenu() {
        popupMenu = BrowserPopupMenu(layoutInflater)
        val view = popupMenu.contentView
        popupMenu.apply {
            onMenuItemClicked(view.forwardPopupMenuItem) { viewModel.onUserPressedForward() }
            onMenuItemClicked(view.backPopupMenuItem) { activity?.onBackPressed() }
            onMenuItemClicked(view.refreshPopupMenuItem) { viewModel.onRefreshRequested() }
            onMenuItemClicked(view.newTabPopupMenuItem) { viewModel.userRequestedOpeningNewTab() }
            onMenuItemClicked(view.bookmarksPopupMenuItem) { browserActivity?.launchBookmarks() }
            onMenuItemClicked(view.addBookmarksPopupMenuItem) { launch { viewModel.onBookmarkAddRequested() } }
            onMenuItemClicked(view.findInPageMenuItem) { viewModel.onFindInPageSelected() }
            onMenuItemClicked(view.brokenSitePopupMenuItem) { viewModel.onBrokenSiteSelected() }
            onMenuItemClicked(view.settingsPopupMenuItem) { browserActivity?.launchSettings() }
            onMenuItemClicked(view.requestDesktopSiteCheckMenuItem) { viewModel.onDesktopSiteModeToggled(view.requestDesktopSiteCheckMenuItem.isChecked) }
            onMenuItemClicked(view.sharePageMenuItem) { viewModel.onShareSelected() }
            onMenuItemClicked(view.addToHome) { viewModel.onPinPageToHomeSelected() }
        }
    }

    private fun addHomeShortcut(homeShortcut: Command.AddHomeShortcut, context: Context) {
        val shortcutInfo = shortcutBuilder.buildPinnedPageShortcut(context, homeShortcut)
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
    }

    private fun configureObservers() {
        viewModel.autoCompleteViewState.observe(this, Observer<AutoCompleteViewState> {
            it?.let { renderer.renderAutocomplete(it) }
        })

        viewModel.globalLayoutState.observe(this, Observer<GlobalLayoutViewState> {
            it?.let { renderer.renderGlobalViewState(it) }
        })

        viewModel.browserViewState.observe(this, Observer<BrowserViewState> {
            it?.let { renderer.renderBrowserViewState(it) }
        })

        viewModel.loadingViewState.observe(this, Observer<LoadingViewState> {
            it?.let { renderer.renderLoadingIndicator(it) }
        })

        viewModel.omnibarViewState.observe(this, Observer<OmnibarViewState> {
            it?.let { renderer.renderOmnibar(it) }
        })

        viewModel.findInPageViewState.observe(this, Observer<FindInPageViewState> {
            it?.let { renderer.renderFindInPageState(it) }
        })

        viewModel.ctaViewState.observe(this, Observer {
            it?.let { renderer.renderCtaViewState(it) }
        })

        viewModel.command.observe(this, Observer {
            processCommand(it)
        })

        viewModel.survey.observe(this, Observer<Survey> {
            it.let { viewModel.onSurveyChanged(it) }
        })

        addTabsObserver()
    }

    private fun addTabsObserver() {
        viewModel.tabs.observe(this, Observer<List<TabEntity>> {
            it?.let { renderer.renderTabIcon(it) }
        })
    }

    private fun fragmentIsVisible(): Boolean {
        // using isHidden rather than isVisible, as isVisible will incorrectly return false when windowToken is not yet initialized.
        // changes on isHidden will be received in onHiddenChanged
        return !isHidden
    }

    private fun showHome() {
        errorSnackbar.dismiss()
        newTabLayout.show()
        showKeyboardImmediately()
        appBarLayout.setExpanded(true)
        webView?.onPause()
        webView?.hide()
        omnibarScrolling.disableOmnibarScrolling(toolbarContainer)
        logoHidingListener.onReadyToShowLogo()
    }

    private fun showBrowser() {
        newTabLayout.gone()
        webView?.show()
        webView?.onResume()
        omnibarScrolling.enableOmnibarScrolling(toolbarContainer)
    }

    fun submitQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    private fun navigate(url: String) {
        hideKeyboard()
        renderer.hideFindInPage()
        viewModel.registerDaxBubbleCtaDismissed()
        webView?.loadUrl(url)
    }

    fun onRefreshRequested() {
        viewModel.onRefreshRequested()
    }

    fun refresh() {
        webView?.reload()
    }

    private fun processCommand(it: Command?) {
        renderer.cancelAllAnimations()
        when (it) {
            is Command.Refresh -> refresh()
            is Command.OpenInNewTab -> {
                browserActivity?.openInNewTab(it.query)
            }
            is Command.OpenMessageInNewTab -> {
                browserActivity?.openMessageInNewTab(it.message)
            }
            is Command.OpenInNewBackgroundTab -> {
                openInNewBackgroundTab()
            }
            is Command.LaunchNewTab -> browserActivity?.launchNewTab()
            is Command.ShowBookmarkAddedConfirmation -> bookmarkAdded(it.bookmarkId, it.title, it.url)
            is Command.Navigate -> {
                navigate(it.url)
            }
            is Command.NavigateBack -> {
                webView?.goBackOrForward(-it.steps)
            }
            is Command.NavigateForward -> {
                webView?.goForward()
            }
            is Command.ResetHistory -> {
                resetWebView()
            }
            is Command.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${it.telephoneNumber}")
                openExternalDialog(intent, null, false)
            }
            is Command.SendEmail -> {
                val intent = Intent(Intent.ACTION_SENDTO)
                intent.data = Uri.parse(it.emailAddress)
                openExternalDialog(intent)
            }
            is Command.SendSms -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${it.telephoneNumber}"))
                openExternalDialog(intent)
            }
            is Command.ShowKeyboard -> {
                showKeyboard()
            }
            is Command.HideKeyboard -> {
                hideKeyboard()
            }
            is Command.BrokenSiteFeedback -> {
                browserActivity?.launchBrokenSiteFeedback(it.url)
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
            is Command.DismissFindInPage -> webView?.findAllAsync("")
            is Command.ShareLink -> launchSharePageChooser(it.url)
            is Command.CopyLink -> {
                clipboardManager.primaryClip = ClipData.newPlainText(null, it.url)
            }
            is Command.ShowFileChooser -> {
                launchFilePicker(it)
            }
            is Command.AddHomeShortcut -> {
                context?.let { context ->
                    addHomeShortcut(it, context)
                }
            }
            is Command.HandleExternalAppLink -> {
                openExternalDialog(it.appLink.intent, it.appLink.fallbackUrl, false)
            }
            is Command.LaunchSurvey -> launchSurvey(it.survey)
            is Command.LaunchAddWidget -> launchAddWidget()
            is Command.LaunchLegacyAddWidget -> launchLegacyAddWidget()
            is Command.RequiresAuthentication -> showAuthenticationDialog(it.request)
            is Command.SaveCredentials -> saveBasicAuthCredentials(it.request, it.credentials)
            is Command.GenerateWebViewPreviewImage -> generateWebViewPreviewImage()
            is Command.LaunchTabSwitcher -> launchTabSwitcher()
            is Command.ShowErrorWithAction -> showErrorSnackbar(it)
        }
    }

    private fun showErrorSnackbar(command: Command.ShowErrorWithAction) {
        //Snackbar is global and it should appear only the foreground fragment
        if (!errorSnackbar.view.isAttachedToWindow && isVisible) {
            errorSnackbar.setAction(R.string.crashedWebViewErrorAction) { command.action() }.show()
        }
    }

    private fun generateWebViewPreviewImage() {
        webView?.let { webView ->

            // if there's an existing job for generating a preview, cancel that in favor of the new request
            bitmapGeneratorJob?.cancel()

            bitmapGeneratorJob = launch {
                Timber.d("Generating WebView preview")
                try {
                    val preview = previewGenerator.generatePreview(webView)
                    val fileName = previewPersister.save(preview, tabId)
                    viewModel.updateTabPreview(tabId, fileName)
                    Timber.d("Saved and updated tab preview")
                } catch (e: Exception) {
                    Timber.d(e, "Failed to generate WebView preview")
                }
            }
        }
    }

    private fun openInNewBackgroundTab() {
        appBarLayout.setExpanded(true, true)
        viewModel.tabs.removeObservers(this)
        val view = tabsButton?.actionView as TabSwitcherButton
        view.increment {
            addTabsObserver()
        }
    }

    private fun openExternalDialog(intent: Intent, fallbackUrl: String? = null, useFirstActivityFound: Boolean = true) {
        context?.let {
            val pm = it.packageManager
            val activities = pm.queryIntentActivities(intent, 0)

            if (activities.isEmpty()) {
                if (fallbackUrl != null) {
                    webView?.loadUrl(fallbackUrl)
                } else {
                    showToast(R.string.unableToOpenLink)
                }
            } else {
                if (activities.size == 1 || useFirstActivityFound) {
                    val activity = activities.first()
                    val appTitle = activity.loadLabel(pm)
                    Timber.i("Exactly one app available for intent: $appTitle")
                    launchExternalAppDialog(it) { it.startActivity(intent) }
                } else {
                    val title = getString(R.string.openExternalApp)
                    val intentChooser = Intent.createChooser(intent, title)
                    launchExternalAppDialog(it) { it.startActivity(intentChooser) }
                }
            }
        }
    }

    private fun launchExternalAppDialog(context: Context, onClick: () -> Unit) {
        val isShowing = alertDialog?.isShowing

        if (isShowing != true) {
            alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.launchingExternalApp)
                .setMessage(getString(R.string.confirmOpenExternalApp))
                .setPositiveButton(R.string.yes) { _, _ ->
                    onClick()
                }
                .setNeutralButton(R.string.closeTab) { dialog, _ ->
                    dialog.dismiss()
                    launch {
                        viewModel.closeCurrentTab()
                        destroyWebView()
                    }
                }
                .setNegativeButton(R.string.no) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
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

    private fun showAuthenticationDialog(request: BasicAuthenticationRequest) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val dialog = HttpAuthenticationDialogFragment.createHttpAuthenticationDialog(request.site)
            dialog.show(fragmentManager, AUTHENTICATION_DIALOG_TAG)
            dialog.listener = viewModel
            dialog.request = request
        }
    }

    private fun saveBasicAuthCredentials(request: BasicAuthenticationRequest, credentials: BasicAuthenticationCredentials) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val webViewDatabase = WebViewDatabase.getInstance(context)
            webViewDatabase.setHttpAuthUsernamePassword(request.host, request.realm, credentials.username, credentials.password)
        } else {
            @Suppress("DEPRECATION")
            webView?.setHttpAuthUsernamePassword(request.host, request.realm, credentials.username, credentials.password)
        }
    }

    private fun configureAutoComplete() {
        val context = context ?: return
        autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(context)
        autoCompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                userSelectedAutocomplete(it)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            }
        )
        autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun configureAppBar() {
        toolbar.inflateMenu(R.menu.menu_browser_activity)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.fire -> {
                    browserActivity?.launchFire()
                    return@setOnMenuItemClickListener true
                }
                else -> return@setOnMenuItemClickListener false
            }
        }

        toolbar.privacyGradeButton.setOnClickListener {
            browserActivity?.launchPrivacyDashboard()
        }

        browserMenu.setOnClickListener {
            hideKeyboardImmediately()
            launchPopupMenu()
        }

        viewModel.privacyGrade.observe(this, Observer<PrivacyGrade> {
            Timber.d("Observed grade: $it")
            it?.let { privacyGrade ->
                val drawable = context?.getDrawable(privacyGrade.icon()) ?: return@let
                privacyGradeButton?.setImageDrawable(drawable)
                privacyGradeButton?.isEnabled = privacyGrade != PrivacyGrade.UNKNOWN
            }
        })
    }

    private fun configureFindInPage() {
        findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && findInPageInput.text.toString() != viewModel.findInPageViewState.value?.searchTerm) {
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
            OnFocusChangeListener { _, hasFocus: Boolean ->
                viewModel.onOmnibarInputStateChanged(omnibarTextInput.text.toString(), hasFocus, false)
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
        newTabLayout.layoutTransition?.apply {
            // we want layout transitions for when the size changes; we don't want them when items disappear (can cause glitch on call to action button)
            enableTransitionType(CHANGING)
            disableTransitionType(DISAPPEARING)
            setDuration(LAYOUT_TRANSITION_MS)
        }
        rootView.addOnLayoutChangeListener(logoHidingListener)
    }

    private fun userSelectedAutocomplete(suggestion: AutoCompleteSuggestion) {
        // send pixel before submitting the query and changing the autocomplete state to empty; otherwise will send the wrong params
        GlobalScope.launch {
            viewModel.fireAutocompletePixel(suggestion)
            withContext(Dispatchers.Main) {
                viewModel.onUserSubmittedQuery(suggestion.phrase)
            }
        }
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
            userAgentProvider = UserAgentProvider(it.settings.userAgentString, deviceInfo)

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
                setSupportMultipleWindows(true)
                disableWebSql(this)
                setSupportZoom(true)
            }

            it.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
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

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * Explicitly disable database to try protect against Magellan WebSQL/SQLite vulnerability
     */
    private fun disableWebSql(settings: WebSettings) {
        settings.databaseEnabled = false
    }

    private fun addTextChangedListeners() {
        findInPageInput.replaceTextChangedListener(findInPageTextWatcher)
        omnibarTextInput.replaceTextChangedListener(omnibarInputTextWatcher)
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        webView?.hitTestResult?.let {
            val target = getLongPressTarget(it) ?: return
            viewModel.userLongPressedInWebView(target, menu)
        }
    }

    /**
     * Use requestFocusNodeHref to get the a tag url for the touched image.
     */
    private fun getTargetUrlForImageSource(): String? {
        val handler = Handler()
        val message = handler.obtainMessage()

        webView?.requestFocusNodeHref(message)

        return message.data.getString(URL_BUNDLE_KEY)
    }

    private fun getLongPressTarget(hitTestResult: HitTestResult): LongPressTarget? {
        return when {
            hitTestResult.extra == null -> null
            hitTestResult.type == UNKNOWN_TYPE -> null
            hitTestResult.type == IMAGE_TYPE -> LongPressTarget(
                url = hitTestResult.extra,
                imageUrl = hitTestResult.extra,
                type = hitTestResult.type
            )
            hitTestResult.type == SRC_IMAGE_ANCHOR_TYPE -> LongPressTarget(
                url = getTargetUrlForImageSource(),
                imageUrl = hitTestResult.extra,
                type = hitTestResult.type
            )
            else -> LongPressTarget(
                url = hitTestResult.extra,
                type = hitTestResult.type
            )
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        webView?.hitTestResult?.let {
            val target = getLongPressTarget(it)
            if (target != null && viewModel.userSelectedItemFromLongPressMenu(target, item)) {
                return true
            }
        }

        return super.onContextItemSelected(item)
    }

    private fun launchPopupMenu() {
        popupMenu.show(rootView, toolbar)
    }

    private fun bookmarkAdded(bookmarkId: Long, title: String?, url: String?) {

        Snackbar.make(rootView, R.string.bookmarkEdited, Snackbar.LENGTH_LONG)
            .setAction(R.string.edit) {
                val addBookmarkDialog = EditBookmarkDialogFragment.instance(bookmarkId, title, url)
                addBookmarkDialog.show(childFragmentManager, ADD_BOOKMARK_FRAGMENT_TAG)
                addBookmarkDialog.listener = viewModel
            }
            .show()
    }

    private fun launchSharePageChooser(url: String) {
        activity?.share(url, "")
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

    private fun showKeyboardImmediately() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibarTextInput?.showKeyboard()
        }
    }

    private fun showKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibarTextInput?.showKeyboard() }
        }
    }

    /**
     * Attempting to save the WebView's state can result in a TransactionTooLargeException being thrown.
     * This will only happen if the bundle size is too large - but the exact size is undefined.
     * Instead of saving using normal Android state mechanism - use our own implementation instead.
     */
    override fun onSaveInstanceState(bundle: Bundle) {
        viewModel.saveWebViewState(webView, tabId)
        super.onSaveInstanceState(bundle)
    }

    override fun onViewStateRestored(bundle: Bundle?) {
        viewModel.restoreWebViewState(webView, omnibarTextInput.text.toString())
        viewModel.determineShowBrowser()
        super.onViewStateRestored(bundle)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            viewModel.onViewHidden()
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
        ddgLogo.setImageResource(R.drawable.logo_full)
        if (ctaContainer.isNotEmpty()) {
            renderer.renderHomeCta()
        }
    }

    fun onBackPressed(): Boolean {
        if (!isAdded) return false
        return viewModel.onUserPressedBack()
    }

    private fun resetWebView() {
        destroyWebView()
        configureWebView()
    }

    override fun onDestroy() {
        supervisorJob.cancel()
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
            userAgent = userAgentProvider.getUserAgent(),
            subfolder = Environment.DIRECTORY_DOWNLOADS
        )

        downloadFileWithPermissionCheck()
    }

    private fun requestImageDownload(url: String) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            userAgent = userAgentProvider.getUserAgent(),
            subfolder = Environment.DIRECTORY_PICTURES
        )

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
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
                        fileDownloadNotificationManager.showDownloadFinishedNotification(file.name, uri, mimeType)
                    }
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

    private fun launchSurvey(survey: Survey) {
        context?.let {
            startActivity(SurveyActivity.intent(it, survey))
        }
    }

    @SuppressLint("NewApi")
    private fun launchAddWidget() {
        val context = context ?: return
        val provider = ComponentName(context, SearchWidgetLight::class.java)
        AppWidgetManager.getInstance(context).requestPinAppWidget(provider, null, null)
    }

    private fun launchLegacyAddWidget() {
        val context = context ?: return
        val options = ActivityOptions.makeSceneTransitionAnimation(activity).toBundle()
        startActivity(AddWidgetInstructionsActivity.intent(context), options)
    }

    companion object {

        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val URL_EXTRA_ARG = "URL_EXTRA_ARG"
        private const val SKIP_HOME_ARG = "SKIP_HOME_ARG"

        private const val ADD_BOOKMARK_FRAGMENT_TAG = "ADD_BOOKMARK"
        private const val KEYBOARD_DELAY = 200L
        private const val LAYOUT_TRANSITION_MS = 200L

        private const val REQUEST_CODE_CHOOSE_FILE = 100
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200

        private const val URL_BUNDLE_KEY = "url"

        private const val AUTHENTICATION_DIALOG_TAG = "AUTH_DIALOG_TAG"
        private const val DAX_DIALOG_DIALOG_TAG = "DAX_DIALOG_TAG"

        private const val MAX_PROGRESS = 100
        private const val TRACKERS_INI_DELAY = 500L
        private const val TRACKERS_SECONDARY_DELAY = 200L

        fun newInstance(tabId: String, query: String? = null, skipHome: Boolean): BrowserTabFragment {
            val fragment = BrowserTabFragment()
            val args = Bundle()
            args.putString(TAB_ID_ARG, tabId)
            args.putBoolean(SKIP_HOME_ARG, skipHome)
            query.let {
                args.putString(URL_EXTRA_ARG, query)
            }
            fragment.arguments = args
            return fragment
        }
    }

    inner class BrowserTabFragmentRenderer {

        private var lastSeenOmnibarViewState: OmnibarViewState? = null
        private var lastSeenLoadingViewState: LoadingViewState? = null
        private var lastSeenFindInPageViewState: FindInPageViewState? = null
        private var lastSeenBrowserViewState: BrowserViewState? = null
        private var lastSeenGlobalViewState: GlobalLayoutViewState? = null
        private var lastSeenAutoCompleteViewState: AutoCompleteViewState? = null
        private var lastSeenCtaViewState: CtaViewState? = null

        fun renderAutocomplete(viewState: AutoCompleteViewState) {
            renderIfChanged(viewState, lastSeenAutoCompleteViewState) {
                lastSeenAutoCompleteViewState = viewState

                if (viewState.showSuggestions) {
                    autoCompleteSuggestionsList.show()
                    autoCompleteSuggestionsAdapter.updateData(viewState.searchResults.query, viewState.searchResults.suggestions)
                } else {
                    autoCompleteSuggestionsList.gone()
                }
            }
        }

        fun renderOmnibar(viewState: OmnibarViewState) {
            renderIfChanged(viewState, lastSeenOmnibarViewState) {
                lastSeenOmnibarViewState = viewState

                if (viewState.isEditing) {
                    omniBarContainer.background = null
                    cancelAllAnimations()
                } else {
                    omniBarContainer.setBackgroundResource(R.drawable.omnibar_field_background)
                }

                if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
                    omnibarTextInput.setText(viewState.omnibarText)
                    appBarLayout.setExpanded(true, true)
                    if (viewState.shouldMoveCaretToEnd) {
                        omnibarTextInput.setSelection(viewState.omnibarText.length)
                    }
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun renderLoadingIndicator(viewState: LoadingViewState) {
            renderIfChanged(viewState, lastSeenLoadingViewState) {
                lastSeenLoadingViewState = viewState

                pageLoadingIndicator.apply {
                    if (viewState.isLoading) show()
                    smoothProgressAnimator.onNewProgress(viewState.progress) { if (!viewState.isLoading) hide() }
                }

                if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest) && privacySettingsStore.privacyOn) {

                    if (lastSeenOmnibarViewState?.isEditing == true) {
                        cancelAllAnimations()
                    }

                    if (viewState.progress == MAX_PROGRESS) {
                        createTrackersAnimation()
                    }
                }
            }
        }

        private fun createTrackersAnimation() {
            launch {
                delay(TRACKERS_INI_DELAY)
                viewModel.refreshCta()
                delay(TRACKERS_SECONDARY_DELAY)
                if (lastSeenOmnibarViewState?.isEditing != true) {
                    val site = viewModel.siteLiveData.value
                    val events = site?.trackingEvents

                    activity?.let { activity ->
                        animatorHelper.startTrackersAnimation(
                            lastSeenCtaViewState?.cta,
                            activity,
                            networksContainer,
                            omnibarViews(),
                            events
                        )
                    }
                }
            }
        }

        fun cancelAllAnimations() {
            if (variantManager.getVariant().hasFeature(VariantManager.VariantFeature.ConceptTest)) {
                animatorHelper.cancelAnimations()
                networksContainer.alpha = 0f
                clearTextButton.alpha = 1f
                omnibarTextInput.alpha = 1f
                privacyGradeButton.alpha = 1f
            }
        }

        private fun omnibarViews(): List<View> = listOf(clearTextButton, omnibarTextInput, privacyGradeButton)

        fun renderGlobalViewState(viewState: GlobalLayoutViewState) {
            if (lastSeenGlobalViewState is GlobalLayoutViewState.Invalidated &&
                viewState is GlobalLayoutViewState.Browser
            ) {
                throw IllegalStateException("Invalid state transition")
            }

            renderIfChanged(viewState, lastSeenGlobalViewState) {
                lastSeenGlobalViewState = viewState

                when (viewState) {
                    is GlobalLayoutViewState.Browser -> {
                        if (viewState.isNewTabState) {
                            browserLayout.hide()
                        } else {
                            browserLayout.show()
                        }
                    }
                    is GlobalLayoutViewState.Invalidated -> destroyWebView()
                }
            }
        }

        fun renderBrowserViewState(viewState: BrowserViewState) {
            renderIfChanged(viewState, lastSeenBrowserViewState) {
                val browserShowing = viewState.browserShowing

                val browserShowingChanged = viewState.browserShowing != lastSeenBrowserViewState?.browserShowing
                lastSeenBrowserViewState = viewState
                if (browserShowingChanged) {
                    if (browserShowing) {
                        showBrowser()
                    } else {
                        showHome()
                    }
                }

                toggleDesktopSiteMode(viewState.isDesktopBrowsingMode)
                renderToolbarMenus(viewState)
                renderPopupMenus(browserShowing, viewState)
                renderFullscreenMode(viewState)
            }
        }

        private fun renderFullscreenMode(viewState: BrowserViewState) {
            activity?.isImmersiveModeEnabled()?.let {
                if (viewState.isFullScreen) {
                    if (!it) goFullScreen()
                } else {
                    if (it) exitFullScreen()
                }
            }
        }

        private fun renderPopupMenus(browserShowing: Boolean, viewState: BrowserViewState) {
            popupMenu.contentView.apply {
                backPopupMenuItem.isEnabled = viewState.canGoBack
                forwardPopupMenuItem.isEnabled = viewState.canGoForward
                refreshPopupMenuItem.isEnabled = browserShowing
                newTabPopupMenuItem.isEnabled = browserShowing
                addBookmarksPopupMenuItem?.isEnabled = viewState.canAddBookmarks
                sharePageMenuItem?.isEnabled = viewState.canSharePage
                brokenSitePopupMenuItem?.isEnabled = viewState.canReportSite
                requestDesktopSiteCheckMenuItem?.isEnabled = viewState.canChangeBrowsingMode

                addToHome?.let {
                    it.visibility = if (viewState.addToHomeVisible) VISIBLE else GONE
                    it.isEnabled = viewState.addToHomeEnabled
                }
            }
        }

        private fun renderToolbarMenus(viewState: BrowserViewState) {
            privacyGradeButton?.isVisible = viewState.showPrivacyGrade
            clearTextButton?.isVisible = viewState.showClearButton
            tabsButton?.isVisible = viewState.showTabsButton
            fireMenuButton?.isVisible = viewState.showFireButton
            menuButton?.isVisible = viewState.showMenuButton
        }

        fun renderFindInPageState(viewState: FindInPageViewState) {
            if (viewState == lastSeenFindInPageViewState) {
                return
            }

            lastSeenFindInPageViewState = viewState

            if (viewState.visible) {
                showFindInPageView(viewState)
            } else {
                hideFindInPage()
            }

            popupMenu.contentView.findInPageMenuItem?.isEnabled = viewState.canFindInPage
        }

        fun renderTabIcon(tabs: List<TabEntity>) {
            context?.let {
                val button = tabsButton?.actionView as TabSwitcherButton
                button.count = tabs.count()
                button.hasUnread = tabs.firstOrNull { !it.viewed } != null
            }
        }

        fun renderCtaViewState(viewState: CtaViewState) {
            if (isHidden) {
                return
            }

            renderIfChanged(viewState, lastSeenCtaViewState) {
                ddgLogo.show()
                lastSeenCtaViewState = viewState
                if (viewState.cta != null) {
                    showCta(viewState.cta)
                } else {
                    hideHomeCta()
                    hideDaxCta()
                }
            }
        }

        private fun showCta(configuration: Cta) {
            when (configuration) {
                is HomePanelCta -> showHomeCta(configuration)
                is DaxBubbleCta -> showDaxCta(configuration)
                is DaxDialogCta -> showDaxDialogCta(configuration)
            }

            viewModel.onCtaShown()
        }

        private fun showDaxDialogCta(configuration: DaxDialogCta) {
            hideHomeCta()
            hideDaxCta()
            val container = networksContainer
            activity?.let { activity ->
                daxDialog?.dismiss()
                daxDialog = configuration.createCta(activity).apply {
                    setHideClickListener {
                        dismiss()
                        launchHideTipsDialog(activity, configuration)
                    }
                    setDismissListener {
                        if (configuration is DaxDialogCta.DaxTrackersBlockedCta) {
                            animatorHelper.finishTrackerAnimation(omnibarViews(), container)
                        }
                        viewModel.onUserDismissedCta()
                    }
                    setPrimaryCtaClickListener {
                        viewModel.onUserClickCtaOkButton()
                        if (configuration is DaxDialogCta.DaxMainNetworkCta) {
                            setPrimaryCtaClickListener {
                                viewModel.onUserClickCtaOkButton()
                                dismiss()
                            }
                            configuration.setSecondDialog(this, activity)
                            viewModel.onManualCtaShown(configuration)
                        } else {
                            dismiss()
                        }
                    }
                    show(activity.supportFragmentManager, DAX_DIALOG_DIALOG_TAG)
                }
            }
        }

        private fun launchHideTipsDialog(context: Context, cta: Cta) {
            AlertDialog.Builder(context)
                .setTitle(R.string.hideTipsTitle)
                .setMessage(getString(R.string.hideTipsText))
                .setPositiveButton(R.string.hideTipsButton) { dialog, _ ->
                    dialog.dismiss()
                    launch {
                        ctaViewModel.hideTipsForever(cta)
                    }
                }
                .setNegativeButton(android.R.string.no) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        private fun showDaxCta(configuration: DaxBubbleCta) {
            ddgLogo.hide()
            hideHomeCta()
            configuration.showCta(daxCtaContainer)
        }

        private fun showHomeCta(configuration: HomePanelCta) {
            hideDaxCta()
            if (ctaContainer.isEmpty()) {
                renderHomeCta()
            } else {
                configuration.showCta(ctaContainer)
            }
        }

        private fun hideDaxCta() {
            dialogTextCta.cancelAnimation()
            daxCtaContainer.hide()
        }

        private fun hideHomeCta() {
            ctaContainer.gone()
        }

        fun renderHomeCta() {
            val context = context ?: return
            val cta = lastSeenCtaViewState?.cta ?: return
            val configuration = if (cta is HomePanelCta) cta else return

            ctaContainer.removeAllViews()

            inflate(context, R.layout.include_cta, ctaContainer)
            logoHidingListener.callToActionView = ctaContainer

            configuration.showCta(ctaContainer)
            ctaContainer.ctaOkButton.setOnClickListener {
                viewModel.onUserClickCtaOkButton()
            }

            ctaContainer.ctaDismissButton.setOnClickListener {
                viewModel.onUserDismissedCta()
            }

            ConstraintSet().also {
                it.clone(newTabLayout)
                it.connect(ddgLogo.id, ConstraintSet.BOTTOM, ctaContainer.id, ConstraintSet.TOP, 0)
                it.applyTo(newTabLayout)
            }
        }

        fun hideFindInPage() {
            if (findInPageContainer.visibility != GONE) {
                focusDummy.requestFocus()
                findInPageContainer.gone()
                findInPageInput.hideKeyboard()
            }
        }

        private fun showFindInPageView(viewState: FindInPageViewState) {

            if (findInPageContainer.visibility != VISIBLE) {
                findInPageContainer.show()
                findInPageInput.postDelayed(KEYBOARD_DELAY) {
                    findInPageInput?.showKeyboard()
                }
            }

            if (viewState.showNumberMatches) {
                findInPageMatches.text = getString(R.string.findInPageMatches, viewState.activeMatchIndex, viewState.numberMatches)
                findInPageMatches.show()
            } else {
                findInPageMatches.hide()
            }
        }

        private fun toggleDesktopSiteMode(isDesktopSiteMode: Boolean) {
            webView?.settings?.userAgentString = userAgentProvider.getUserAgent(isDesktopSiteMode)
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

        private fun shouldUpdateOmnibarTextInput(viewState: OmnibarViewState, omnibarInput: String?) =
            (!viewState.isEditing || omnibarInput.isNullOrEmpty()) && omnibarTextInput.isDifferent(omnibarInput)
    }
}