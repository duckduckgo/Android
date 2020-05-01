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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.text.Editable
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.OnFocusChangeListener
import android.view.View.VISIBLE
import android.view.View.inflate
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.HitTestResult.IMAGE_TYPE
import android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
import android.webkit.WebView.HitTestResult.UNKNOWN_TYPE
import android.webkit.WebViewDatabase
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.isEmpty
import androidx.core.view.isInvisible
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.transaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.bookmarks.ui.EditBookmarkDialogFragment
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.browser.BrowserTabViewModel.AutoCompleteViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.BrowserViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.CtaViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.FindInPageViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.LoadingViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.OmnibarViewState
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.downloader.FileDownloadNotificationManager
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.downloader.FileDownloader.FileDownloadListener
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
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.DaxBubbleCta
import com.duckduckgo.app.cta.ui.DaxDialogCta
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.cta.ui.HomeTopPanelCta
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.global.model.orderedTrackingEntities
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
import com.duckduckgo.app.tabs.ui.TabSwitcherBottomBarFeatureActivity
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
import com.duckduckgo.widget.SearchWidgetLight
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_browser_tab.autoCompleteSuggestionsList
import kotlinx.android.synthetic.main.fragment_browser_tab.bottomNavigationBar
import kotlinx.android.synthetic.main.fragment_browser_tab.browserLayout
import kotlinx.android.synthetic.main.fragment_browser_tab.focusDummy
import kotlinx.android.synthetic.main.fragment_browser_tab.rootView
import kotlinx.android.synthetic.main.fragment_browser_tab.webViewContainer
import kotlinx.android.synthetic.main.fragment_browser_tab.webViewFullScreenContainer
import kotlinx.android.synthetic.main.include_add_widget_instruction_buttons.view.closeButton
import kotlinx.android.synthetic.main.include_cta_buttons.view.ctaDismissButton
import kotlinx.android.synthetic.main.include_cta_buttons.view.ctaOkButton
import kotlinx.android.synthetic.main.include_dax_dialog_cta.daxCtaContainer
import kotlinx.android.synthetic.main.include_dax_dialog_cta.dialogTextCta
import kotlinx.android.synthetic.main.include_find_in_page.closeFindInPagePanel
import kotlinx.android.synthetic.main.include_find_in_page.findInPageContainer
import kotlinx.android.synthetic.main.include_find_in_page.findInPageInput
import kotlinx.android.synthetic.main.include_find_in_page.findInPageMatches
import kotlinx.android.synthetic.main.include_find_in_page.nextSearchTermButton
import kotlinx.android.synthetic.main.include_find_in_page.previousSearchTermButton
import kotlinx.android.synthetic.main.include_new_browser_tab.ctaContainer
import kotlinx.android.synthetic.main.include_new_browser_tab.ctaTopContainer
import kotlinx.android.synthetic.main.include_new_browser_tab.ddgLogo
import kotlinx.android.synthetic.main.include_new_browser_tab.newTabLayout
import kotlinx.android.synthetic.main.include_omnibar_toolbar.*
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.browserMenu
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.fireIconMenu
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.privacyGradeButton
import kotlinx.android.synthetic.main.include_omnibar_toolbar.view.tabsMenu
import kotlinx.android.synthetic.main.layout_browser_bottom_navigation_bar.bottomBarBookmarksItemOne
import kotlinx.android.synthetic.main.layout_browser_bottom_navigation_bar.bottomBarFireItem
import kotlinx.android.synthetic.main.layout_browser_bottom_navigation_bar.bottomBarOverflowItem
import kotlinx.android.synthetic.main.layout_browser_bottom_navigation_bar.bottomBarSearchItem
import kotlinx.android.synthetic.main.layout_browser_bottom_navigation_bar.bottomBarTabsItem
import kotlinx.android.synthetic.main.popup_window_browser_bottom_tab_menu.view.sharePopupMenuItem
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.longToast
import org.jetbrains.anko.share
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

class BrowserTabFragment : Fragment(), FindListener, CoroutineScope, DaxDialogListener {

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

    val tabId get() = requireArguments()[TAB_ID_ARG] as String

    lateinit var userAgentProvider: UserAgentProvider

    var messageFromPreviousTab: Message? = null

    private val initialUrl get() = requireArguments().getString(URL_EXTRA_ARG)

    private val skipHome get() = requireArguments().getBoolean(SKIP_HOME_ARG)

    private lateinit var popupMenu: BrowserPopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private lateinit var renderer: BrowserTabFragmentRenderer

    private lateinit var decorator: BrowserTabFragmentDecorator

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProvider(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl, skipHome)
        viewModel
    }

    private val animatorHelper by lazy { BrowserTrackersAnimatorHelper() }

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    // Optimization to prevent against excessive work generating WebView previews; an existing job will be cancelled if a new one is launched
    private var bitmapGeneratorJob: Job? = null

    private val browserActivity
        get() = activity as? BrowserActivity

    private val tabsButton: TabSwitcherButton?
        get() = appBarLayout.tabsMenu

    private val fireMenuButton: ViewGroup?
        get() = appBarLayout.fireIconMenu

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

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeDaxDialogFromActivity()
        renderer = BrowserTabFragmentRenderer()
        decorator = BrowserTabFragmentDecorator()
        if (savedInstanceState != null) {
            updateFragmentListener()
        }
    }

    private fun updateFragmentListener() {
        val fragment = fragmentManager?.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG) as? DownloadConfirmationFragment
        fragment?.downloadListener = createDownloadListener()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_browser_tab, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        configureObservers()
        configurePrivacyGrade()
        configureWebView()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        configureKeyboardAwareLogoAnimation()

        decorateWithFeatures()

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

    private fun getDaxDialogFromActivity(): Fragment? = activity?.supportFragmentManager?.findFragmentByTag(DAX_DIALOG_DIALOG_TAG)

    private fun removeDaxDialogFromActivity() {
        val fragment = getDaxDialogFromActivity()
        fragment?.let {
            activity?.supportFragmentManager?.transaction { remove(it) }
        }
    }

    private fun processMessage(message: Message) {
        val transport = message.obj as WebView.WebViewTransport
        transport.webView = webView
        message.sendToTarget()

        decorator.animateTabsCount()
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

    private fun launchTabSwitcher() {
        val activity = activity ?: return
        if (isBottomNavigationFeatureEnabled()) {
            startActivity(TabSwitcherBottomBarFeatureActivity.intent(activity, tabId))
        } else {
            startActivity(TabSwitcherActivity.intent(activity, tabId))
        }

        activity.overridePendingTransition(R.anim.tab_anim_fade_in, R.anim.slide_to_bottom)
    }

    override fun onResume() {
        super.onResume()

        appBarLayout.setExpanded(true)
        viewModel.onViewResumed()
        logoHidingListener.onResume()

        // onResume can be called for a hidden/backgrounded fragment, ensure this tab is visible.
        if (fragmentIsVisible()) {
            viewModel.onViewVisible()
        }

        addTextChangedListeners()
    }

    override fun onPause() {
        logoHidingListener.onPause()
        dismissDownloadFragment()
        super.onPause()
    }

    private fun dismissDownloadFragment() {
        val fragment = fragmentManager?.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG) as? DownloadConfirmationFragment
        fragment?.dismiss()
    }

    private fun addHomeShortcut(homeShortcut: Command.AddHomeShortcut, context: Context) {
        val shortcutInfo = shortcutBuilder.buildPinnedPageShortcut(context, homeShortcut)
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
    }

    private fun configureObservers() {
        viewModel.autoCompleteViewState.observe(viewLifecycleOwner, Observer<AutoCompleteViewState> {
            it?.let { renderer.renderAutocomplete(it) }
        })

        viewModel.globalLayoutState.observe(viewLifecycleOwner, Observer<GlobalLayoutViewState> {
            it?.let { renderer.renderGlobalViewState(it) }
        })

        viewModel.browserViewState.observe(viewLifecycleOwner, Observer<BrowserViewState> {
            it?.let { renderer.renderBrowserViewState(it) }
        })

        viewModel.loadingViewState.observe(viewLifecycleOwner, Observer<LoadingViewState> {
            it?.let { renderer.renderLoadingIndicator(it) }
        })

        viewModel.omnibarViewState.observe(viewLifecycleOwner, Observer<OmnibarViewState> {
            it?.let { renderer.renderOmnibar(it) }
        })

        viewModel.findInPageViewState.observe(viewLifecycleOwner, Observer<FindInPageViewState> {
            it?.let { renderer.renderFindInPageState(it) }
        })

        viewModel.ctaViewState.observe(viewLifecycleOwner, Observer {
            it?.let { renderer.renderCtaViewState(it) }
        })

        viewModel.command.observe(viewLifecycleOwner, Observer {
            processCommand(it)
        })

        viewModel.survey.observe(viewLifecycleOwner, Observer<Survey> {
            it.let { viewModel.onSurveyChanged(it) }
        })

        addTabsObserver()
    }

    private fun addTabsObserver() {
        viewModel.tabs.observe(viewLifecycleOwner, Observer<List<TabEntity>> {
            it?.let {
                decorator.renderTabIcon(it)
            }
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
        if (it !is Command.DaxCommand) {
            renderer.cancelAllAnimations()
        }
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
            is Command.ShowFireproofWebSiteConfirmation -> fireproofWebsiteConfirmation(it.fireproofWebsiteEntity)
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
                launchBrokenSiteFeedback(it.url, it.blockedTrackers, it.surrogates, it.httpsUpgraded)
            }
            is Command.ShowFullScreen -> {
                webViewFullScreenContainer.addView(
                    it.view, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            is Command.DownloadImage -> requestImageDownload(it.url, it.requestUserConfirmation)
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
            is Command.DaxCommand.FinishTrackerAnimation -> finishTrackerAnimation()
            is Command.DaxCommand.HideDaxDialog -> showHideTipsDialog(it.cta)
        }
    }

    private fun launchBrokenSiteFeedback(url: String, blockedTrackers: String, surrogates: String, upgradedHttps: Boolean) {
        context?.let {
            val options = ActivityOptions.makeSceneTransitionAnimation(browserActivity).toBundle()
            startActivity(BrokenSiteActivity.intent(it, url, blockedTrackers, surrogates, upgradedHttps), options)
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
        decorator.updateBottomBarVisibility(true, true)
        viewModel.tabs.removeObservers(this)
        decorator.incrementTabs()
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

    private fun isBottomNavigationFeatureEnabled() =
        variantManager.getVariant().hasFeature(VariantManager.VariantFeature.BottomBarNavigation)

    private fun decorateWithFeatures() {
        if (isBottomNavigationFeatureEnabled()) {
            decorator.decorateWithBottomBarSearch()
        } else {
            decorator.decorateWithToolbar()
        }
    }

    private fun configurePrivacyGrade() {
        toolbar.privacyGradeButton.setOnClickListener {
            browserActivity?.launchPrivacyDashboard()
        }

        viewModel.privacyGrade.observe(viewLifecycleOwner, Observer<PrivacyGrade> {
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
                if (!hasFocus) {
                    omnibarTextInput.hideKeyboard()
                    focusDummy.requestFocus()
                }
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
                requestFileDownload(url, contentDisposition, mimeType, true)
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

    private fun bookmarkAdded(bookmarkId: Long, title: String?, url: String?) {
        Snackbar.make(browserLayout, R.string.bookmarkEdited, Snackbar.LENGTH_LONG)
            .setAction(R.string.edit) {
                val addBookmarkDialog = EditBookmarkDialogFragment.instance(bookmarkId, title, url)
                addBookmarkDialog.show(childFragmentManager, ADD_BOOKMARK_FRAGMENT_TAG)
                addBookmarkDialog.listener = viewModel
            }
            .show()
    }

    private fun fireproofWebsiteConfirmation(entity: FireproofWebsiteEntity) {
        Snackbar.make(
            rootView,
            HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteSnackbarConfirmation, entity.website()), FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.fireproofWebsiteSnackbarAction) {
                viewModel.onFireproofWebsiteSnackbarUndoClicked(entity)
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

    private fun requestFileDownload(url: String, contentDisposition: String, mimeType: String, requestUserConfirmation: Boolean) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            userAgent = userAgentProvider.getUserAgent(),
            subfolder = Environment.DIRECTORY_DOWNLOADS
        )

        if (hasWriteStoragePermission()) {
            downloadFile(requestUserConfirmation)
        } else {
            requestWriteStoragePermission()
        }
    }

    private fun requestImageDownload(url: String, requestUserConfirmation: Boolean) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            userAgent = userAgentProvider.getUserAgent(),
            subfolder = Environment.DIRECTORY_PICTURES
        )

        if (hasWriteStoragePermission()) {
            downloadFile(requestUserConfirmation)
        } else {
            requestWriteStoragePermission()
        }
    }

    @AnyThread
    private fun downloadFile(requestUserConfirmation: Boolean) {
        val pendingDownload = pendingFileDownload
        pendingFileDownload = null

        if (pendingDownload == null) {
            return
        }

        val downloadListener = createDownloadListener()
        if (requestUserConfirmation) {
            requestDownloadConfirmation(pendingDownload, downloadListener)
        } else {
            completeDownload(pendingDownload, downloadListener)
        }
    }

    private fun createDownloadListener(): FileDownloadListener {
        return object : FileDownloadListener {
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
        }
    }

    private fun requestDownloadConfirmation(pendingDownload: PendingFileDownload, downloadListener: FileDownloadListener) {
        fragmentManager?.let {
            if (!it.isStateSaved) {
                DownloadConfirmationFragment.instance(pendingDownload, downloadListener).show(it, DOWNLOAD_CONFIRMATION_TAG)
            }
        }
    }

    private fun completeDownload(pendingDownload: PendingFileDownload, callback: FileDownloadListener) {
        thread {
            fileDownloader.download(pendingDownload, callback)
        }
    }

    private fun launchFilePicker(command: Command.ShowFileChooser) {
        pendingUploadTask = command.filePathCallback
        val canChooseMultipleFiles = command.fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(command.fileChooserParams.acceptTypes, canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if ((grantResults.isNotEmpty()) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.i("Write external storage permission granted")
                downloadFile(requestUserConfirmation = false)
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

    private fun finishTrackerAnimation() {
        animatorHelper.finishTrackerAnimation(omnibarViews(), networksContainer)
    }

    private fun showHideTipsDialog(cta: Cta) {
        context?.let {
            launchHideTipsDialog(it, cta)
        }
    }

    override fun onDaxDialogDismiss() {
        viewModel.onDaxDialogDismissed()
    }

    override fun onDaxDialogHideClick() {
        viewModel.onUserHideDaxDialog()
    }

    override fun onDaxDialogPrimaryCtaClick() {
        viewModel.onUserClickCtaOkButton()
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

    fun omnibarViews(): List<View> = listOf(clearTextButton, omnibarTextInput, privacyGradeButton, searchIcon)

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
        private const val DOWNLOAD_CONFIRMATION_TAG = "DOWNLOAD_CONFIRMATION_TAG"
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

    inner class BrowserTabFragmentDecorator {
        fun decorateToolbar(viewState: BrowserViewState) {
            if (!variantManager.getVariant().hasFeature(VariantManager.VariantFeature.BottomBarNavigation)) {
                decorator.decorateToolbarActions(viewState)
            }
        }

        private fun decorateToolbarActions(viewState: BrowserViewState) {
            tabsButton?.isVisible = viewState.showTabsButton
            fireMenuButton?.isVisible = viewState.showFireButton
            menuButton?.isVisible = viewState.showMenuButton
        }

        fun decorateWithToolbar() {
            hideBottomBar()
            decorateAppBarWithToolbarOnly()
            createPopupMenuWithToolbarOnly()
            configureShowTabSwitcherListenerWithToolbarOnly()
            configureLongClickOpensNewTabListenerWithToolbarOnly()
            removeUnnecessaryLayoutBehaviour()
        }

        fun decorateWithBottomBarSearch() {
            bindBottomBarButtons()
            decorateAppBarWithBottomBar()
            createPopupMenuWithBottomBar()
            configureShowTabSwitcherListenerWithBottomBarNavigationOnly()
            configureLongClickOpensNewTabListenerWithBottomBarNavigationOnly()
        }

        private fun decorateAppBarWithToolbarOnly() {
            fireMenuButton?.show()
            fireMenuButton?.setOnClickListener {
                browserActivity?.launchFire()
                pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_FIRE_PRESSED.pixelName, variantManager.getVariant().key))
            }

            tabsButton?.show()
        }

        private fun decorateAppBarWithBottomBar() {
            menuButton?.gone()
            tabsButton?.gone()
            fireMenuButton?.gone()
        }

        private fun createPopupMenuWithToolbarOnly() {
            popupMenu = BrowserPopupMenu(layoutInflater, variantManager.getVariant())
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.forwardPopupMenuItem) { viewModel.onUserPressedForward() }
                onMenuItemClicked(view.backPopupMenuItem) { activity?.onBackPressed() }
                onMenuItemClicked(view.refreshPopupMenuItem) {
                    viewModel.onRefreshRequested()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_REFRESH_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onMenuItemClicked(view.newTabPopupMenuItem) {
                    viewModel.userRequestedOpeningNewTab()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_NEW_TAB_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onMenuItemClicked(view.bookmarksPopupMenuItem) {
                    browserActivity?.launchBookmarks()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_BOOKMARKS_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onMenuItemClicked(view.fireproofWebsitePopupMenuItem) { launch { viewModel.onFireproofWebsiteClicked() } }
                onMenuItemClicked(view.addBookmarksPopupMenuItem) { launch { viewModel.onBookmarkAddRequested() } }
                onMenuItemClicked(view.findInPageMenuItem) { viewModel.onFindInPageSelected() }
                onMenuItemClicked(view.brokenSitePopupMenuItem) { viewModel.onBrokenSiteSelected() }
                onMenuItemClicked(view.settingsPopupMenuItem) { browserActivity?.launchSettings() }
                onMenuItemClicked(view.requestDesktopSiteCheckMenuItem) { viewModel.onDesktopSiteModeToggled(view.requestDesktopSiteCheckMenuItem.isChecked) }
                onMenuItemClicked(view.sharePageMenuItem) { viewModel.onShareSelected() }
                onMenuItemClicked(view.addToHome) { viewModel.onPinPageToHomeSelected() }
            }
            browserMenu.setOnClickListener {
                hideKeyboardImmediately()
                launchTopAnchoredPopupMenu()
            }
        }

        private fun launchTopAnchoredPopupMenu() {
            popupMenu.show(rootView, toolbar)
            pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_POPUP_OPENED.pixelName, variantManager.getVariant().key))
        }

        private fun createPopupMenuWithBottomBar() {
            popupMenu = BrowserPopupMenu(layoutInflater, variantManager.getVariant())
            val view = popupMenu.contentView
            popupMenu.apply {
                onMenuItemClicked(view.forwardPopupMenuItem) { viewModel.onUserPressedForward() }
                onMenuItemClicked(view.backPopupMenuItem) { activity?.onBackPressed() }
                onMenuItemClicked(view.refreshPopupMenuItem) {
                    viewModel.onRefreshRequested()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_REFRESH_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onMenuItemClicked(view.sharePopupMenuItem) { viewModel.onShareSelected() }
                onMenuItemClicked(view.newTabPopupMenuItem) {
                    viewModel.userRequestedOpeningNewTab()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_NEW_TAB_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onMenuItemClicked(view.addBookmarksPopupMenuItem) { launch { viewModel.onBookmarkAddRequested() } }
                onMenuItemClicked(view.fireproofWebsitePopupMenuItem) { launch { viewModel.onFireproofWebsiteClicked() } }
                onMenuItemClicked(view.findInPageMenuItem) { viewModel.onFindInPageSelected() }
                onMenuItemClicked(view.brokenSitePopupMenuItem) { viewModel.onBrokenSiteSelected() }
                onMenuItemClicked(view.settingsPopupMenuItem) { browserActivity?.launchSettings() }
                onMenuItemClicked(view.requestDesktopSiteCheckMenuItem) { viewModel.onDesktopSiteModeToggled(view.requestDesktopSiteCheckMenuItem.isChecked) }
                onMenuItemClicked(view.addToHome) { viewModel.onPinPageToHomeSelected() }
            }
        }

        private fun launchBottomAnchoredPopupMenu() {
            popupMenu.show(rootView, bottomNavigationBar)
            pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_POPUP_OPENED.pixelName, variantManager.getVariant().key))
        }

        private fun bindBottomBarButtons() {
            bottomNavigationBar.apply {
                onItemClicked(bottomBarFireItem) {
                    browserActivity?.launchFire()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_FIRE_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onItemClicked(bottomBarBookmarksItemOne) {
                    browserActivity?.launchBookmarks()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_BOOKMARKS_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onItemClicked(bottomBarSearchItem) {
                    omnibarTextInput.requestFocus()
                    pixel.fire(String.format(Locale.US, Pixel.PixelName.MENU_ACTION_SEARCH_PRESSED.pixelName, variantManager.getVariant().key))
                }
                onItemClicked(bottomBarTabsItem) {
                    viewModel.userLaunchingTabSwitcher()
                }
                onItemClicked(bottomBarOverflowItem) {
                    hideKeyboardImmediately()
                    launchBottomAnchoredPopupMenu()
                }
            }
        }

        fun updateBottomBarVisibility(shouldShow: Boolean, shouldAnimate: Boolean = false) {
            if (isBottomNavigationFeatureEnabled()) {
                if (shouldShow) {
                    showBottomBar(shouldAnimate)
                } else {
                    hideBottomBar(shouldAnimate)
                }
            }
        }

        private fun hideBottomBar(shouldAnimate: Boolean = false) {
            if (shouldAnimate) {
                bottomNavigationBar.animateBarVisibility(isVisible = false)
            }
            bottomNavigationBar.gone()
        }

        private fun showBottomBar(shouldAnimate: Boolean) {
            if (shouldAnimate) {
                bottomNavigationBar.show()
                bottomNavigationBar.animateBarVisibility(isVisible = true)
            } else {
                bottomNavigationBar.postDelayed(KEYBOARD_DELAY) {
                    bottomNavigationBar.show()
                }
            }
        }

        private fun configureShowTabSwitcherListenerWithToolbarOnly() {
            tabsButton?.setOnClickListener {
                launch { viewModel.userLaunchingTabSwitcher() }
            }
        }

        private fun configureShowTabSwitcherListenerWithBottomBarNavigationOnly() {
            bottomBarTabsItem.setOnClickListener {
                launch { viewModel.userLaunchingTabSwitcher() }
            }
        }

        private fun configureLongClickOpensNewTabListenerWithToolbarOnly() {
            tabsButton?.setOnLongClickListener {
                launch { viewModel.userRequestedOpeningNewTab() }
                return@setOnLongClickListener true
            }
        }

        private fun configureLongClickOpensNewTabListenerWithBottomBarNavigationOnly() {
            bottomBarTabsItem.setOnLongClickListener {
                launch { viewModel.userRequestedOpeningNewTab() }
                return@setOnLongClickListener true
            }
        }

        private fun removeUnnecessaryLayoutBehaviour() {
            val params: CoordinatorLayout.LayoutParams = bottomNavigationBar.getLayoutParams() as CoordinatorLayout.LayoutParams
            params.behavior = null
        }

        fun animateTabsCount() {
            tabsButton?.animateCount()
            bottomBarTabsItem.animateCount()
        }

        fun renderTabIcon(tabs: List<TabEntity>) {
            context?.let {
                tabsButton?.count = tabs.count()
                tabsButton?.hasUnread = tabs.firstOrNull { !it.viewed } != null

                bottomBarTabsItem.count = tabs.count()
                bottomBarTabsItem.hasUnread = tabs.firstOrNull { !it.viewed } != null
            }
        }

        fun incrementTabs() {
            if (isBottomNavigationFeatureEnabled()) {
                bottomBarTabsItem.increment {
                    addTabsObserver()
                }
            } else {
                tabsButton?.increment {
                    addTabsObserver()
                }
            }
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
                    cancelAllAnimations()
                }

                if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
                    omnibarTextInput.setText(viewState.omnibarText)
                    appBarLayout.setExpanded(true, true)
                    decorator.updateBottomBarVisibility(true, true)
                    if (viewState.shouldMoveCaretToEnd) {
                        omnibarTextInput.setSelection(viewState.omnibarText.length)
                    }
                } else {
                    decorator.updateBottomBarVisibility(!viewState.isEditing)
                }

                lastSeenBrowserViewState?.let {
                    renderToolbarMenus(it)
                }

                if (ctaContainer.isVisible) {
                    if (isBottomNavigationFeatureEnabled()) {
                        lastSeenOmnibarViewState?.let {
                            if (it.isEditing) {
                                ctaContainer.setPadding(0, 0, 0, 0)
                            } else {
                                ctaContainer.setPadding(0, 0, 0, 46.toPx())
                            }
                        } ?: ctaContainer.setPadding(0, 0, 0, 46.toPx())
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

                if (privacySettingsStore.privacyOn) {

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
                    val events = site?.orderedTrackingEntities()

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
            animatorHelper.cancelAnimations()
            networksContainer.alpha = 0f
            clearTextButton.alpha = 1f
            omnibarTextInput.alpha = 1f
        }

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
                fireproofWebsitePopupMenuItem?.isEnabled = viewState.canFireproofSite
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
            if (viewState.browserShowing) {
                privacyGradeButton?.isInvisible = !viewState.showPrivacyGrade
                clearTextButton?.isVisible = viewState.showClearButton
                searchIcon?.isVisible = viewState.showSearchIcon
            } else {
                privacyGradeButton?.isVisible = false
                clearTextButton?.isVisible = viewState.showClearButton
                searchIcon?.isVisible = true
            }

            decorator.decorateToolbar(viewState)
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
                    hideHomeTopCta()
                }
            }
        }

        private fun showCta(configuration: Cta) {
            when (configuration) {
                is HomePanelCta -> showHomeCta(configuration)
                is DaxBubbleCta -> showDaxCta(configuration)
                is DaxDialogCta -> showDaxDialogCta(configuration)
                is HomeTopPanelCta -> showHomeTopCta(configuration)
            }

            viewModel.onCtaShown()
        }

        private fun showDaxDialogCta(configuration: DaxDialogCta) {
            hideHomeCta()
            hideDaxCta()
            activity?.let { activity ->
                val daxDialog = getDaxDialogFromActivity() as? DaxDialog
                if (daxDialog != null) {
                    daxDialog.setDaxDialogListener(this@BrowserTabFragment)
                    return
                }
                configuration.createCta(activity).apply {
                    setDaxDialogListener(this@BrowserTabFragment)
                    getDaxDialog().show(activity.supportFragmentManager, DAX_DIALOG_DIALOG_TAG)
                }
            }
        }

        private fun showDaxCta(configuration: DaxBubbleCta) {
            ddgLogo.hide()
            hideHomeCta()
            hideHomeTopCta()
            configuration.showCta(daxCtaContainer)
        }

        private fun showHomeTopCta(configuration: HomeTopPanelCta) {
            hideDaxCta()
            hideHomeCta()

            logoHidingListener.callToActionView = ctaTopContainer

            configuration.showCta(ctaTopContainer)
            ctaTopContainer.setOnClickListener {
                viewModel.onUserClickTopCta(configuration)
            }
            ctaTopContainer.closeButton.setOnClickListener {
                viewModel.onUserDismissedCta()
            }
        }

        private fun showHomeCta(configuration: HomePanelCta) {
            hideDaxCta()
            hideHomeTopCta()
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

        private fun hideHomeTopCta() {
            ctaTopContainer.gone()
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

            if (isBottomNavigationFeatureEnabled()) {
                lastSeenOmnibarViewState?.let {
                    if (it.isEditing) {
                        ctaContainer.setPadding(0, 0, 0, 0)
                    } else {
                        ctaContainer.setPadding(0, 0, 0, 46.toPx())
                    }
                } ?: ctaContainer.setPadding(0, 0, 0, 46.toPx())

                ConstraintSet().also {
                    it.clone(newTabLayout)
                    it.connect(ddgLogo.id, ConstraintSet.BOTTOM, ctaContainer.id, ConstraintSet.TOP, 0)
                    it.applyTo(newTabLayout)
                }
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