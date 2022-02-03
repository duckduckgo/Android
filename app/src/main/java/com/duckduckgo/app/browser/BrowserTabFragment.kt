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
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityOptions
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.view.*
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.HitTestResult.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.transaction
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.brokensite.BrokenSiteData
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.DownloadCommand
import com.duckduckgo.app.browser.DownloadConfirmationFragment.DownloadConfirmationDialogListener
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.downloader.BlobConverterInjector
import com.duckduckgo.app.browser.downloader.DownloadFailReason
import com.duckduckgo.app.browser.downloader.FileDownloadNotificationManager
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.favorites.QuickAccessDragTouchItemListener
import com.duckduckgo.app.browser.filechooser.FileChooserIntentBuilder
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.KeyboardAwareEditText
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.browser.omnibar.QueryOrigin.FromAutocomplete
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.ui.HttpAuthenticationDialogFragment
import com.duckduckgo.app.browser.useragent.UserAgentProvider
import com.duckduckgo.app.browser.webview.enableDarkMode
import com.duckduckgo.app.browser.webview.enableLightMode
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.email.EmailAutofillTooltipFragment
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.model.orderedTrackingEntities
import com.duckduckgo.app.global.view.DaxDialog
import com.duckduckgo.app.global.view.DaxDialogListener
import com.duckduckgo.app.global.view.NonDismissibleBehavior
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.disableAnimation
import com.duckduckgo.app.global.view.enableAnimation
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.global.view.isImmersiveModeEnabled
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.global.view.toggleFullScreen
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.mobile.android.ui.view.*
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.ui.SiteLocationPermissionDialog
import com.duckduckgo.app.location.ui.SystemLocationPermissionDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.renderer.icon
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import android.content.pm.ApplicationInfo
import com.duckduckgo.app.browser.urlextraction.DOMUrlExtractor
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebView
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebViewClient
import android.content.pm.ResolveInfo
import android.widget.Button
import com.duckduckgo.app.browser.BrowserTabViewModel.AccessibilityViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.AutoCompleteViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.BrowserViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.CtaViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.FindInPageViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.HighlightableButton
import com.duckduckgo.app.browser.BrowserTabViewModel.LoadingViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.OmnibarViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.PrivacyGradeViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.SavedSiteChangedViewState
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeQuickAccessItemsBinding
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.app.statistics.isFireproofExperimentEnabled
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.google.android.material.snackbar.BaseTransientBottomBar
import javax.inject.Provider

class BrowserTabFragment :
    Fragment(),
    FindListener,
    CoroutineScope,
    DaxDialogListener,
    TrackersAnimatorListener,
    DownloadConfirmationDialogListener,
    SiteLocationPermissionDialog.SiteLocationPermissionDialogListener,
    SystemLocationPermissionDialog.SystemLocationPermissionDialogListener {

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
    lateinit var loginDetector: DOMLoginDetector

    @Inject
    lateinit var blobConverterInjector: BlobConverterInjector

    val tabId get() = requireArguments()[TAB_ID_ARG] as String

    @Inject
    lateinit var userAgentProvider: UserAgentProvider

    @Inject
    lateinit var webViewHttpAuthStore: WebViewHttpAuthStore

    @Inject
    lateinit var thirdPartyCookieManager: ThirdPartyCookieManager

    @Inject
    lateinit var emailInjector: EmailInjector

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var themingDataStore: ThemingDataStore

    @Inject
    lateinit var accessibilitySettingsDataStore: AccessibilitySettingsDataStore

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var urlExtractingWebViewClient: Provider<UrlExtractingWebViewClient>

    @Inject
    lateinit var urlExtractor: Provider<DOMUrlExtractor>

    @Inject
    lateinit var urlExtractorUserAgent: Provider<UserAgentProvider>

    private var urlExtractingWebView: UrlExtractingWebView? = null

    private var _binding: FragmentBrowserTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var omnibar: IncludeOmnibarToolbarBinding

    private lateinit var quickAccessItems: IncludeQuickAccessItemsBinding

    private val findInPage
        get() = omnibar.findInPage

    private val newBrowserTab
        get() = binding.includeNewBrowserTab

    private val daxDialogCta
        get() = binding.includeNewBrowserTab.includeDaxDialogCta

    var messageFromPreviousTab: Message? = null

    private val initialUrl get() = requireArguments().getString(URL_EXTRA_ARG)

    private val skipHome get() = requireArguments().getBoolean(SKIP_HOME_ARG)

    private val favoritesOnboarding get() = requireArguments().getBoolean(FAVORITES_ONBOARDING_ARG, false)

    private lateinit var popupMenu: PopupMenu

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private lateinit var renderer: BrowserTabFragmentRenderer

    private lateinit var decorator: BrowserTabFragmentDecorator

    private lateinit var quickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var quickAccessItemTouchHelper: ItemTouchHelper

    private lateinit var omnibarQuickAccessAdapter: FavoritesQuickAccessAdapter
    private lateinit var omnibarQuickAccessItemTouchHelper: ItemTouchHelper

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProvider(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl, skipHome, favoritesOnboarding)
        viewModel
    }

    private val animatorHelper by lazy { BrowserTrackersAnimatorHelper() }

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(omnibar.pageLoadingIndicator) }

    // Optimization to prevent against excessive work generating WebView previews; an existing job will be cancelled if a new one is launched
    private var bitmapGeneratorJob: Job? = null

    private val browserActivity
        get() = activity as? BrowserActivity

    private val tabsButton: TabSwitcherButton?
        get() = omnibar.tabsMenu

    private val fireMenuButton: ViewGroup?
        get() = omnibar.fireIconMenu

    private val menuButton: ViewGroup?
        get() = omnibar.browserMenu

    private var webView: DuckDuckGoWebView? = null

    private val errorSnackbar: Snackbar by lazy {
        binding.browserLayout.makeSnackbarWithNoBottomInset(R.string.crashedWebViewErrorMessage, Snackbar.LENGTH_INDEFINITE)
            .setBehavior(NonDismissibleBehavior())
    }

    private val findInPageTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.userFindingInPage(findInPage.findInPageInput.text.toString())
        }
    }

    private val omnibarInputTextWatcher = object : TextChangedWatcher() {
        override fun afterTextChanged(editable: Editable) {
            viewModel.onOmnibarInputStateChanged(omnibar.omnibarTextInput.text.toString(), omnibar.omnibarTextInput.hasFocus(), true)
        }
    }

    private val homeBackgroundLogo by lazy { HomeBackgroundLogo(newBrowserTab.ddgLogo) }

    private val ctaViewStateObserver = Observer<CtaViewState> {
        it?.let { renderer.renderCtaViewState(it) }
    }

    private var alertDialog: AlertDialog? = null

    private var appLinksSnackBar: Snackbar? = null

    private var loginDetectionDialog: AlertDialog? = null

    private var emailAutofillTooltipDialog: EmailAutofillTooltipFragment? = null

    private val pulseAnimation: PulseAnimation = PulseAnimation(this)

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeDaxDialogFromActivity()
        renderer = BrowserTabFragmentRenderer()
        decorator = BrowserTabFragmentDecorator()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBrowserTabBinding.inflate(inflater, container, false)
        omnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
        quickAccessItems = IncludeQuickAccessItemsBinding.bind(binding.rootView)
        return binding.rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        configureObservers()
        configurePrivacyGrade()
        configureWebView()
        configureSwipeRefresh()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        configureOmnibarQuickAccessGrid()
        configureHomeTabQuickAccessGrid()

        decorator.decorateWithFeatures()

        animatorHelper.setListener(this)

        if (savedInstanceState == null) {
            viewModel.onViewReady()
            messageFromPreviousTab?.let {
                processMessage(it)
            }
        } else {
            viewModel.onViewRecreated()
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
        startActivity(TabSwitcherActivity.intent(activity, tabId))
        activity.overridePendingTransition(R.anim.tab_anim_fade_in, R.anim.slide_to_bottom)
    }

    override fun onResume() {
        super.onResume()

        omnibar.appBarLayout.setExpanded(true)
        viewModel.onViewResumed()

        // onResume can be called for a hidden/backgrounded fragment, ensure this tab is visible.
        if (fragmentIsVisible()) {
            viewModel.onViewVisible()
        }

        addTextChangedListeners()
    }

    override fun onPause() {
        dismissDownloadFragment()
        dismissAuthenticationDialog()
        super.onPause()
    }

    override fun onStop() {
        alertDialog?.dismiss()
        super.onStop()
    }

    private fun dismissAuthenticationDialog() {
        if (isAdded) {
            val fragment = parentFragmentManager.findFragmentByTag(AUTHENTICATION_DIALOG_TAG) as? HttpAuthenticationDialogFragment
            fragment?.dismiss()
        }
    }

    private fun dismissDownloadFragment() {
        val fragment = fragmentManager?.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG) as? DownloadConfirmationFragment
        fragment?.dismiss()
    }

    private fun addHomeShortcut(
        homeShortcut: Command.AddHomeShortcut,
        context: Context
    ) {
        shortcutBuilder.requestPinShortcut(context, homeShortcut)
    }

    private fun configureObservers() {
        viewModel.autoCompleteViewState.observe(
            viewLifecycleOwner,
            Observer<AutoCompleteViewState> {
                it?.let { renderer.renderAutocomplete(it) }
            }
        )

        viewModel.globalLayoutState.observe(
            viewLifecycleOwner,
            Observer<GlobalLayoutViewState> {
                it?.let { renderer.renderGlobalViewState(it) }
            }
        )

        viewModel.browserViewState.observe(
            viewLifecycleOwner,
            Observer<BrowserViewState> {
                it?.let { renderer.renderBrowserViewState(it) }
            }
        )

        viewModel.loadingViewState.observe(
            viewLifecycleOwner,
            Observer<LoadingViewState> {
                it?.let { renderer.renderLoadingIndicator(it) }
            }
        )

        viewModel.omnibarViewState.observe(
            viewLifecycleOwner,
            Observer<OmnibarViewState> {
                it?.let { renderer.renderOmnibar(it) }
            }
        )

        viewModel.findInPageViewState.observe(
            viewLifecycleOwner,
            Observer<FindInPageViewState> {
                it?.let { renderer.renderFindInPageState(it) }
            }
        )

        viewModel.accessibilityViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.applyAccessibilitySettings(it) }
            }
        )

        viewModel.ctaViewState.observe(viewLifecycleOwner, ctaViewStateObserver)

        viewModel.command.observe(
            viewLifecycleOwner,
            Observer {
                processCommand(it)
            }
        )

        viewModel.survey.observe(
            viewLifecycleOwner,
            Observer<Survey> {
                it.let { viewModel.onSurveyChanged(it) }
            }
        )

        viewModel.privacyGradeViewState.observe(
            viewLifecycleOwner,
            Observer {
                it.let { renderer.renderPrivacyGrade(it) }
            }
        )

        addTabsObserver()
    }

    private fun addTabsObserver() {
        viewModel.tabs.observe(
            viewLifecycleOwner,
            Observer<List<TabEntity>> {
                it?.let {
                    decorator.renderTabIcon(it)
                }
            }
        )
    }

    private fun fragmentIsVisible(): Boolean {
        // using isHidden rather than isVisible, as isVisible will incorrectly return false when windowToken is not yet initialized.
        // changes on isHidden will be received in onHiddenChanged
        return !isHidden
    }

    private fun showHome() {
        viewModel.clearPreviousAppLink()
        dismissAppLinkSnackBar()
        errorSnackbar.dismiss()
        newBrowserTab.newTabLayout.show()
        binding.browserLayout.gone()
        omnibar.appBarLayout.setExpanded(true)
        webView?.onPause()
        webView?.hide()
    }

    private fun showBrowser() {
        newBrowserTab.newTabLayout.gone()
        binding.browserLayout.show()
        webView?.show()
        webView?.onResume()
    }

    fun submitQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    private fun navigate(
        url: String,
        headers: Map<String, String>
    ) {
        hideKeyboard()
        renderer.hideFindInPage()
        viewModel.registerDaxBubbleCtaDismissed()
        webView?.loadUrl(url, headers)
    }

    fun onRefreshRequested() {
        viewModel.onRefreshRequested()
    }

    fun refresh() {
        webView?.reload()
        viewModel.onWebViewRefreshed()
    }

    private fun processCommand(it: Command?) {
        if (it !is Command.DaxCommand) {
            renderer.cancelTrackersAnimation()
        }
        when (it) {
            is Command.Refresh -> refresh()
            is Command.OpenInNewTab -> {
                browserActivity?.openInNewTab(it.query, it.sourceTabId)
            }
            is Command.OpenMessageInNewTab -> {
                browserActivity?.openMessageInNewTab(it.message, it.sourceTabId)
            }
            is Command.OpenInNewBackgroundTab -> {
                openInNewBackgroundTab()
            }
            is Command.LaunchNewTab -> browserActivity?.launchNewTab()
            is Command.ShowSavedSiteAddedConfirmation -> savedSiteAdded(it.savedSiteChangedViewState)
            is Command.ShowEditSavedSiteDialog -> editSavedSite(it.savedSiteChangedViewState)
            is Command.DeleteSavedSiteConfirmation -> confirmDeleteSavedSite(it.savedSite)
            is Command.ShowFireproofWebSiteConfirmation -> fireproofWebsiteConfirmation(it.fireproofWebsiteEntity)
            is Command.Navigate -> {
                dismissAppLinkSnackBar()
                navigate(it.url, it.headers)
            }
            is Command.NavigateBack -> {
                dismissAppLinkSnackBar()
                webView?.goBackOrForward(-it.steps)
            }
            is Command.NavigateForward -> {
                dismissAppLinkSnackBar()
                webView?.goForward()
            }
            is Command.ResetHistory -> {
                resetWebView()
            }
            is Command.DialNumber -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${it.telephoneNumber}")
                openExternalDialog(intent = intent, fallbackUrl = null, fallbackIntent = null, useFirstActivityFound = false)
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
                launchBrokenSiteFeedback(it.data)
            }
            is Command.ShowFullScreen -> {
                binding.webViewFullScreenContainer.addView(
                    it.view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            is Command.DownloadImage -> requestImageDownload(it.url, it.requestUserConfirmation)
            is Command.FindInPageCommand -> webView?.findAllAsync(it.searchTerm)
            is Command.DismissFindInPage -> webView?.findAllAsync("")
            is Command.ShareLink -> launchSharePageChooser(it.url)
            is Command.CopyLink -> clipboardManager.setPrimaryClip(ClipData.newPlainText(null, it.url))
            is Command.ShowFileChooser -> {
                launchFilePicker(it)
            }
            is Command.AddHomeShortcut -> {
                context?.let { context ->
                    addHomeShortcut(it, context)
                }
            }
            is Command.ShowAppLinkPrompt -> {
                showAppLinkSnackBar(it.appLink)
            }
            is Command.OpenAppLink -> {
                openAppLink(it.appLink)
            }
            is Command.HandleNonHttpAppLink -> {
                openExternalDialog(
                    intent = it.nonHttpAppLink.intent,
                    fallbackUrl = it.nonHttpAppLink.fallbackUrl,
                    fallbackIntent = it.nonHttpAppLink.fallbackIntent,
                    useFirstActivityFound = false,
                    headers = it.headers
                )
            }
            is Command.ExtractUrlFromCloakedTrackingLink -> {
                extractUrlFromTrackingLink(it.initialUrl)
            }
            is Command.LoadExtractedUrl -> {
                webView?.loadUrl(it.extractedUrl)
                destroyUrlExtractingWebView()
            }
            is Command.LaunchSurvey -> launchSurvey(it.survey)
            is Command.LaunchAddWidget -> addWidgetLauncher.launchAddWidget(activity)
            is Command.RequiresAuthentication -> showAuthenticationDialog(it.request)
            is Command.SaveCredentials -> saveBasicAuthCredentials(it.request, it.credentials)
            is Command.GenerateWebViewPreviewImage -> generateWebViewPreviewImage()
            is Command.LaunchTabSwitcher -> launchTabSwitcher()
            is Command.ShowErrorWithAction -> showErrorSnackbar(it)
            is Command.DaxCommand.FinishTrackerAnimation -> finishTrackerAnimation()
            is Command.DaxCommand.HideDaxDialog -> showHideTipsDialog(it.cta)
            is Command.HideWebContent -> webView?.hide()
            is Command.ShowWebContent -> webView?.show()
            is Command.CheckSystemLocationPermission -> checkSystemLocationPermission(it.domain, it.deniedForever)
            is Command.RequestSystemLocationPermission -> requestLocationPermissions()
            is Command.AskDomainPermission -> askSiteLocationPermission(it.domain)
            is Command.RefreshUserAgent -> refreshUserAgent(it.url, it.isDesktop)
            is Command.AskToFireproofWebsite -> askToFireproofWebsite(requireContext(), it.fireproofWebsite)
            is Command.AskToDisableLoginDetection -> askToDisableLoginDetection(requireContext())
            is Command.ShowDomainHasPermissionMessage -> showDomainHasLocationPermission(it.domain)
            is DownloadCommand -> processDownloadCommand(it)
            is Command.ConvertBlobToDataUri -> convertBlobToDataUri(it)
            is Command.RequestFileDownload -> requestFileDownload(it.url, it.contentDisposition, it.mimeType, it.requestUserConfirmation)
            is Command.ChildTabClosed -> processUriForThirdPartyCookies()
            is Command.CopyAliasToClipboard -> copyAliasToClipboard(it.alias)
            is Command.InjectEmailAddress -> injectEmailAddress(it.address)
            is Command.ShowEmailTooltip -> showEmailTooltip(it.address)
            is Command.EditWithSelectedQuery -> {
                omnibar.omnibarTextInput.setText(it.query)
                omnibar.omnibarTextInput.setSelection(it.query.length)
            }
        }
    }

    private fun extractUrlFromTrackingLink(initialUrl: String) {
        context?.let {
            val client = urlExtractingWebViewClient.get()
            client.urlExtractionListener = viewModel

            Timber.d("Tracking link detection: Creating WebView for URL extraction")
            urlExtractingWebView = UrlExtractingWebView(requireContext(), client, urlExtractorUserAgent.get(), urlExtractor.get())

            urlExtractingWebView?.urlExtractionListener = viewModel

            Timber.d("Tracking link detection: Loading tracking URL for extraction")
            urlExtractingWebView?.loadUrl(initialUrl)
        }
    }

    private fun destroyUrlExtractingWebView() {
        urlExtractingWebView?.destroyWebView()
        urlExtractingWebView = null
    }

    private fun injectEmailAddress(alias: String) {
        webView?.let {
            emailInjector.injectAddressInEmailField(it, alias)
        }
    }

    private fun copyAliasToClipboard(alias: String) {
        context?.let {
            val clipboard: ClipboardManager? = ContextCompat.getSystemService(it, ClipboardManager::class.java)
            val clip: ClipData = ClipData.newPlainText("Alias", alias)
            clipboard?.setPrimaryClip(clip)
            showToast(R.string.aliasToClipboardMessage)
        }
    }

    private fun processUriForThirdPartyCookies() {
        webView?.let {
            val url = it.url ?: return
            launch {
                thirdPartyCookieManager.processUriForThirdPartyCookies(it, url.toUri())
            }
        }
    }

    private fun processDownloadCommand(it: DownloadCommand) {
        when (it) {
            is DownloadCommand.ScanMediaFiles -> {
                context?.applicationContext?.let { context ->
                    MediaScannerConnection.scanFile(context, arrayOf(it.file.absolutePath), null, null)
                }
            }
            is DownloadCommand.ShowDownloadFinishedNotification -> {
                fileDownloadNotificationManager.showDownloadFinishedNotification(it.file.name, it.file.absolutePath.toUri(), it.mimeType)
            }
            DownloadCommand.ShowDownloadInProgressNotification -> {
                fileDownloadNotificationManager.showDownloadInProgressNotification()
            }
            is DownloadCommand.ShowDownloadFailedNotification -> {
                fileDownloadNotificationManager.showDownloadFailedNotification()

                val snackbar = omnibar.toolbar.makeSnackbarWithNoBottomInset(R.string.downloadFailed, Snackbar.LENGTH_INDEFINITE)
                if (it.reason == DownloadFailReason.DownloadManagerDisabled) {
                    snackbar.setText(it.message)
                    snackbar.setAction(getString(R.string.enable)) {
                        showDownloadManagerAppSettings()
                    }
                }
                snackbar.show()
            }
        }
    }

    private fun locationPermissionsHaveNotBeenGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    }

    private fun checkSystemLocationPermission(
        domain: String,
        deniedForever: Boolean
    ) {
        if (locationPermissionsHaveNotBeenGranted()) {
            if (deniedForever) {
                viewModel.onSystemLocationPermissionDeniedOneTime()
            } else {
                val dialog = SystemLocationPermissionDialog.instance(domain)
                dialog.show(childFragmentManager, SystemLocationPermissionDialog.SYSTEM_LOCATION_PERMISSION_TAG)
            }
        } else {
            viewModel.onSystemLocationPermissionGranted()
        }
    }

    private fun requestLocationPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            PERMISSION_REQUEST_GEO_LOCATION
        )
    }

    private fun askSiteLocationPermission(domain: String) {
        val dialog = SiteLocationPermissionDialog.instance(domain, false, tabId)
        dialog.show(childFragmentManager, SiteLocationPermissionDialog.SITE_LOCATION_PERMISSION_TAG)
    }

    private fun launchBrokenSiteFeedback(data: BrokenSiteData) {
        context?.let {
            val options = ActivityOptions.makeSceneTransitionAnimation(browserActivity).toBundle()
            startActivity(BrokenSiteActivity.intent(it, data), options)
        }
    }

    private fun showErrorSnackbar(command: Command.ShowErrorWithAction) {
        // Snackbar is global and it should appear only the foreground fragment
        if (!errorSnackbar.view.isAttachedToWindow && isVisible) {
            errorSnackbar.setText(command.textResId)
            errorSnackbar.setAction(R.string.crashedWebViewErrorAction) { command.action() }.show()
        }
    }

    private fun showDomainHasLocationPermission(domain: String) {
        val snackbar =
            binding.rootView.makeSnackbarWithNoBottomInset(
                getString(R.string.preciseLocationSnackbarMessage, domain.websiteFromGeoLocationsApiOrigin()),
                Snackbar.LENGTH_SHORT
            )
        snackbar.view.setOnClickListener {
            browserActivity?.launchLocationSettings()
        }
        snackbar.show()
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
        omnibar.appBarLayout.setExpanded(true, true)
        viewModel.tabs.removeObservers(this)
        decorator.incrementTabs()
    }

    private fun showAppLinkSnackBar(appLink: SpecialUrlDetector.UrlType.AppLink) {
        view?.let { view ->

            val message: String?
            val action: String?

            if (appLink.appIntent != null) {
                val packageName = appLink.appIntent.component?.packageName ?: return
                message = getString(R.string.appLinkSnackBarMessage, getAppName(packageName))
                action = getString(R.string.appLinkSnackBarAction)
            } else {
                message = getString(R.string.appLinkMultipleSnackBarMessage)
                action = getString(R.string.appLinkMultipleSnackBarAction)
            }

            appLinksSnackBar = view.makeSnackbarWithNoBottomInset(
                message,
                Snackbar.LENGTH_LONG
            ).setAction(action) {
                pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_OPEN_ACTION_PRESSED)
                openAppLink(appLink)
            }.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onShown(transientBottomBar: Snackbar?) {
                    super.onShown(transientBottomBar)
                    pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_SHOWN)
                }

                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int
                ) {
                    super.onDismissed(transientBottomBar, event)
                }
            })

            appLinksSnackBar?.setDuration(6000)?.show()
        }
    }

    private fun getAppName(packageName: String): String? {
        val packageManager: PackageManager? = context?.packageManager
        val applicationInfo: ApplicationInfo? = try {
            packageManager?.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return if (applicationInfo != null) {
            packageManager?.getApplicationLabel(applicationInfo).toString()
        } else {
            null
        }
    }

    private fun openAppLink(appLink: SpecialUrlDetector.UrlType.AppLink) {
        if (appLink.appIntent != null) {
            startActivity(appLink.appIntent)
        } else if (appLink.excludedComponents != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val title = getString(R.string.appLinkIntentChooserTitle)
            val chooserIntent = getChooserIntent(appLink.uriString, title, appLink.excludedComponents)
            startActivity(chooserIntent)
        }
        viewModel.clearPreviousUrl()
    }

    private fun dismissAppLinkSnackBar() {
        appLinksSnackBar?.dismiss()
        appLinksSnackBar = null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getChooserIntent(
        url: String?,
        title: String,
        excludedComponents: List<ComponentName>
    ): Intent {
        val urlIntent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
        val chooserIntent = Intent.createChooser(urlIntent, title)
        chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
        return chooserIntent
    }

    private fun openExternalDialog(
        intent: Intent,
        fallbackUrl: String? = null,
        fallbackIntent: Intent? = null,
        useFirstActivityFound: Boolean = true,
        headers: Map<String, String> = emptyMap()
    ) {
        context?.let {
            val pm = it.packageManager
            val activities = pm.queryIntentActivities(intent, 0)

            if (activities.isEmpty()) {
                when {
                    fallbackIntent != null -> {
                        val fallbackActivities = pm.queryIntentActivities(fallbackIntent, 0)
                        launchDialogForIntent(it, pm, fallbackIntent, fallbackActivities, useFirstActivityFound)
                    }
                    fallbackUrl != null -> {
                        webView?.loadUrl(fallbackUrl, headers)
                    }
                    else -> {
                        showToast(R.string.unableToOpenLink)
                    }
                }
            } else {
                launchDialogForIntent(it, pm, intent, activities, useFirstActivityFound)
            }
        }
    }

    private fun launchDialogForIntent(
        context: Context,
        pm: PackageManager?,
        intent: Intent,
        activities: List<ResolveInfo>,
        useFirstActivityFound: Boolean
    ) {
        if (activities.size == 1 || useFirstActivityFound) {
            val activity = activities.first()
            val appTitle = activity.loadLabel(pm)
            Timber.i("Exactly one app available for intent: $appTitle")
            launchExternalAppDialog(context) { context.startActivity(intent) }
        } else {
            val title = getString(R.string.openExternalApp)
            val intentChooser = Intent.createChooser(intent, title)
            launchExternalAppDialog(context) { context.startActivity(intentChooser) }
        }
    }

    private fun askToFireproofWebsite(
        context: Context,
        fireproofWebsite: FireproofWebsiteEntity
    ) {
        val isShowing = loginDetectionDialog?.isShowing

        if (isShowing != true) {
            loginDetectionDialog = AlertDialog.Builder(context)
                .setTitle(getString(R.string.fireproofWebsiteLoginDialogTitle, fireproofWebsite.website()))
                .setMessage(R.string.fireproofWebsiteLoginDialogDescription)
                .setPositiveButton(R.string.fireproofWebsiteLoginDialogPositive) { _, _ ->
                    viewModel.onUserConfirmedFireproofDialog(fireproofWebsite.domain)
                }.setNegativeButton(R.string.fireproofWebsiteLoginDialogNegative) { dialog, _ ->
                    dialog.dismiss()
                    viewModel.onUserDismissedFireproofLoginDialog()
                }.setOnCancelListener {
                    viewModel.onUserDismissedFireproofLoginDialog()
                }.show()

            viewModel.onFireproofLoginDialogShown()
        }
    }

    private fun askToDisableLoginDetection(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.disableLoginDetectionDialogTitle))
            .setMessage(R.string.disableLoginDetectionDialogDescription)
            .setPositiveButton(R.string.disableLoginDetectionDialogPositive) { _, _ ->
                viewModel.onUserConfirmedDisableLoginDetectionDialog()
            }
            .setNegativeButton(R.string.disableLoginDetectionDialogNegative) { dialog, _ ->
                dialog.dismiss()
                viewModel.onUserDismissedDisableLoginDetectionDialog()
            }.setOnCancelListener {
                viewModel.onUserDismissedDisableLoginDetectionDialog()
            }.show()

        viewModel.onDisableLoginDetectionDialogShown()
    }

    private fun launchExternalAppDialog(
        context: Context,
        onClick: () -> Unit
    ) {
        val isShowing = alertDialog?.isShowing

        if (isShowing != true) {
            alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.launchingExternalApp)
                .setMessage(getString(R.string.confirmOpenExternalApp))
                .setPositiveButton(R.string.open) { _, _ ->
                    onClick()
                }
                .setNeutralButton(R.string.closeTab) { dialog, _ ->
                    dialog.dismiss()
                    launch {
                        viewModel.closeCurrentTab()
                        destroyWebView()
                    }
                }
                .setNegativeButton(R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
            handleFileUploadResult(resultCode, data)
        }
    }

    private fun handleFileUploadResult(
        resultCode: Int,
        intent: Intent?
    ) {
        if (resultCode != RESULT_OK || intent == null) {
            Timber.i("Received resultCode $resultCode (or received null intent) indicating user did not select any files")
            pendingUploadTask?.onReceiveValue(null)
            return
        }

        val uris = fileChooserIntentBuilder.extractSelectedFileUris(intent)
        pendingUploadTask?.onReceiveValue(uris)
    }

    private fun showToast(@StringRes messageId: Int) {
        Toast.makeText(context?.applicationContext, messageId, Toast.LENGTH_LONG).show()
    }

    private fun showAuthenticationDialog(request: BasicAuthenticationRequest) {
        activity?.supportFragmentManager?.let { fragmentManager ->
            val dialog = HttpAuthenticationDialogFragment.createHttpAuthenticationDialog(request.site)
            dialog.show(fragmentManager, AUTHENTICATION_DIALOG_TAG)
            dialog.listener = viewModel
            dialog.request = request
        }
    }

    private fun saveBasicAuthCredentials(
        request: BasicAuthenticationRequest,
        credentials: BasicAuthenticationCredentials
    ) {
        webView?.let {
            webViewHttpAuthStore.setHttpAuthUsernamePassword(
                it,
                host = request.host,
                realm = request.realm,
                username = credentials.username,
                password = credentials.password
            )
        }
    }

    private fun configureAutoComplete() {
        val context = context ?: return
        binding.autoCompleteSuggestionsList.layoutManager = LinearLayoutManager(context)
        autoCompleteSuggestionsAdapter = BrowserAutoCompleteSuggestionsAdapter(
            immediateSearchClickListener = {
                userSelectedAutocomplete(it)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            }
        )
        binding.autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun configureOmnibarQuickAccessGrid() {
        configureQuickAccessGridLayout(binding.quickAccessSuggestionsRecyclerView)
        omnibarQuickAccessAdapter = createQuickAccessAdapter(originPixel = AppPixelName.FAVORITE_OMNIBAR_ITEM_PRESSED) { viewHolder ->
            binding.quickAccessSuggestionsRecyclerView.enableAnimation()
            omnibarQuickAccessItemTouchHelper.startDrag(viewHolder)
        }
        omnibarQuickAccessItemTouchHelper = createQuickAccessItemHolder(binding.quickAccessSuggestionsRecyclerView, omnibarQuickAccessAdapter)
        binding.quickAccessSuggestionsRecyclerView.adapter = omnibarQuickAccessAdapter
        binding.quickAccessSuggestionsRecyclerView.disableAnimation()
    }

    private fun configureHomeTabQuickAccessGrid() {
        configureQuickAccessGridLayout(quickAccessItems.quickAccessRecyclerView)
        quickAccessAdapter = createQuickAccessAdapter(originPixel = AppPixelName.FAVORITE_HOMETAB_ITEM_PRESSED) { viewHolder ->
            quickAccessItems.quickAccessRecyclerView.enableAnimation()
            quickAccessItemTouchHelper.startDrag(viewHolder)
        }
        quickAccessItemTouchHelper = createQuickAccessItemHolder(quickAccessItems.quickAccessRecyclerView, quickAccessAdapter)
        quickAccessItems.quickAccessRecyclerView.adapter = quickAccessAdapter
        quickAccessItems.quickAccessRecyclerView.disableAnimation()
    }

    private fun createQuickAccessItemHolder(
        recyclerView: RecyclerView,
        apapter: FavoritesQuickAccessAdapter
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                apapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                        recyclerView.disableAnimation()
                    }
                }
            )
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun createQuickAccessAdapter(
        originPixel: AppPixelName,
        onMoveListener: (RecyclerView.ViewHolder) -> Unit
    ): FavoritesQuickAccessAdapter {
        return FavoritesQuickAccessAdapter(
            this, faviconManager, onMoveListener,
            {
                pixel.fire(originPixel)
                viewModel.onUserSubmittedQuery(it.favorite.url)
            },
            { viewModel.onEditSavedSiteRequested(it.favorite) },
            { viewModel.onDeleteQuickAccessItemRequested(it.favorite) }
        )
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val numOfColumns = gridViewColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(requireContext(), numOfColumns)
        recyclerView.layoutManager = layoutManager
        val sidePadding = gridViewColumnCalculator.calculateSidePadding(QUICK_ACCESS_ITEM_MAX_SIZE_DP, numOfColumns)
        recyclerView.setPadding(sidePadding, recyclerView.paddingTop, sidePadding, recyclerView.paddingBottom)
    }

    private fun configurePrivacyGrade() {
        omnibar.privacyGradeButton.setOnClickListener {
            browserActivity?.launchPrivacyDashboard()
        }
    }

    private fun configureFindInPage() {
        findInPage.findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && findInPage.findInPageInput.text.toString() != viewModel.findInPageViewState.value?.searchTerm) {
                viewModel.userFindingInPage(findInPage.findInPageInput.text.toString())
            }
        }

        findInPage.previousSearchTermButton.setOnClickListener { webView?.findNext(false) }
        findInPage.nextSearchTermButton.setOnClickListener { webView?.findNext(true) }
        findInPage.closeFindInPagePanel.setOnClickListener {
            viewModel.dismissFindInView()
        }
    }

    private fun configureOmnibarTextInput() {
        omnibar.omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->
                viewModel.onOmnibarInputStateChanged(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
                if (!hasFocus) {
                    omnibar.omnibarTextInput.hideKeyboard()
                    binding.focusDummy.requestFocus()
                }
            }

        omnibar.omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                omnibar.omnibarTextInput.hideKeyboard()
                binding.focusDummy.requestFocus()
                return true
            }
        }

        omnibar.omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    userEnteredQuery(omnibar.omnibarTextInput.text.toString())
                    return@OnEditorActionListener true
                }
                false
            }
        )

        omnibar.clearTextButton.setOnClickListener { omnibar.omnibarTextInput.setText("") }
    }

    private fun userSelectedAutocomplete(suggestion: AutoCompleteSuggestion) {
        // send pixel before submitting the query and changing the autocomplete state to empty; otherwise will send the wrong params
        appCoroutineScope.launch {
            viewModel.fireAutocompletePixel(suggestion)
            withContext(Dispatchers.Main) {
                val origin = when (suggestion) {
                    is AutoCompleteBookmarkSuggestion -> FromAutocomplete(isNav = true)
                    is AutoCompleteSearchSuggestion -> FromAutocomplete(isNav = suggestion.isUrl)
                }
                viewModel.onUserSubmittedQuery(suggestion.phrase, origin)
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
            binding.webViewContainer,
            true
        ).findViewById(R.id.browserWebView) as DuckDuckGoWebView

        webView?.let {
            it.webViewClient = webViewClient
            it.webChromeClient = webChromeClient

            it.settings.apply {
                userAgentString = userAgentProvider.userAgent()
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                javaScriptCanOpenWindowsAutomatically = appBuildConfig.isTest // only allow when running tests
                setSupportMultipleWindows(true)
                disableWebSql(this)
                setSupportZoom(true)
                configureDarkThemeSupport(this)
                if (accessibilitySettingsDataStore.overrideSystemFontSize) {
                    textZoom = accessibilitySettingsDataStore.fontSize.toInt()
                }
            }

            it.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                viewModel.requestFileDownload(url, contentDisposition, mimeType, true)
            }

            it.setOnTouchListener { _, _ ->
                if (omnibar.omnibarTextInput.isFocused) {
                    binding.focusDummy.requestFocus()
                }
                dismissAppLinkSnackBar()
                false
            }

            it.setEnableSwipeRefreshCallback { enable ->
                binding.swipeRefreshContainer?.isEnabled = enable
            }

            registerForContextMenu(it)

            it.setFindListener(this)
            loginDetector.addLoginDetection(it) { viewModel.loginDetected() }
            blobConverterInjector.addJsInterface(it) { url, mimeType -> viewModel.requestFileDownload(url, null, mimeType, true) }
            emailInjector.addJsInterface(it) { viewModel.showEmailTooltip() }
        }

        if (appBuildConfig.isDebug) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun configureDarkThemeSupport(webSettings: WebSettings) {
        when (themingDataStore.theme) {
            DuckDuckGoTheme.LIGHT -> webSettings.enableLightMode()
            DuckDuckGoTheme.DARK -> webSettings.enableDarkMode()
            DuckDuckGoTheme.SYSTEM_DEFAULT -> {
                val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                    webSettings.enableDarkMode()
                } else {
                    webSettings.enableLightMode()
                }
            }
        }
    }

    private fun configureSwipeRefresh() {
        val metrics = resources.displayMetrics
        val distanceToTrigger = (DEFAULT_CIRCLE_TARGET_TIMES_1_5 * metrics.density).toInt()
        binding.swipeRefreshContainer.setDistanceToTriggerSync(distanceToTrigger)
        binding.swipeRefreshContainer.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), com.duckduckgo.mobile.android.R.color.cornflowerBlue)
        )

        binding.swipeRefreshContainer.setOnRefreshListener {
            onRefreshRequested()
        }

        binding.swipeRefreshContainer.setCanChildScrollUpCallback {
            webView?.canScrollVertically(-1) ?: false
        }

        // avoids progressView from showing under toolbar
        binding.swipeRefreshContainer.progressViewStartOffset = binding.swipeRefreshContainer.progressViewStartOffset - 15
    }

    /**
     * Explicitly disable database to try protect against Magellan WebSQL/SQLite vulnerability
     */
    private fun disableWebSql(settings: WebSettings) {
        settings.databaseEnabled = false
    }

    private fun addTextChangedListeners() {
        findInPage.findInPageInput.replaceTextChangedListener(findInPageTextWatcher)
        omnibar.omnibarTextInput.replaceTextChangedListener(omnibarInputTextWatcher)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
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

    private fun savedSiteAdded(savedSiteChangedViewState: SavedSiteChangedViewState) {
        val snackbarMessage = when (savedSiteChangedViewState.savedSite) {
            is SavedSite.Bookmark -> R.string.bookmarkAddedMessage
            is SavedSite.Favorite -> R.string.favoriteAddedMessage
        }
        binding.browserLayout.makeSnackbarWithNoBottomInset(snackbarMessage, Snackbar.LENGTH_LONG)
            .setAction(R.string.edit) {
                editSavedSite(savedSiteChangedViewState)
            }
            .show()
    }

    private fun editSavedSite(savedSiteChangedViewState: SavedSiteChangedViewState) {
        val addBookmarkDialog = EditSavedSiteDialogFragment.instance(
            savedSiteChangedViewState.savedSite,
            savedSiteChangedViewState.bookmarkFolder?.id ?: 0,
            savedSiteChangedViewState.bookmarkFolder?.name
        )
        addBookmarkDialog.show(childFragmentManager, ADD_SAVED_SITE_FRAGMENT_TAG)
        addBookmarkDialog.listener = viewModel
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(requireContext())
        viewModel.deleteQuickAccessItem(savedSite)
        binding.rootView.makeSnackbarWithNoBottomInset(
            message,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insertQuickAccessItem(savedSite)
        }.show()
    }

    private fun fireproofWebsiteConfirmation(entity: FireproofWebsiteEntity) {
        val snackbar = binding.rootView.makeSnackbarWithNoBottomInset(
            HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteSnackbarConfirmation, entity.website()), FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_LONG
        )

        snackbar.setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.onFireproofWebsiteSnackbarUndoClicked(entity)
        }

        if (variantManager.isFireproofExperimentEnabled()) {
            snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onShown(transientBottomBar: Snackbar?) {
                    super.onShown(transientBottomBar)
                    pixel.fire(AppPixelName.FIREPROOF_SNACKBAR_SHOWN)
                }

                override fun onDismissed(
                    transientBottomBar: Snackbar?,
                    event: Int
                ) {
                    super.onDismissed(transientBottomBar, event)
                }
            })
        }
        snackbar.show()
    }

    private fun launchSharePageChooser(url: String) {
        val intent = Intent(Intent.ACTION_SEND).also {
            it.type = "text/plain"
            it.putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    override fun onFindResultReceived(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
        isDoneCounting: Boolean
    ) {
        viewModel.onFindResultsReceived(activeMatchOrdinal, numberOfMatches)
    }

    private fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
        removeTextChangedListener(textWatcher)
        addTextChangedListener(textWatcher)
    }

    private fun hideKeyboardImmediately() {
        if (!isHidden) {
            Timber.v("Keyboard now hiding")
            omnibar.omnibarTextInput.hideKeyboard()
            binding.focusDummy.requestFocus()
        }
    }

    private fun hideKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now hiding")
            omnibar.omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibar.omnibarTextInput?.hideKeyboard() }
            binding.focusDummy.requestFocus()
        }
    }

    private fun showKeyboardImmediately() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibar.omnibarTextInput?.showKeyboard()
        }
    }

    private fun showKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibar.omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibar.omnibarTextInput?.showKeyboard() }
        }
    }

    private fun refreshUserAgent(
        url: String?,
        isDesktop: Boolean
    ) {
        val currentAgent = webView?.settings?.userAgentString
        val newAgent = userAgentProvider.userAgent(url, isDesktop)
        if (newAgent != currentAgent) {
            webView?.settings?.userAgentString = newAgent
        }
        Timber.d("User Agent is $newAgent")
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
        viewModel.restoreWebViewState(webView, omnibar.omnibarTextInput.text.toString())
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
        newBrowserTab.ddgLogo.setImageResource(R.drawable.logo_full)
        if (newBrowserTab.ctaContainer.isNotEmpty()) {
            renderer.renderHomeCta()
        }
        configureQuickAccessGridLayout(quickAccessItems.quickAccessRecyclerView)
        configureQuickAccessGridLayout(binding.quickAccessSuggestionsRecyclerView)
    }

    fun onBackPressed(): Boolean {
        if (!isAdded) return false
        return viewModel.onUserPressedBack()
    }

    private fun resetWebView() {
        destroyWebView()
        configureWebView()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        destroyWebView()
        _binding = null
    }

    override fun onDestroy() {
        dismissAppLinkSnackBar()
        pulseAnimation.stop()
        animatorHelper.removeListener()
        supervisorJob.cancel()
        popupMenu.dismiss()
        loginDetectionDialog?.dismiss()
        emailAutofillTooltipDialog?.dismiss()
        super.onDestroy()
    }

    private fun destroyWebView() {
        binding.webViewContainer?.removeAllViews()
        webView?.destroy()
        webView = null
    }

    private fun convertBlobToDataUri(blob: Command.ConvertBlobToDataUri) {
        webView?.let {
            blobConverterInjector.convertBlobIntoDataUriAndDownload(it, blob.url, blob.mimeType)
        }
    }

    private fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            userAgent = userAgentProvider.userAgent(),
            subfolder = Environment.DIRECTORY_DOWNLOADS
        )

        if (hasWriteStoragePermission()) {
            downloadFile(requestUserConfirmation)
        } else {
            requestWriteStoragePermission()
        }
    }

    private fun requestImageDownload(
        url: String,
        requestUserConfirmation: Boolean
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            userAgent = userAgentProvider.userAgent(),
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
        val pendingDownload = pendingFileDownload ?: return

        pendingFileDownload = null

        if (requestUserConfirmation) {
            requestDownloadConfirmation(pendingDownload)
        } else {
            continueDownload(pendingDownload)
        }
    }

    private fun requestDownloadConfirmation(pendingDownload: PendingFileDownload) {
        if (isStateSaved) return

        val downloadConfirmationFragment = DownloadConfirmationFragment.instance(pendingDownload)
        childFragmentManager.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG)?.let {
            Timber.i("Found existing dialog; removing it now")
            childFragmentManager.commitNow(allowStateLoss = true) { remove(it) }
        }
        downloadConfirmationFragment.show(childFragmentManager, DOWNLOAD_CONFIRMATION_TAG)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.i("Write external storage permission granted")
                    downloadFile(requestUserConfirmation = false)
                } else {
                    Timber.i("Write external storage permission refused")
                    omnibar.toolbar.makeSnackbarWithNoBottomInset(R.string.permissionRequiredToDownload, Snackbar.LENGTH_LONG).show()
                }
            }
            PERMISSION_REQUEST_GEO_LOCATION -> {
                if ((grantResults.isNotEmpty()) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    viewModel.onSystemLocationPermissionGranted()
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                        viewModel.onSystemLocationPermissionDeniedOneTime()
                    } else {
                        viewModel.onSystemLocationPermissionDeniedForever()
                    }
                }
            }
        }
    }

    private fun launchSurvey(survey: Survey) {
        context?.let {
            startActivity(SurveyActivity.intent(it, survey))
        }
    }

    private fun finishTrackerAnimation() {
        animatorHelper.finishTrackerAnimation(omnibarViews(), omnibar.animationContainer)
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

    override fun onDaxDialogSecondaryCtaClick() {
        viewModel.onUserClickCtaSecondaryButton()
    }

    private fun launchHideTipsDialog(
        context: Context,
        cta: Cta
    ) {
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

    fun omnibarViews(): List<View> = listOf(omnibar.clearTextButton, omnibar.omnibarTextInput, omnibar.searchIcon)

    override fun onAnimationFinished() {
        viewModel.stopShowingEmptyGrade()
    }

    private fun showEmailTooltip(address: String) {
        context?.let {
            val isShowing: Boolean? = emailAutofillTooltipDialog?.isShowing
            if (isShowing != true) {
                emailAutofillTooltipDialog = EmailAutofillTooltipFragment(it, address)
                emailAutofillTooltipDialog?.show()
                emailAutofillTooltipDialog?.setOnCancelListener { viewModel.cancelAutofillTooltip() }
                emailAutofillTooltipDialog?.useAddress = { viewModel.useAddress() }
                emailAutofillTooltipDialog?.usePrivateAlias = { viewModel.consumeAlias() }
            }
        }
    }

    companion object {
        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val URL_EXTRA_ARG = "URL_EXTRA_ARG"
        private const val SKIP_HOME_ARG = "SKIP_HOME_ARG"
        private const val FAVORITES_ONBOARDING_ARG = "FAVORITES_ONBOARDING_ARG"

        private const val ADD_SAVED_SITE_FRAGMENT_TAG = "ADD_SAVED_SITE"
        private const val KEYBOARD_DELAY = 200L

        private const val REQUEST_CODE_CHOOSE_FILE = 100
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val PERMISSION_REQUEST_GEO_LOCATION = 300

        private const val URL_BUNDLE_KEY = "url"

        private const val AUTHENTICATION_DIALOG_TAG = "AUTH_DIALOG_TAG"
        private const val DOWNLOAD_CONFIRMATION_TAG = "DOWNLOAD_CONFIRMATION_TAG"
        private const val DAX_DIALOG_DIALOG_TAG = "DAX_DIALOG_TAG"

        private const val MAX_PROGRESS = 100
        private const val TRACKERS_INI_DELAY = 500L
        private const val TRACKERS_SECONDARY_DELAY = 200L

        private const val DEFAULT_CIRCLE_TARGET_TIMES_1_5 = 96

        private const val QUICK_ACCESS_GRID_MAX_COLUMNS = 6

        fun newInstance(
            tabId: String,
            query: String? = null,
            skipHome: Boolean
        ): BrowserTabFragment {
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

        fun newInstanceFavoritesOnboarding(tabId: String): BrowserTabFragment {
            val fragment = BrowserTabFragment()
            val args = Bundle()
            args.putString(TAB_ID_ARG, tabId)
            args.putBoolean(FAVORITES_ONBOARDING_ARG, true)
            fragment.arguments = args
            return fragment
        }
    }

    inner class BrowserTabFragmentDecorator {

        fun decorateWithFeatures() {
            decorateToolbarWithButtons()
            createPopupMenu()
            configureShowTabSwitcherListener()
            configureLongClickOpensNewTabListener()
        }

        fun updateToolbarActionsVisibility(viewState: BrowserViewState) {
            tabsButton?.isVisible = viewState.showTabsButton
            fireMenuButton?.isVisible = viewState.fireButton is HighlightableButton.Visible
            menuButton?.isVisible = viewState.showMenuButton is HighlightableButton.Visible

            val targetView = if (viewState.showMenuButton.isHighlighted()) {
                omnibar.browserMenuImageView
            } else if (viewState.fireButton.isHighlighted()) {
                omnibar.fireIconImageView
            } else {
                null
            }

            // omnibar only scrollable when browser showing and the fire button is not promoted
            if (targetView != null) {
                omnibarScrolling.disableOmnibarScrolling(omnibar.toolbarContainer)
                playPulseAnimation(targetView)
            } else {
                if (viewState.browserShowing) {
                    omnibarScrolling.enableOmnibarScrolling(omnibar.toolbarContainer)
                }
                pulseAnimation.stop()
            }
        }

        private fun playPulseAnimation(targetView: View) {
            omnibar.toolbarContainer.doOnLayout {
                pulseAnimation.playOn(targetView)
            }
        }

        private fun decorateToolbarWithButtons() {
            fireMenuButton?.show()
            fireMenuButton?.setOnClickListener {
                browserActivity?.launchFire()
                pixel.fire(
                    AppPixelName.MENU_ACTION_FIRE_PRESSED.pixelName,
                    mapOf(FIRE_BUTTON_STATE to pulseAnimation.isActive.toString())
                )
            }

            tabsButton?.show()
        }

        private fun createPopupMenu() {
            popupMenu = PopupMenu(layoutInflater, R.layout.popup_window_browser_menu)
            val menuBinding = PopupWindowBrowserMenuBinding.bind(popupMenu.contentView)
            popupMenu.apply {
                onMenuItemClicked(menuBinding.forwardPopupMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_NAVIGATE_FORWARD_PRESSED)
                    viewModel.onUserPressedForward()
                }
                onMenuItemClicked(menuBinding.backPopupMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_NAVIGATE_BACK_PRESSED)
                    activity?.onBackPressed()
                }
                onMenuItemClicked(menuBinding.refreshPopupMenuItem) {
                    viewModel.onRefreshRequested()
                    pixel.fire(AppPixelName.MENU_ACTION_REFRESH_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.newTabPopupMenuItem) {
                    viewModel.userRequestedOpeningNewTab()
                    pixel.fire(AppPixelName.MENU_ACTION_NEW_TAB_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.bookmarksPopupMenuItem) {
                    browserActivity?.launchBookmarks()
                    pixel.fire(AppPixelName.MENU_ACTION_BOOKMARKS_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.fireproofWebsitePopupMenuItem) { launch { viewModel.onFireproofWebsiteMenuClicked() } }
                onMenuItemClicked(menuBinding.addBookmarksPopupMenuItem) {
                    viewModel.onBookmarkMenuClicked()
                }
                onMenuItemClicked(menuBinding.addFavoritePopupMenuItem) {
                    viewModel.onFavoriteMenuClicked()
                }
                onMenuItemClicked(menuBinding.findInPageMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_FIND_IN_PAGE_PRESSED)
                    viewModel.onFindInPageSelected()
                }
                onMenuItemClicked(menuBinding.whitelistPopupMenuItem) { viewModel.onWhitelistSelected() }
                onMenuItemClicked(menuBinding.brokenSitePopupMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_REPORT_BROKEN_SITE_PRESSED)
                    viewModel.onBrokenSiteSelected()
                }
                onMenuItemClicked(menuBinding.settingsPopupMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_SETTINGS_PRESSED)
                    browserActivity?.launchSettings()
                }
                onMenuItemClicked(menuBinding.requestDesktopSiteCheckMenuItem) {
                    viewModel.onDesktopSiteModeToggled(menuBinding.requestDesktopSiteCheckMenuItem.isChecked)
                }
                onMenuItemClicked(menuBinding.sharePageMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_SHARE_PRESSED)
                    viewModel.onShareSelected()
                }
                onMenuItemClicked(menuBinding.addToHome) {
                    pixel.fire(AppPixelName.MENU_ACTION_ADD_TO_HOME_PRESSED)
                    viewModel.onPinPageToHomeSelected()
                }
                onMenuItemClicked(menuBinding.newEmailAliasMenuItem) { viewModel.consumeAliasAndCopyToClipboard() }
                onMenuItemClicked(menuBinding.openInAppMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_APP_LINKS_OPEN_PRESSED)
                    viewModel.openAppLink()
                }
            }
            omnibar.browserMenu.setOnClickListener {
                viewModel.onBrowserMenuClicked()
                hideKeyboardImmediately()
                launchTopAnchoredPopupMenu()
            }
        }

        private fun launchTopAnchoredPopupMenu() {
            popupMenu.show(binding.rootView, omnibar.toolbar) {
                viewModel.onBrowserMenuClosed()
            }
            pixel.fire(AppPixelName.MENU_ACTION_POPUP_OPENED.pixelName)
        }

        private fun configureShowTabSwitcherListener() {
            tabsButton?.setOnClickListener {
                launch { viewModel.userLaunchingTabSwitcher() }
            }
        }

        private fun configureLongClickOpensNewTabListener() {
            tabsButton?.setOnLongClickListener {
                launch { viewModel.userRequestedOpeningNewTab() }
                return@setOnLongClickListener true
            }
        }

        fun animateTabsCount() {
            tabsButton?.animateCount()
        }

        fun renderTabIcon(tabs: List<TabEntity>) {
            context?.let {
                tabsButton?.count = tabs.count()
                tabsButton?.hasUnread = tabs.firstOrNull { !it.viewed } != null
            }
        }

        fun incrementTabs() {
            tabsButton?.increment {
                addTabsObserver()
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
        private var lastSeenPrivacyGradeViewState: PrivacyGradeViewState? = null

        fun renderPrivacyGrade(viewState: PrivacyGradeViewState) {

            renderIfChanged(viewState, lastSeenPrivacyGradeViewState) {

                val oldGrade = lastSeenPrivacyGradeViewState?.privacyGrade
                val oldShowEmptyGrade = lastSeenPrivacyGradeViewState?.showEmptyGrade
                val grade = viewState.privacyGrade
                val newShowEmptyGrade = viewState.showEmptyGrade

                val canChangeGrade = (oldGrade != grade && !newShowEmptyGrade) || (oldGrade == grade && oldShowEmptyGrade != newShowEmptyGrade)
                lastSeenPrivacyGradeViewState = viewState

                if (canChangeGrade) {
                    context?.let {
                        val drawable = if (viewState.showEmptyGrade || viewState.shouldAnimate) {
                            ContextCompat.getDrawable(it, R.drawable.privacygrade_icon_loading)
                        } else {
                            ContextCompat.getDrawable(it, viewState.privacyGrade.icon())
                        }
                        omnibar.privacyGradeButton?.setImageDrawable(drawable)
                    }
                }

                omnibar.privacyGradeButton?.isEnabled = viewState.isEnabled

                if (viewState.shouldAnimate) {
                    animatorHelper.startPulseAnimation(omnibar.privacyGradeButton)
                } else {
                    animatorHelper.stopPulseAnimation()
                }
            }
        }

        fun renderAutocomplete(viewState: AutoCompleteViewState) {
            renderIfChanged(viewState, lastSeenAutoCompleteViewState) {
                lastSeenAutoCompleteViewState = viewState

                if (viewState.showSuggestions || viewState.showFavorites) {
                    if (viewState.favorites.isNotEmpty() && viewState.showFavorites) {
                        binding.autoCompleteSuggestionsList.gone()
                        binding.quickAccessSuggestionsRecyclerView.show()
                        omnibarQuickAccessAdapter.submitList(viewState.favorites)
                    } else {
                        binding.autoCompleteSuggestionsList.show()
                        binding.quickAccessSuggestionsRecyclerView.gone()
                        autoCompleteSuggestionsAdapter.updateData(viewState.searchResults.query, viewState.searchResults.suggestions)
                    }
                } else {
                    binding.autoCompleteSuggestionsList.gone()
                    binding.quickAccessSuggestionsRecyclerView.gone()
                }
            }
        }

        fun renderOmnibar(viewState: OmnibarViewState) {
            renderIfChanged(viewState, lastSeenOmnibarViewState) {
                lastSeenOmnibarViewState = viewState

                if (viewState.isEditing) {
                    cancelTrackersAnimation()
                }

                if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
                    omnibar.omnibarTextInput.setText(viewState.omnibarText)
                    omnibar.appBarLayout.setExpanded(true, true)
                    if (viewState.shouldMoveCaretToEnd) {
                        omnibar.omnibarTextInput.setSelection(viewState.omnibarText.length)
                    }
                }

                lastSeenBrowserViewState?.let {
                    renderToolbarMenus(it)
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun renderLoadingIndicator(viewState: LoadingViewState) {
            renderIfChanged(viewState, lastSeenLoadingViewState) {
                lastSeenLoadingViewState = viewState

                omnibar.pageLoadingIndicator.apply {
                    if (viewState.isLoading) show()
                    smoothProgressAnimator.onNewProgress(viewState.progress) { if (!viewState.isLoading) hide() }
                }

                if (viewState.privacyOn) {
                    if (lastSeenOmnibarViewState?.isEditing == true) {
                        cancelTrackersAnimation()
                    }

                    if (viewState.progress == MAX_PROGRESS) {
                        createTrackersAnimation()
                    }
                }

                if (!viewState.isLoading && lastSeenBrowserViewState?.browserShowing == true) {
                    binding.swipeRefreshContainer.isRefreshing = false
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
                        animatorHelper.startTrackersAnimation(lastSeenCtaViewState?.cta, activity, omnibar.animationContainer, omnibarViews(), events)
                    }
                }
            }
        }

        fun cancelTrackersAnimation() {
            animatorHelper.cancelAnimations(omnibarViews(), omnibar.animationContainer)
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
                            binding.browserLayout.hide()
                        } else {
                            binding.browserLayout.show()
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

        fun applyAccessibilitySettings(viewState: AccessibilityViewState) {
            Timber.v("Accessibility: render state applyAccessibilitySettings $viewState")
            val webView = webView ?: return

            val fontSizeChanged = webView.settings.textZoom != viewState.fontSize.toInt()
            if (fontSizeChanged) {
                Timber.v(
                    "Accessibility: UpdateAccessibilitySetting fontSizeChanged " +
                        "from ${webView.settings.textZoom} to ${viewState.fontSize.toInt()}"
                )

                webView.settings.textZoom = viewState.fontSize.toInt()
            }

            if (this@BrowserTabFragment.isHidden && viewState.refreshWebView) return
            if (viewState.refreshWebView) {
                Timber.v("Accessibility: UpdateAccessibilitySetting forceZoomChanged")
                refresh()
            }
        }

        private fun renderPopupMenus(
            browserShowing: Boolean,
            viewState: BrowserViewState
        ) {
            PopupWindowBrowserMenuBinding.bind(popupMenu.contentView).apply {
                backPopupMenuItem.isEnabled = viewState.canGoBack
                forwardPopupMenuItem.isEnabled = viewState.canGoForward
                refreshPopupMenuItem.isEnabled = browserShowing
                newTabPopupMenuItem.isEnabled = browserShowing
                addBookmarksPopupMenuItem?.isEnabled = viewState.canAddBookmarks
                addBookmarksPopupMenuItem?.text =
                    getString(if (viewState.bookmark != null) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
                addFavoritePopupMenuItem?.isEnabled = viewState.addFavorite.isEnabled()
                addFavoritePopupMenuItem.text = when {
                    viewState.addFavorite.isHighlighted() -> getString(R.string.addFavoriteMenuTitleHighlighted)
                    viewState.favorite != null -> getString(R.string.removeFavoriteMenuTitle)
                    else -> getString(R.string.addFavoriteMenuTitle)
                }
                fireproofWebsitePopupMenuItem?.isEnabled = viewState.canFireproofSite
                fireproofWebsitePopupMenuItem?.isChecked = viewState.canFireproofSite && viewState.isFireproofWebsite
                sharePageMenuItem?.isEnabled = viewState.canSharePage
                whitelistPopupMenuItem?.isEnabled = viewState.canWhitelist
                whitelistPopupMenuItem?.text =
                    getText(if (viewState.isWhitelisted) R.string.enablePrivacyProtection else R.string.disablePrivacyProtection)
                brokenSitePopupMenuItem?.isEnabled = viewState.canReportSite
                requestDesktopSiteCheckMenuItem?.isEnabled = viewState.canChangeBrowsingMode
                requestDesktopSiteCheckMenuItem?.isChecked = viewState.isDesktopBrowsingMode

                newEmailAliasMenuItem?.let {
                    it.visibility = if (viewState.isEmailSignedIn) VISIBLE else GONE
                }

                addToHome?.let {
                    it.visibility = if (viewState.addToHomeVisible) VISIBLE else GONE
                    it.isEnabled = viewState.addToHomeEnabled
                }

                openInAppMenuItem?.let {
                    it.visibility = if (viewState.previousAppLink != null) VISIBLE else GONE
                }
            }
        }

        private fun renderToolbarMenus(viewState: BrowserViewState) {
            if (viewState.browserShowing) {
                omnibar.daxIcon?.isVisible = viewState.showDaxIcon
                omnibar.privacyGradeButton?.isInvisible = !viewState.showPrivacyGrade || viewState.showDaxIcon
                omnibar.clearTextButton?.isVisible = viewState.showClearButton
                omnibar.searchIcon?.isVisible = viewState.showSearchIcon
            } else {
                omnibar.daxIcon.isVisible = false
                omnibar.privacyGradeButton?.isVisible = false
                omnibar.clearTextButton?.isVisible = viewState.showClearButton
                omnibar.searchIcon?.isVisible = true
            }

            decorator.updateToolbarActionsVisibility(viewState)
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

            PopupWindowBrowserMenuBinding.bind(popupMenu.contentView).findInPageMenuItem?.isEnabled = viewState.canFindInPage
        }

        fun renderCtaViewState(viewState: CtaViewState) {
            if (isHidden) {
                return
            }

            renderIfChanged(viewState, lastSeenCtaViewState) {
                lastSeenCtaViewState = viewState
                removeNewTabLayoutClickListener()
                if (viewState.cta != null) {
                    showCta(viewState.cta, viewState.favorites)
                } else {
                    hideHomeCta()
                    hideDaxCta()
                    showHomeBackground(viewState.favorites)
                }
            }
        }

        private fun showCta(
            configuration: Cta,
            favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>
        ) {
            when (configuration) {
                is HomePanelCta -> showHomeCta(configuration, favorites)
                is DaxBubbleCta -> showDaxCta(configuration)
                is BubbleCta -> showBubleCta(configuration)
                is DialogCta -> showDaxDialogCta(configuration)
            }
        }

        private fun showDaxDialogCta(configuration: DialogCta) {
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
                viewModel.onCtaShown()
            }
        }

        private fun showDaxCta(configuration: DaxBubbleCta) {
            hideHomeBackground()
            hideHomeCta()
            configuration.showCta(daxDialogCta.daxCtaContainer)
            newBrowserTab.newTabLayout.setOnClickListener { daxDialogCta.dialogTextCta.finishAnimation() }

            if (configuration is DaxBubbleCta.DaxFireproofCta) {
                configureFireproofButtons()
            }

            viewModel.onCtaShown()
        }

        private fun showBubleCta(configuration: BubbleCta) {
            hideHomeBackground()
            hideHomeCta()
            configuration.showCta(daxDialogCta.daxCtaContainer)
            newBrowserTab.newTabLayout.setOnClickListener { daxDialogCta.dialogTextCta.finishAnimation() }
            viewModel.onCtaShown()
        }

        private fun removeNewTabLayoutClickListener() {
            newBrowserTab.newTabLayout.setOnClickListener(null)
        }

        private fun showHomeCta(
            configuration: HomePanelCta,
            favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>
        ) {
            hideDaxCta()
            if (newBrowserTab.ctaContainer.isEmpty()) {
                renderHomeCta()
            } else {
                configuration.showCta(newBrowserTab.ctaContainer)
            }
            showHomeBackground(favorites)
            viewModel.onCtaShown()
        }

        private fun showHomeBackground(favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
            if (favorites.isEmpty()) {
                homeBackgroundLogo.showLogo()
                quickAccessItems.quickAccessRecyclerView.gone()
            } else {
                homeBackgroundLogo.hideLogo()
                quickAccessAdapter.submitList(favorites)
                quickAccessItems.quickAccessRecyclerView.show()
            }

            newBrowserTab.newTabQuickAcessItemsLayout.show()
        }

        private fun hideHomeBackground() {
            homeBackgroundLogo.hideLogo()
            newBrowserTab.newTabQuickAcessItemsLayout.gone()
        }

        private fun hideDaxCta() {
            daxDialogCta.dialogTextCta.cancelAnimation()
            daxDialogCta.daxCtaContainer.hide()
        }

        private fun hideHomeCta() {
            newBrowserTab.ctaContainer.gone()
        }

        private fun configureFireproofButtons() {
            daxDialogCta.fireproofButtons.show()

            daxDialogCta.fireproofKeepMeSignedIn.setOnClickListener {
                daxDialogCta.fireproofButtons.gone()
                daxDialogCta.dialogTextCta.cancelAnimation()
                viewModel.userSelectedFireproofSetting(true)
            }

            daxDialogCta.fireproofBurnEverything.setOnClickListener {
                daxDialogCta.fireproofButtons.gone()
                daxDialogCta.dialogTextCta.cancelAnimation()
                viewModel.userSelectedFireproofSetting(false)
            }
        }

        fun renderHomeCta() {
            val context = context ?: return
            val cta = lastSeenCtaViewState?.cta ?: return
            val configuration = if (cta is HomePanelCta) cta else return

            newBrowserTab.ctaContainer.removeAllViews()

            inflate(context, R.layout.include_cta, newBrowserTab.ctaContainer)

            configuration.showCta(newBrowserTab.ctaContainer)
            newBrowserTab.ctaContainer.findViewById<Button>(R.id.ctaOkButton).setOnClickListener {
                viewModel.onUserClickCtaOkButton()
            }

            newBrowserTab.ctaContainer.findViewById<Button>(R.id.ctaDismissButton).setOnClickListener {
                viewModel.onUserDismissedCta()
            }
        }

        fun hideFindInPage() {
            if (findInPage.findInPageContainer.visibility != GONE) {
                binding.focusDummy.requestFocus()
                findInPage.findInPageContainer.gone()
                findInPage.findInPageInput.hideKeyboard()
            }
        }

        private fun showFindInPageView(viewState: FindInPageViewState) {

            if (findInPage.findInPageContainer.visibility != VISIBLE) {
                findInPage.findInPageContainer.show()
                findInPage.findInPageInput.postDelayed(KEYBOARD_DELAY) {
                    findInPage.findInPageInput?.showKeyboard()
                }
            }

            if (viewState.showNumberMatches) {
                findInPage.findInPageMatches.text = getString(R.string.findInPageMatches, viewState.activeMatchIndex, viewState.numberMatches)
                findInPage.findInPageMatches.show()
            } else {
                findInPage.findInPageMatches.hide()
            }
        }

        private fun goFullScreen() {
            Timber.i("Entering full screen")
            binding.webViewFullScreenContainer.show()
            activity?.toggleFullScreen()
        }

        private fun exitFullScreen() {
            Timber.i("Exiting full screen")
            binding.webViewFullScreenContainer.removeAllViews()
            binding.webViewFullScreenContainer.gone()
            activity?.toggleFullScreen()
            binding.focusDummy.requestFocus()
        }

        private fun shouldUpdateOmnibarTextInput(
            viewState: OmnibarViewState,
            omnibarInput: String?
        ) =
            (!viewState.isEditing || omnibarInput.isNullOrEmpty()) && omnibar.omnibarTextInput.isDifferent(omnibarInput)
    }

    override fun openExistingFile(file: File?) {
        if (file == null) {
            Toast.makeText(activity, R.string.downloadConfirmationUnableToOpenFileText, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = context?.let { createIntentToOpenFile(it, file) }
        activity?.packageManager?.let { packageManager ->
            if (intent?.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Timber.e("No suitable activity found")
                Toast.makeText(activity, R.string.downloadConfirmationUnableToOpenFileText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun replaceExistingFile(
        file: File?,
        pendingFileDownload: PendingFileDownload
    ) {
        Timber.i("Deleting existing file: $file")
        runCatching { file?.delete() }
        continueDownload(pendingFileDownload)
    }

    private fun showDownloadManagerAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = DownloadFailReason.DOWNLOAD_MANAGER_SETTINGS_URI
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Could not open DownloadManager settings")
            omnibar.toolbar.makeSnackbarWithNoBottomInset(R.string.downloadManagerIncompatible, Snackbar.LENGTH_INDEFINITE).show()
        }
    }

    private fun createIntentToOpenFile(
        context: Context,
        file: File
    ): Intent? {
        val uri = FileProvider.getUriForFile(context, "${appBuildConfig.applicationId}.provider", file)
        val mime = activity?.contentResolver?.getType(uri) ?: return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, mime)
        return intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun continueDownload(pendingFileDownload: PendingFileDownload) {
        Timber.i("Continuing to download %s", pendingFileDownload)
        viewModel.download(pendingFileDownload)
    }

    override fun cancelDownload() {
        viewModel.closeAndReturnToSourceIfBlankTab()
    }

    fun onFireDialogVisibilityChanged(isVisible: Boolean) {
        if (isVisible) {
            viewModel.ctaViewState.removeObserver(ctaViewStateObserver)
        } else {
            viewModel.ctaViewState.observe(viewLifecycleOwner, ctaViewStateObserver)
        }
    }

    override fun onSiteLocationPermissionSelected(
        domain: String,
        permission: LocationPermissionType
    ) {
        viewModel.onSiteLocationPermissionSelected(domain, permission)
    }

    override fun onSystemLocationPermissionAllowed() {
        viewModel.onSystemLocationPermissionAllowed()
    }

    override fun onSystemLocationPermissionNotAllowed() {
        viewModel.onSystemLocationPermissionNotAllowed()
    }

    override fun onSystemLocationPermissionNeverAllowed() {
        viewModel.onSystemLocationPermissionNeverAllowed()
    }
}
