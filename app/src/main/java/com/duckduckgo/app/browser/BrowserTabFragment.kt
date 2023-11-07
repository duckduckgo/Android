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
import android.app.PendingIntent
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.*
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.Editable
import android.view.*
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.HitTestResult.*
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.transaction
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.browser.BrowserTabViewModel.AccessibilityViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.AutoCompleteViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.BrowserViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.Command
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.BrowserTabViewModel.CtaViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.FindInPageViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.HighlightableButton
import com.duckduckgo.app.browser.BrowserTabViewModel.LoadingViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.NavigationCommand
import com.duckduckgo.app.browser.BrowserTabViewModel.OmnibarViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.PrivacyShieldViewState
import com.duckduckgo.app.browser.BrowserTabViewModel.SavedSiteChangedViewState
import com.duckduckgo.app.browser.DownloadConfirmationFragment.DownloadConfirmationDialogListener
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.databinding.ContentSiteLocationPermissionDialogBinding
import com.duckduckgo.app.browser.databinding.ContentSystemLocationPermissionDialogBinding
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.HttpAuthenticationBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeQuickAccessItemsBinding
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.app.browser.downloader.BlobConverterInjector
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.Companion.QUICK_ACCESS_ITEM_MAX_SIZE_DP
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.browser.favorites.QuickAccessDragTouchItemListener
import com.duckduckgo.app.browser.filechooser.FileChooserIntentBuilder
import com.duckduckgo.app.browser.history.NavigationHistorySheet
import com.duckduckgo.app.browser.history.NavigationHistorySheet.NavigationHistorySheetListener
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.menu.BrowserPopupMenu
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.browser.omnibar.QueryOrigin.FromAutocomplete
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.animations.TrackersAnimatorListener
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.browser.remotemessage.SharePromoLinkRMFBroadCastReceiver
import com.duckduckgo.app.browser.remotemessage.asMessage
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.ui.dialogs.AutomaticFireproofDialogOptions
import com.duckduckgo.app.browser.ui.dialogs.LaunchInExternalAppOptions
import com.duckduckgo.app.browser.urlextraction.DOMUrlExtractor
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebView
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebViewClient
import com.duckduckgo.app.browser.webview.enableDarkMode
import com.duckduckgo.app.browser.webview.enableLightMode
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.cta.ui.DaxDialogCta.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.downloads.DownloadsFileActions
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoFragment
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.app.global.extensions.html
import com.duckduckgo.app.global.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.global.view.NonDismissibleBehavior
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.disableAnimation
import com.duckduckgo.app.global.view.enableAnimation
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.global.view.isImmersiveModeEnabled
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.global.view.toggleFullScreen
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.ui.SurveyActivity
import com.duckduckgo.app.survey.ui.SurveyActivity.Companion.SurveySource
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillSettingsActivityLauncher
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.Callback
import com.duckduckgo.autofill.api.CredentialAutofillDialogFactory
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialSavePickerDialog
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpDialog
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpHandleVerificationLink
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpScreenNoParams
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpScreenResult
import com.duckduckgo.autofill.api.EmailProtectionUserPromptListener
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.credential.saving.DuckAddressLoginCreator
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.emailprotection.EmailInjector
import com.duckduckgo.autofill.api.store.AutofillStore.ContainsCredentialsResult.*
import com.duckduckgo.autofill.api.systemautofill.SystemAutofillUsageMonitor
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.mobile.android.ui.store.BrowserAppTheme
import com.duckduckgo.mobile.android.ui.view.*
import com.duckduckgo.mobile.android.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.mobile.android.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.mobile.android.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.cancellable
import timber.log.Timber

@InjectWith(FragmentScope::class)
class BrowserTabFragment :
    DuckDuckGoFragment(R.layout.fragment_browser_tab),
    FindListener,
    CoroutineScope,
    TrackersAnimatorListener,
    DownloadConfirmationDialogListener,
    SitePermissionsGrantedListener,
    AutofillEventListener,
    EmailProtectionUserPromptListener,
    SystemAutofillListener {

    private val supervisorJob = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = supervisorJob + dispatchers.main()

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var webViewClient: BrowserWebViewClient

    @Inject
    lateinit var webChromeClient: BrowserChromeClient

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var fileChooserIntentBuilder: FileChooserIntentBuilder

    @Inject
    lateinit var fileDownloader: FileDownloader

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
    lateinit var browserAutofill: BrowserAutofill

    @Inject
    lateinit var autofillSettingsLauncher: AutofillSettingsActivityLauncher

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var gridViewColumnCalculator: GridViewColumnCalculator

    @Inject
    lateinit var appTheme: BrowserAppTheme

    @Inject
    lateinit var accessibilitySettingsDataStore: AccessibilitySettingsDataStore

    @Inject
    lateinit var playStoreUtils: PlayStoreUtils

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    lateinit var addWidgetLauncher: AddWidgetLauncher

    @Inject
    lateinit var downloadsFileActions: DownloadsFileActions

    @Inject
    lateinit var urlExtractingWebViewClient: Provider<UrlExtractingWebViewClient>

    @Inject
    lateinit var urlExtractor: Provider<DOMUrlExtractor>

    @Inject
    lateinit var urlExtractorUserAgent: Provider<UserAgentProvider>

    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var printInjector: PrintInjector

    @Inject
    lateinit var credentialAutofillDialogFactory: CredentialAutofillDialogFactory

    @Inject
    lateinit var duckAddressInjectedResultHandler: DuckAddressLoginCreator

    @Inject
    lateinit var existingCredentialMatchDetector: ExistingCredentialMatchDetector

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var autoconsent: Autoconsent

    @Inject
    lateinit var autofillSettingsActivityLauncher: AutofillSettingsActivityLauncher

    @Inject
    lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

    @Inject
    lateinit var sitePermissionsDialogLauncher: SitePermissionsDialogLauncher

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var contentScopeScripts: ContentScopeScripts

    @Inject
    lateinit var systemAutofillUsageMonitor: SystemAutofillUsageMonitor

    /**
     * We use this to monitor whether the user was seeing the in-context Email Protection signup prompt
     * This is needed because the activity stack will be cleared if an external link is opened in our browser
     * We need to be able to determine if inContextEmailProtection view was showing. If it was, it will consume email verification links.
     */
    var inContextEmailProtectionShowing: Boolean = false

    private var urlExtractingWebView: UrlExtractingWebView? = null

    var messageFromPreviousTab: Message? = null

    private val initialUrl get() = requireArguments().getString(URL_EXTRA_ARG)

    private val skipHome get() = requireArguments().getBoolean(SKIP_HOME_ARG)

    private val favoritesOnboarding get() = requireArguments().getBoolean(FAVORITES_ONBOARDING_ARG, false)

    private lateinit var popupMenu: BrowserPopupMenu

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

    @Inject
    lateinit var autofillFragmentResultListeners: PluginPoint<AutofillFragmentResultsPlugin>

    private var isActiveTab: Boolean = false

    private val downloadMessagesJob = ConflatedJob()

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProvider(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl, skipHome, favoritesOnboarding)
        launchDownloadMessagesJob()
        viewModel
    }

    /*
        private val animatorHelper by lazy {
            BrowserTrackersAnimatorHelper(
                omnibarViews = listOf(clearTextButton, omnibarTextInput, searchIcon),
                privacyGradeView = privacyGradeButton,
                cookieView = cookieAnimation,
                cookieScene = scene_root,
                dummyCookieView = cookieDummyView,
                container = animationContainer,
                appTheme = appTheme
            )
        }
     */

    private val binding: FragmentBrowserTabBinding by viewBinding()

    private lateinit var omnibar: IncludeOmnibarToolbarBinding

    private lateinit var quickAccessItems: IncludeQuickAccessItemsBinding

    private lateinit var webViewContainer: FrameLayout

    private val findInPage
        get() = omnibar.findInPage

    private val newBrowserTab
        get() = binding.includeNewBrowserTab

    private val errorView
        get() = binding.includeErrorView

    private val daxDialogCta
        get() = binding.includeNewBrowserTab.includeDaxDialogCta

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

    private val activityResultHandlerEmailProtectionInContextSignup = registerForActivityResult(StartActivityForResult()) { result: ActivityResult ->
        when (result.resultCode) {
            EmailProtectionInContextSignUpScreenResult.SUCCESS -> {
                browserAutofill.inContextEmailProtectionFlowFinished()
                inContextEmailProtectionShowing = false
            }
            EmailProtectionInContextSignUpScreenResult.CANCELLED -> {
                browserAutofill.inContextEmailProtectionFlowFinished()
                inContextEmailProtectionShowing = false
            }
            else -> {
                // we don't set inContextEmailProtectionShowing to false here because the system is cancelling it
                // this is likely because of an external link being clicked (e.g., the email protection verification link)
            }
        }
    }

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
            viewModel.triggerAutocomplete(omnibar.omnibarTextInput.text.toString(), omnibar.omnibarTextInput.hasFocus(), true)
        }
    }

    private val showSuggestionsListener = object : ShowSuggestionsListener {
        override fun showSuggestions() {
            viewModel.triggerAutocomplete(omnibar.omnibarTextInput.text.toString(), omnibar.omnibarTextInput.hasFocus(), true)
        }
    }

    private val autoconsentCallback = object : AutoconsentCallback {
        override fun onFirstPopUpHandled() {
            // Remove comment to promote feature
            // ctaViewModel.enableAutoconsentCta()
            // launch {
            //     viewModel.refreshCta()
            // }
        }

        override fun onPopUpHandled(isCosmetic: Boolean) {
            launch {
                if (isCosmetic) {
                    delay(COOKIES_ANIMATION_DELAY)
                }
                context?.let {
                    animatorHelper.createCookiesAnimation(
                        it,
                        omnibarViews(),
                        omnibar.cookieDummyView,
                        omnibar.cookieAnimation,
                        omnibar.omnibarIconContainer.findViewById(R.id.scene_root),
                        isCosmetic,
                    )
                }
            }
        }

        override fun onResultReceived(
            consentManaged: Boolean,
            optOutFailed: Boolean,
            selfTestFailed: Boolean,
            isCosmetic: Boolean?,
        ) {
            viewModel.onAutoconsentResultReceived(consentManaged, optOutFailed, selfTestFailed, isCosmetic)
        }
    }

    private val autofillCallback = object : Callback {
        override suspend fun onCredentialsAvailableToInject(
            originalUrl: String,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
        ) {
            withContext(dispatchers.main()) {
                showAutofillDialogChooseCredentials(originalUrl, credentials, triggerType)
            }
        }

        override suspend fun onGeneratedPasswordAvailableToUse(
            originalUrl: String,
            username: String?,
            generatedPassword: String,
        ) {
            // small delay added to let keyboard disappear if it was present; helps avoid jarring transition
            delay(100)

            withContext(dispatchers.main()) {
                showUserAutoGeneratedPasswordDialog(originalUrl, username, generatedPassword)
            }
        }

        override fun noCredentialsAvailable(originalUrl: String) {
            viewModel.returnNoCredentialsWithPage(originalUrl)
        }

        override fun onCredentialsSaved(savedCredentials: LoginCredentials) {
            viewModel.onShowUserCredentialsSaved(savedCredentials)
        }

        override suspend fun onCredentialsAvailableToSave(
            currentUrl: String,
            credentials: LoginCredentials,
        ) {
            val username = credentials.username
            val password = credentials.password

            if (username == null && password == null) {
                Timber.w("Not saving credentials with null username and password")
                return
            }

            val matchType = existingCredentialMatchDetector.determine(currentUrl, username, password)
            Timber.v("MatchType is %s", matchType.javaClass.simpleName)

            // we need this delay to ensure web navigation / form submission events aren't blocked
            delay(100)

            withContext(dispatchers.main()) {
                when (matchType) {
                    ExactMatch -> Timber.w("Credentials already exist for %s", currentUrl)
                    UsernameMatch -> showAutofillDialogUpdatePassword(currentUrl, credentials)
                    UsernameMissing -> showAutofillDialogUpdateUsername(currentUrl, credentials)
                    NoMatch -> showAutofillDialogSaveCredentials(currentUrl, credentials)
                    UrlOnlyMatch -> showAutofillDialogSaveCredentials(currentUrl, credentials)
                }
            }
        }

        private fun showUserAutoGeneratedPasswordDialog(
            originalUrl: String,
            username: String?,
            generatedPassword: String,
        ) {
            val url = webView?.url ?: return
            if (url != originalUrl) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }
            val dialog = credentialAutofillDialogFactory.autofillGeneratePasswordDialog(url, username, generatedPassword, tabId)
            showDialogHidingPrevious(dialog, UseGeneratedPasswordDialog.TAG, originalUrl)
        }

        private fun showAutofillDialogChooseCredentials(
            originalUrl: String,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
        ) {
            if (triggerType == LoginTriggerType.AUTOPROMPT && !(viewModel.canAutofillSelectCredentialsDialogCanAutomaticallyShow())) {
                Timber.d("AutoPrompt is disabled, not showing dialog")
                return
            }
            val url = webView?.url ?: return
            if (url != originalUrl) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }
            val dialog = credentialAutofillDialogFactory.autofillSelectCredentialsDialog(url, credentials, triggerType, tabId)
            showDialogHidingPrevious(dialog, CredentialAutofillPickerDialog.TAG, originalUrl)
        }
    }

    private val homeBackgroundLogo by lazy { HomeBackgroundLogo(newBrowserTab.ddgLogo) }

    private val ctaViewStateObserver = Observer<CtaViewState> {
        it?.let { renderer.renderCtaViewState(it) }
    }

    private var alertDialog: DaxAlertDialog? = null

    private var appLinksSnackBar: Snackbar? = null

    private var loginDetectionDialog: DaxAlertDialog? = null

    private var automaticFireproofDialog: DaxAlertDialog? = null

    private val pulseAnimation: PulseAnimation = PulseAnimation(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        removeDaxDialogFromActivity()
        renderer = BrowserTabFragmentRenderer()
        decorator = BrowserTabFragmentDecorator()
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) {
            when (it) {
                is VoiceSearchLauncher.Event.VoiceRecognitionSuccess -> {
                    omnibar.omnibarTextInput.setText(it.result)
                    userEnteredQuery(it.result)
                    resumeWebView()
                }
                is VoiceSearchLauncher.Event.SearchCancelled -> resumeWebView()
                is VoiceSearchLauncher.Event.VoiceSearchDisabled -> viewModel.voiceSearchDisabled()
            }
        }
        sitePermissionsDialogLauncher.registerPermissionLauncher(this)
    }

    private fun resumeWebView() {
        webView?.let {
            if (it.isShown) it.onResume()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        omnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
        quickAccessItems = IncludeQuickAccessItemsBinding.bind(binding.rootView)
        webViewContainer = binding.webViewContainer
        configureObservers()
        configurePrivacyShield()
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

        lifecycle.addObserver(
            @SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (isVisible) {
                        updateOrDeleteWebViewPreview()
                    }
                }
            },
        )

        childFragmentManager.findFragmentByTag(ADD_SAVED_SITE_FRAGMENT_TAG)?.let { dialog ->
            (dialog as EditSavedSiteDialogFragment).listener = viewModel
            dialog.deleteBookmarkListener = viewModel
        }
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

        viewModel.onMessageReceived()
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
        resumeWebView()
    }

    override fun onPause() {
        dismissDownloadFragment()
        super.onPause()
    }

    override fun onStop() {
        alertDialog?.dismiss()
        super.onStop()
    }

    override fun onDestroyView() {
        binding.swipeRefreshContainer.removeCanChildScrollUpCallback()
        webView?.removeEnableSwipeRefreshCallback()
        webView?.stopNestedScroll()
        webView?.stopLoading()
        super.onDestroyView()
    }

    private fun dismissDownloadFragment() {
        val fragment = fragmentManager?.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG) as? DownloadConfirmationFragment
        fragment?.dismiss()
    }

    private fun addHomeShortcut(
        homeShortcut: Command.AddHomeShortcut,
        context: Context,
    ) {
        shortcutBuilder.requestPinShortcut(context, homeShortcut)
    }

    private fun configureObservers() {
        viewModel.autoCompleteViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderAutocomplete(it) }
            },
        )

        viewModel.globalLayoutState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderGlobalViewState(it) }
            },
        )

        viewModel.browserViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderBrowserViewState(it) }
            },
        )

        viewModel.loadingViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderLoadingIndicator(it) }
            },
        )

        viewModel.omnibarViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderOmnibar(it) }
            },
        )

        viewModel.findInPageViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.renderFindInPageState(it) }
            },
        )

        viewModel.accessibilityViewState.observe(
            viewLifecycleOwner,
            Observer {
                it?.let { renderer.applyAccessibilitySettings(it) }
            },
        )

        viewModel.ctaViewState.observe(viewLifecycleOwner, ctaViewStateObserver)

        viewModel.command.observe(
            viewLifecycleOwner,
            Observer {
                processCommand(it)
            },
        )

        viewModel.survey.observe(
            viewLifecycleOwner,
            Observer {
                it.let { viewModel.onSurveyChanged(it) }
            },
        )

        viewModel.privacyShieldViewState.observe(
            viewLifecycleOwner,
            Observer {
                it.let { renderer.renderPrivacyShield(it) }
            },
        )

        addTabsObserver()
    }

    private fun processFileDownloadedCommand(command: DownloadCommand) {
        when (command) {
            is DownloadCommand.ShowDownloadStartedMessage -> downloadStarted(command)
            is DownloadCommand.ShowDownloadFailedMessage -> downloadFailed(command)
            is DownloadCommand.ShowDownloadSuccessMessage -> downloadSucceeded(command)
        }
    }

    @SuppressLint("WrongConstant")
    private fun downloadStarted(command: DownloadCommand.ShowDownloadStartedMessage) {
        view?.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), DOWNLOAD_SNACKBAR_LENGTH)?.show()
    }

    private fun downloadFailed(command: DownloadCommand.ShowDownloadFailedMessage) {
        val downloadFailedSnackbar = view?.makeSnackbarWithNoBottomInset(getString(command.messageId), Snackbar.LENGTH_LONG)
        view?.postDelayed({ downloadFailedSnackbar?.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun downloadSucceeded(command: DownloadCommand.ShowDownloadSuccessMessage) {
        val downloadSucceededSnackbar = view?.makeSnackbarWithNoBottomInset(getString(command.messageId, command.fileName), Snackbar.LENGTH_LONG)
            ?.apply {
                this.setAction(R.string.downloadsDownloadFinishedActionName) {
                    val result = downloadsFileActions.openFile(requireActivity(), File(command.filePath))
                    if (!result) {
                        view.makeSnackbarWithNoBottomInset(getString(R.string.downloadsCannotOpenFileErrorMessage), Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        view?.postDelayed({ downloadSucceededSnackbar?.show() }, DOWNLOAD_SNACKBAR_DELAY)
    }

    private fun addTabsObserver() {
        viewModel.tabs.observe(
            viewLifecycleOwner,
            Observer {
                it?.let {
                    decorator.renderTabIcon(it)
                }
            },
        )

        viewModel.liveSelectedTab.distinctUntilChanged().observe(
            viewLifecycleOwner,
            Observer {
                it?.let {
                    val wasActive = isActiveTab
                    isActiveTab = it.tabId == tabId
                    if (wasActive && !isActiveTab) {
                        Timber.v("Tab %s is newly inactive", tabId)

                        // want to ensure that we aren't offering to inject credentials from an inactive tab
                        hideDialogWithTag(CredentialAutofillPickerDialog.TAG)
                    }
                }
            },
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
        errorView.errorLayout.gone()
    }

    private fun showBrowser() {
        newBrowserTab.newTabLayout.gone()
        binding.browserLayout.show()
        webView?.show()
        webView?.onResume()
        errorView.errorLayout.gone()
    }

    private fun showError(errorType: WebViewErrorResponse, url: String?) {
        binding.browserLayout.gone()
        newBrowserTab.newTabLayout.gone()
        omnibar.appBarLayout.setExpanded(true)
        omnibar.shieldIcon.isInvisible = true
        webView?.onPause()
        webView?.hide()
        errorView.errorMessage.text = getString(errorType.errorId, url).html(requireContext())
        if (appTheme.isLightModeEnabled()) {
            errorView.yetiIcon?.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_yeti_light)
        } else {
            errorView.yetiIcon?.setImageResource(com.duckduckgo.mobile.android.R.drawable.ic_yeti_dark)
        }
        errorView.errorLayout.show()
    }

    fun submitQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    private fun navigate(
        url: String,
        headers: Map<String, String>,
    ) {
        hideKeyboard()
        renderer.hideFindInPage()
        viewModel.registerDaxBubbleCtaDismissed()
        webView?.loadUrl(url, headers)
    }

    fun onRefreshRequested() {
        viewModel.onRefreshRequested()
    }

    override fun onAutofillStateChange() {
        viewModel.onRefreshRequested()
    }

    override fun onRejectGeneratedPassword(originalUrl: String) {
        rejectGeneratedPassword(originalUrl)
    }

    override fun onAcceptGeneratedPassword(originalUrl: String) {
        acceptGeneratedPassword(originalUrl)
    }

    override fun onUseEmailProtectionPrivateAlias(originalUrl: String, duckAddress: String) {
        viewModel.usePrivateDuckAddress(originalUrl, duckAddress)
    }

    override fun onUseEmailProtectionPersonalAddress(originalUrl: String, duckAddress: String) {
        viewModel.usePersonalDuckAddress(originalUrl, duckAddress)
    }

    override fun onSelectedToSignUpForInContextEmailProtection() {
        showEmailProtectionInContextWebFlow()
    }

    override fun onEndOfEmailProtectionInContextSignupFlow() {
        webView?.let {
            browserAutofill.inContextEmailProtectionFlowFinished()
        }
    }

    override fun onSavedCredentials(credentials: LoginCredentials) {
        viewModel.onShowUserCredentialsSaved(credentials)
    }

    override fun onUpdatedCredentials(credentials: LoginCredentials) {
        viewModel.onShowUserCredentialsUpdated(credentials)
    }

    override fun onNoCredentialsChosenForAutofill(originalUrl: String) {
        viewModel.returnNoCredentialsWithPage(originalUrl)
    }

    override fun onShareCredentialsForAutofill(originalUrl: String, selectedCredentials: LoginCredentials) {
        injectAutofillCredentials(originalUrl, selectedCredentials)
    }

    fun refresh() {
        webView?.reload()
        viewModel.onWebViewRefreshed()
    }

    private fun processCommand(it: Command?) {
        if (it is NavigationCommand) {
            renderer.cancelTrackersAnimation()
        }

        when (it) {
            is NavigationCommand.Refresh -> refresh()
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
            is Command.DeleteFireproofConfirmation -> removeFireproofWebsiteConfirmation(it.fireproofWebsiteEntity)
            is Command.ShowPrivacyProtectionEnabledConfirmation -> privacyProtectionEnabledConfirmation(it.domain)
            is Command.ShowPrivacyProtectionDisabledConfirmation -> privacyProtectionDisabledConfirmation(it.domain)
            is NavigationCommand.Navigate -> {
                dismissAppLinkSnackBar()
                navigate(it.url, it.headers)
            }

            is NavigationCommand.NavigateBack -> {
                dismissAppLinkSnackBar()
                webView?.goBackOrForward(-it.steps)
            }

            is NavigationCommand.NavigateForward -> {
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
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }

            is Command.DownloadImage -> requestImageDownload(it.url, it.requestUserConfirmation)
            is Command.FindInPageCommand -> webView?.findAllAsync(it.searchTerm)
            is Command.DismissFindInPage -> webView?.findAllAsync("")
            is Command.ShareLink -> launchSharePageChooser(it.url, it.title)
            is Command.SharePromoLinkRMF -> launchSharePromoRMFPageChooser(it.url, it.shareTitle)
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
                    headers = it.headers,
                )
            }

            is Command.ExtractUrlFromCloakedAmpLink -> {
                extractUrlFromAmpLink(it.initialUrl)
            }

            is Command.LoadExtractedUrl -> {
                webView?.loadUrl(it.extractedUrl)
                destroyUrlExtractingWebView()
            }

            is Command.LaunchSurvey -> launchSurvey(it.survey)
            is Command.LaunchPlayStore -> launchPlayStore(it.appPackage)
            is Command.SubmitUrl -> submitQuery(it.url)
            is Command.LaunchAddWidget -> addWidgetLauncher.launchAddWidget(activity)
            is Command.LaunchDefaultBrowser -> launchDefaultBrowser()
            is Command.LaunchAppTPOnboarding -> launchAppTPOnboardingScreen()
            is Command.RequiresAuthentication -> showAuthenticationDialog(it.request)
            is Command.SaveCredentials -> saveBasicAuthCredentials(it.request, it.credentials)
            is Command.GenerateWebViewPreviewImage -> generateWebViewPreviewImage()
            is Command.LaunchTabSwitcher -> launchTabSwitcher()
            is Command.ShowErrorWithAction -> showErrorSnackbar(it)
            is Command.DaxCommand.FinishPartialTrackerAnimation -> finishPartialTrackerAnimation()
            is Command.DaxCommand.HideDaxDialog -> showHideTipsDialog(it.cta)
            is Command.HideWebContent -> webView?.hide()
            is Command.ShowWebContent -> webView?.show()
            is Command.CheckSystemLocationPermission -> checkSystemLocationPermission(it.domain, it.deniedForever)
            is Command.RequestSystemLocationPermission -> requestLocationPermissions()
            is Command.AskDomainPermission -> askSiteLocationPermission(it.domain)
            is Command.RefreshUserAgent -> refreshUserAgent(it.url, it.isDesktop)
            is Command.AskToFireproofWebsite -> askToFireproofWebsite(requireContext(), it.fireproofWebsite)
            is Command.AskToAutomateFireproofWebsite -> askToAutomateFireproofWebsite(requireContext(), it.fireproofWebsite)
            is Command.AskToDisableLoginDetection -> askToDisableLoginDetection(requireContext())
            is Command.ShowDomainHasPermissionMessage -> showDomainHasLocationPermission(it.domain)
            is Command.ConvertBlobToDataUri -> convertBlobToDataUri(it)
            is Command.RequestFileDownload -> requestFileDownload(it.url, it.contentDisposition, it.mimeType, it.requestUserConfirmation)
            is Command.ChildTabClosed -> processUriForThirdPartyCookies()
            is Command.CopyAliasToClipboard -> copyAliasToClipboard(it.alias)
            is Command.InjectEmailAddress -> injectEmailAddress(
                alias = it.duckAddress,
                originalUrl = it.originalUrl,
                autoSaveLogin = it.autoSaveLogin,
            )

            is Command.ShowEmailProtectionChooseEmailPrompt -> showEmailProtectionChooseEmailDialog(it.address)
            is Command.ShowEmailProtectionInContextSignUpPrompt -> showNativeInContextEmailProtectionSignupPrompt()

            is Command.CancelIncomingAutofillRequest -> injectAutofillCredentials(it.url, null)
            is Command.LaunchAutofillSettings -> launchAutofillManagementScreen()
            is Command.EditWithSelectedQuery -> {
                omnibar.omnibarTextInput.setText(it.query)
                omnibar.omnibarTextInput.setSelection(it.query.length)
            }

            is ShowBackNavigationHistory -> showBackNavigationHistory(it)
            is NavigationCommand.NavigateToHistory -> navigateBackHistoryStack(it.historyStackIndex)
            is Command.EmailSignEvent -> {
                notifyEmailSignEvent()
            }

            is Command.PrintLink -> launchPrint(it.url, it.mediaSize)
            is Command.ShowSitePermissionsDialog -> showSitePermissionsDialog(it.permissionsToRequest, it.request)
            is Command.GrantSitePermissionRequest -> grantSitePermissionRequest(it.sitePermissionsToGrant, it.request)
            is Command.ShowUserCredentialSavedOrUpdatedConfirmation -> showAuthenticationSavedOrUpdatedSnackbar(
                loginCredentials = it.credentials,
                messageResourceId = it.messageResourceId,
                includeShortcutToViewCredential = it.includeShortcutToViewCredential,
            )

            is Command.WebViewError -> showError(it.errorType, it.url)
            else -> {
                // NO OP
            }
        }
    }

    private fun extractUrlFromAmpLink(initialUrl: String) {
        context?.let {
            val client = urlExtractingWebViewClient.get()
            client.urlExtractionListener = viewModel

            Timber.d("AMP link detection: Creating WebView for URL extraction")
            urlExtractingWebView = UrlExtractingWebView(requireContext(), client, urlExtractorUserAgent.get(), urlExtractor.get())

            urlExtractingWebView?.urlExtractionListener = viewModel

            Timber.d("AMP link detection: Loading AMP URL for extraction")
            urlExtractingWebView?.loadUrl(initialUrl)
        }
    }

    private fun destroyUrlExtractingWebView() {
        urlExtractingWebView?.destroyWebView()
        urlExtractingWebView = null
    }

    private fun injectEmailAddress(
        alias: String,
        originalUrl: String,
        autoSaveLogin: Boolean,
    ) {
        webView?.let {
            if (it.url != originalUrl) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }

            emailInjector.injectAddressInEmailField(it, alias, it.url)

            if (autoSaveLogin) {
                duckAddressInjectedResultHandler.createLoginForPrivateDuckAddress(
                    duckAddress = alias,
                    tabId = tabId,
                    originalUrl = originalUrl,
                )
            }
        }
    }

    private fun notifyEmailSignEvent() {
        webView?.let {
            emailInjector.notifyWebAppSignEvent(it, it.url)
        }
    }

    private fun copyAliasToClipboard(alias: String) {
        context?.let {
            val clipboard: ClipboardManager? = ContextCompat.getSystemService(it, ClipboardManager::class.java)
            val clip: ClipData = ClipData.newPlainText("Alias", alias)
            clipboard?.setPrimaryClip(clip)
            binding.rootView.makeSnackbarWithNoBottomInset(
                getString(R.string.aliasToClipboardMessage),
                Snackbar.LENGTH_LONG,
            ).show()
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

    private fun locationPermissionsHaveNotBeenGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
    }

    private fun checkSystemLocationPermission(
        domain: String,
        deniedForever: Boolean,
    ) {
        if (locationPermissionsHaveNotBeenGranted()) {
            if (deniedForever) {
                viewModel.onSystemLocationPermissionDeniedForever()
            } else {
                showSystemLocationPermissionDialog(domain)
            }
        } else {
            viewModel.onSystemLocationPermissionGranted()
        }
    }

    private fun showSystemLocationPermissionDialog(domain: String) {
        val binding = ContentSystemLocationPermissionDialogBinding.inflate(layoutInflater)

        val originUrl = domain.websiteFromGeoLocationsApiOrigin()
        val subtitle = getString(R.string.preciseLocationSystemDialogSubtitle, originUrl, originUrl)
        binding.systemPermissionDialogSubtitle.text = subtitle

        val dialog = CustomAlertDialogBuilder(requireActivity())
            .setView(binding)
            .build()

        binding.allowLocationPermission.setOnClickListener {
            viewModel.onSystemLocationPermissionAllowed()
            dialog.dismiss()
        }

        binding.denyLocationPermission.setOnClickListener {
            viewModel.onSystemLocationPermissionNotAllowed()
            dialog.dismiss()
        }

        binding.neverAllowLocationPermission.setOnClickListener {
            viewModel.onSystemLocationPermissionNeverAllowed()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun requestLocationPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            PERMISSION_REQUEST_GEO_LOCATION,
        )
    }

    private fun askSiteLocationPermission(domain: String) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        val binding = ContentSiteLocationPermissionDialogBinding.inflate(layoutInflater)

        val title = domain.websiteFromGeoLocationsApiOrigin()
        binding.sitePermissionDialogTitle.text = getString(R.string.preciseLocationSiteDialogTitle, title)
        binding.sitePermissionDialogSubtitle.text = if (title == DDG_DOMAIN) {
            getString(R.string.preciseLocationDDGDialogSubtitle)
        } else {
            getString(R.string.preciseLocationSiteDialogSubtitle)
        }
        lifecycleScope.launch {
            faviconManager.loadToViewFromLocalWithPlaceholder(tabId, domain, binding.sitePermissionDialogFavicon)
        }

        val dialog = CustomAlertDialogBuilder(requireActivity())
            .setView(binding)
            .build()

        binding.siteAllowAlwaysLocationPermission.setOnClickListener {
            viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ALWAYS)
            dialog.dismiss()
        }

        binding.siteAllowOnceLocationPermission.setOnClickListener {
            viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.ALLOW_ONCE)
            dialog.dismiss()
        }

        binding.siteDenyOnceLocationPermission.setOnClickListener {
            viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.DENY_ONCE)
            dialog.dismiss()
        }

        binding.siteDenyAlwaysLocationPermission.setOnClickListener {
            viewModel.onSiteLocationPermissionSelected(domain, LocationPermissionType.DENY_ALWAYS)
            dialog.dismiss()
        }

        dialog.show()
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
                Snackbar.LENGTH_SHORT,
            )
        snackbar.view.setOnClickListener {
            browserActivity?.launchSitePermissionsSettings()
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
                Snackbar.LENGTH_LONG,
            )
                .setAction(action) {
                    pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_OPEN_ACTION_PRESSED)
                    openAppLink(appLink)
                }
                .addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onShown(transientBottomBar: Snackbar?) {
                            super.onShown(transientBottomBar)
                            pixel.fire(AppPixelName.APP_LINKS_SNACKBAR_SHOWN)
                        }

                        override fun onDismissed(
                            transientBottomBar: Snackbar?,
                            event: Int,
                        ) {
                            super.onDismissed(transientBottomBar, event)
                        }
                    },
                )

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

    @Suppress("NewApi") // we use appBuildConfig
    private fun openAppLink(appLink: SpecialUrlDetector.UrlType.AppLink) {
        if (appLink.appIntent != null) {
            appLink.appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                startActivityOrQuietlyFail(appLink.appIntent)
            } catch (e: SecurityException) {
                showToast(R.string.unableToOpenLink)
            }
        } else if (appLink.excludedComponents != null && appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
            val title = getString(R.string.appLinkIntentChooserTitle)
            val chooserIntent = getChooserIntent(appLink.uriString, title, appLink.excludedComponents)
            startActivityOrQuietlyFail(chooserIntent)
        }
        viewModel.clearPreviousUrl()
    }

    private fun startActivityOrQuietlyFail(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    private fun dismissAppLinkSnackBar() {
        appLinksSnackBar?.dismiss()
        appLinksSnackBar = null
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getChooserIntent(
        url: String?,
        title: String,
        excludedComponents: List<ComponentName>,
    ): Intent {
        val urlIntent = Intent.parseUri(url, Intent.URI_ANDROID_APP_SCHEME)
        urlIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val chooserIntent = Intent.createChooser(urlIntent, title)
        chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
        return chooserIntent
    }

    private fun openExternalDialog(
        intent: Intent,
        fallbackUrl: String? = null,
        fallbackIntent: Intent? = null,
        useFirstActivityFound: Boolean = true,
        headers: Map<String, String> = emptyMap(),
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
        useFirstActivityFound: Boolean,
    ) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

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
        fireproofWebsite: FireproofWebsiteEntity,
    ) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        val isShowing = loginDetectionDialog?.isShowing()

        if (isShowing != true) {
            loginDetectionDialog = TextAlertDialogBuilder(context)
                .setTitle(getString(R.string.fireproofWebsiteLoginDialogTitle, fireproofWebsite.website()))
                .setMessage(R.string.fireproofWebsiteLoginDialogDescription)
                .setPositiveButton(R.string.fireproofWebsiteLoginDialogPositive).setNegativeButton(R.string.fireproofWebsiteLoginDialogNegative)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            viewModel.onUserConfirmedFireproofDialog(fireproofWebsite.domain)
                        }

                        override fun onNegativeButtonClicked() {
                            viewModel.onUserDismissedFireproofLoginDialog()
                        }

                        override fun onDialogShown() {
                            viewModel.onFireproofLoginDialogShown()
                        }
                    },
                )
                .build()
            loginDetectionDialog!!.show()
        }
    }

    private fun askToAutomateFireproofWebsite(
        context: Context,
        fireproofWebsite: FireproofWebsiteEntity,
    ) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        val isShowing = automaticFireproofDialog?.isShowing()

        if (isShowing != true) {
            automaticFireproofDialog = StackedAlertDialogBuilder(context)
                .setTitle(R.string.automaticFireproofWebsiteLoginDialogTitle)
                .setMessage(getString(R.string.automaticFireproofWebsiteLoginDialogDescription))
                .setStackedButtons(AutomaticFireproofDialogOptions.asOptions())
                .addEventListener(
                    object : StackedAlertDialogBuilder.EventListener() {
                        override fun onButtonClicked(position: Int) {
                            when (AutomaticFireproofDialogOptions.getOptionFromPosition(position)) {
                                AutomaticFireproofDialogOptions.ALWAYS -> {
                                    viewModel.onUserEnabledAutomaticFireproofLoginDialog(fireproofWebsite.domain)
                                }

                                AutomaticFireproofDialogOptions.FIREPROOF_THIS_SITE -> {
                                    viewModel.onUserFireproofSiteInAutomaticFireproofLoginDialog(fireproofWebsite.domain)
                                }

                                AutomaticFireproofDialogOptions.NOT_NOW -> {
                                    viewModel.onUserDismissedAutomaticFireproofLoginDialog()
                                }
                            }
                        }

                        override fun onDialogShown() {
                            viewModel.onFireproofLoginDialogShown()
                        }
                    },
                )
                .build()
            automaticFireproofDialog!!.show()
        }
    }

    private fun askToDisableLoginDetection(context: Context) {
        TextAlertDialogBuilder(context)
            .setTitle(getString(R.string.disableLoginDetectionDialogTitle))
            .setMessage(R.string.disableLoginDetectionDialogDescription)
            .setPositiveButton(R.string.disableLoginDetectionDialogPositive)
            .setNegativeButton(R.string.disableLoginDetectionDialogNegative)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.onUserConfirmedDisableLoginDetectionDialog()
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.onUserDismissedDisableLoginDetectionDialog()
                    }

                    override fun onDialogShown() {
                        viewModel.onDisableLoginDetectionDialogShown()
                    }
                },
            )
            .show()
    }

    private fun launchExternalAppDialog(
        context: Context,
        onClick: () -> Unit,
    ) {
        val isShowing = alertDialog?.isShowing()

        if (isShowing != true) {
            alertDialog = StackedAlertDialogBuilder(context)
                .setTitle(R.string.launchingExternalApp)
                .setMessage(getString(R.string.confirmOpenExternalApp))
                .setStackedButtons(LaunchInExternalAppOptions.asOptions())
                .addEventListener(
                    object : StackedAlertDialogBuilder.EventListener() {
                        override fun onButtonClicked(position: Int) {
                            when (LaunchInExternalAppOptions.getOptionFromPosition(position)) {
                                LaunchInExternalAppOptions.OPEN -> onClick()
                                LaunchInExternalAppOptions.CLOSE_TAB -> {
                                    launch {
                                        viewModel.closeCurrentTab()
                                        destroyWebView()
                                    }
                                }

                                LaunchInExternalAppOptions.CANCEL -> {} // no-op
                            }
                        }
                    },
                )
                .build()
            alertDialog!!.show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
            handleFileUploadResult(resultCode, data)
        }
    }

    private fun handleFileUploadResult(
        resultCode: Int,
        intent: Intent?,
    ) {
        if (resultCode != RESULT_OK || intent == null) {
            Timber.i("Received resultCode $resultCode (or received null intent) indicating user did not select any files")
            pendingUploadTask?.onReceiveValue(null)
            return
        }

        val uris = fileChooserIntentBuilder.extractSelectedFileUris(intent)
        pendingUploadTask?.onReceiveValue(uris)
    }

    private fun showToast(@StringRes messageId: Int, length: Int = Toast.LENGTH_LONG) {
        Toast.makeText(context?.applicationContext, messageId, length).show()
    }

    private fun showAuthenticationDialog(request: BasicAuthenticationRequest) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        val authDialogBinding = HttpAuthenticationBinding.inflate(layoutInflater)
        authDialogBinding.httpAuthInformationText.text = getString(R.string.authenticationDialogMessage, request.site)
        CustomAlertDialogBuilder(requireActivity())
            .setPositiveButton(R.string.authenticationDialogPositiveButton)
            .setNegativeButton(R.string.authenticationDialogNegativeButton)
            .setView(authDialogBinding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        viewModel.handleAuthentication(
                            request,
                            BasicAuthenticationCredentials(
                                username = authDialogBinding.usernameInput.text,
                                password = authDialogBinding.passwordInput.text,
                            ),
                        )
                    }

                    override fun onNegativeButtonClicked() {
                        viewModel.cancelAuthentication(request)
                    }

                    override fun onDialogShown() {
                        authDialogBinding.usernameInput.showKeyboardDelayed()
                    }
                },
            )
            .show()
    }

    private fun saveBasicAuthCredentials(
        request: BasicAuthenticationRequest,
        credentials: BasicAuthenticationCredentials,
    ) {
        webView?.let {
            webViewHttpAuthStore.setHttpAuthUsernamePassword(
                it,
                host = request.host,
                realm = request.realm,
                username = credentials.username,
                password = credentials.password,
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
            },
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
        apapter: FavoritesQuickAccessAdapter,
    ): ItemTouchHelper {
        return ItemTouchHelper(
            QuickAccessDragTouchItemListener(
                apapter,
                object : QuickAccessDragTouchItemListener.DragDropListener {
                    override fun onListChanged(listElements: List<QuickAccessFavorite>) {
                        viewModel.onQuickAccessListChanged(listElements)
                        recyclerView.disableAnimation()
                    }
                },
            ),
        ).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun createQuickAccessAdapter(
        originPixel: AppPixelName,
        onMoveListener: (RecyclerView.ViewHolder) -> Unit,
    ): FavoritesQuickAccessAdapter {
        return FavoritesQuickAccessAdapter(
            this,
            faviconManager,
            onMoveListener,
            {
                pixel.fire(originPixel)
                viewModel.onUserSubmittedQuery(it.favorite.url)
            },
            { viewModel.onEditSavedSiteRequested(it.favorite) },
            { viewModel.onDeleteQuickAccessItemRequested(it.favorite) },
        )
    }

    private fun configureQuickAccessGridLayout(recyclerView: RecyclerView) {
        val numOfColumns = gridViewColumnCalculator.calculateNumberOfColumns(QUICK_ACCESS_ITEM_MAX_SIZE_DP, QUICK_ACCESS_GRID_MAX_COLUMNS)
        val layoutManager = GridLayoutManager(requireContext(), numOfColumns)
        recyclerView.layoutManager = layoutManager
        val sidePadding = gridViewColumnCalculator.calculateSidePadding(QUICK_ACCESS_ITEM_MAX_SIZE_DP, numOfColumns)
        recyclerView.setPadding(sidePadding, recyclerView.paddingTop, sidePadding, recyclerView.paddingBottom)
    }

    private fun configurePrivacyShield() {
        omnibar.shieldIcon.setOnClickListener {
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
                viewModel.triggerAutocomplete(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
                if (hasFocus) {
                    cancelPendingAutofillRequestsToChooseCredentials()
                } else {
                    omnibar.omnibarTextInput.hideKeyboard()
                    binding.focusDummy.requestFocus()
                }
            }

        omnibar.omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                omnibar.omnibarTextInput.hideKeyboard()
                binding.focusDummy.requestFocus()
                //  Allow the event to be handled by the next receiver.
                return false
            }
        }

        omnibar.omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    userEnteredQuery(omnibar.omnibarTextInput.text.toString())
                    return@OnEditorActionListener true
                }
                false
            },
        )

        omnibar.clearTextButton.setOnClickListener { omnibar.omnibarTextInput.setText("") }
    }

    private fun userSelectedAutocomplete(suggestion: AutoCompleteSuggestion) {
        // send pixel before submitting the query and changing the autocomplete state to empty; otherwise will send the wrong params
        appCoroutineScope.launch {
            viewModel.fireAutocompletePixel(suggestion)
            withContext(dispatchers.main()) {
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
            true,
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
            emailInjector.addJsInterface(
                it,
                onSignedInEmailProtectionPromptShown = { viewModel.showEmailProtectionChooseEmailPrompt() },
                onInContextEmailProtectionSignupPromptShown = { showNativeInContextEmailProtectionSignupPrompt() },
            )
            configureWebViewForAutofill(it)
            printInjector.addJsInterface(it) { viewModel.printFromWebView() }
            autoconsent.addJsInterface(it, autoconsentCallback)
            contentScopeScripts.addJsInterface(it)
        }

        if (appBuildConfig.isDebug) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    private fun configureWebViewForAutofill(it: DuckDuckGoWebView) {
        browserAutofill.addJsInterface(it, autofillCallback, this, null, tabId)

        autofillFragmentResultListeners.getPlugins().forEach { plugin ->
            setFragmentResultListener(plugin.resultKey(tabId)) { _, result ->
                context?.let {
                    plugin.processResult(
                        result = result,
                        context = it,
                        tabId = tabId,
                        fragment = this@BrowserTabFragment,
                        autofillCallback = this@BrowserTabFragment,
                    )
                }
            }
        }

        it.systemAutofillListener = this
    }

    private fun injectAutofillCredentials(
        url: String,
        credentials: LoginCredentials?,
    ) {
        webView?.let {
            if (it.url != url) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }
            browserAutofill.injectCredentials(credentials)
        }
    }

    private fun acceptGeneratedPassword(url: String) {
        webView?.let {
            if (it.url != url) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }
            browserAutofill.acceptGeneratedPassword()
        }
    }

    private fun rejectGeneratedPassword(url: String) {
        webView?.let {
            if (it.url != url) {
                Timber.w("WebView url has changed since autofill request; bailing")
                return
            }
            browserAutofill.rejectGeneratedPassword()
        }
    }

    private fun cancelPendingAutofillRequestsToChooseCredentials() {
        browserAutofill.cancelPendingAutofillRequestToChooseCredentials()
        viewModel.cancelPendingAutofillRequestToChooseCredentials()
    }

    private fun showAutofillDialogSaveCredentials(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        val url = webView?.url ?: return
        if (url != currentUrl) return

        val dialog = credentialAutofillDialogFactory.autofillSavingCredentialsDialog(url, credentials, tabId)
        showDialogHidingPrevious(dialog, CredentialSavePickerDialog.TAG)
    }

    private fun showAutofillDialogUpdatePassword(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        val url = webView?.url ?: return
        if (url != currentUrl) return

        val dialog = credentialAutofillDialogFactory.autofillSavingUpdatePasswordDialog(url, credentials, tabId)
        showDialogHidingPrevious(dialog, CredentialUpdateExistingCredentialsDialog.TAG)
    }

    private fun showAutofillDialogUpdateUsername(
        currentUrl: String,
        credentials: LoginCredentials,
    ) {
        val url = webView?.url ?: return
        if (url != currentUrl) return

        val dialog = credentialAutofillDialogFactory.autofillSavingUpdateUsernameDialog(url, credentials, tabId)
        showDialogHidingPrevious(dialog, CredentialUpdateExistingCredentialsDialog.TAG)
    }

    private fun showAuthenticationSavedOrUpdatedSnackbar(
        loginCredentials: LoginCredentials,
        @StringRes messageResourceId: Int,
        includeShortcutToViewCredential: Boolean,
        delay: Long = 200,
    ) {
        lifecycleScope.launch(dispatchers.main()) {
            delay(delay)
            val snackbar = binding.browserLayout.makeSnackbarWithNoBottomInset(messageResourceId, Snackbar.LENGTH_LONG)
            if (includeShortcutToViewCredential) {
                snackbar.setAction(R.string.autofillSnackbarAction) {
                    context?.let { startActivity(autofillSettingsActivityLauncher.intentDirectlyViewCredentials(it, loginCredentials)) }
                }
            }
            snackbar.show()
        }
    }

    private fun launchAutofillManagementScreen() {
        startActivity(autofillSettingsLauncher.intentAlsoShowSuggestionsForSite(requireContext(), webView?.url))
    }

    private fun showDialogHidingPrevious(
        dialog: DialogFragment,
        tag: String,
        requiredUrl: String? = null,
    ) {
        // want to ensure lifecycle is at least resumed before attempting to show dialog
        lifecycleScope.launchWhenResumed {
            hideDialogWithTag(tag)

            val currentUrl = webView?.url
            val urlMatch = requiredUrl == null || requiredUrl == currentUrl
            if (isActiveTab && urlMatch) {
                Timber.i("Showing dialog (%s), hidden=%s, requiredUrl=%s, currentUrl=%s, tabId=%s", tag, isHidden, requiredUrl, currentUrl, tabId)
                dialog.show(childFragmentManager, tag)
            } else {
                Timber.w("Not showing dialog (%s), hidden=%s, requiredUrl=%s, currentUrl=%s, tabId=%s", tag, isHidden, requiredUrl, currentUrl, tabId)
            }
        }
    }

    private fun hideDialogWithTag(tag: String) {
        childFragmentManager.findFragmentByTag(tag)?.let {
            Timber.i("Found existing dialog for %s; removing it now", tag)
            if (it is DaxDialog) {
                it.setDaxDialogListener(null) // Avoids calling onDaxDialogDismiss()
            }
            childFragmentManager.commitNow(allowStateLoss = true) { remove(it) }
        }
    }

    private fun showDialogIfNotExist(
        dialog: DialogFragment,
        tag: String,
    ) {
        childFragmentManager.findFragmentByTag(tag)?.let {
            return
        }
        dialog.show(childFragmentManager, tag)
    }

    private fun configureDarkThemeSupport(webSettings: WebSettings) {
        when (appTheme.isLightModeEnabled()) {
            true -> webSettings.enableLightMode()
            false -> webSettings.enableDarkMode()
        }
    }

    private fun configureSwipeRefresh() {
        val metrics = resources.displayMetrics
        val distanceToTrigger = (DEFAULT_CIRCLE_TARGET_TIMES_1_5 * metrics.density).toInt()
        binding.swipeRefreshContainer.setDistanceToTriggerSync(distanceToTrigger)
        binding.swipeRefreshContainer.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), com.duckduckgo.mobile.android.R.color.cornflowerBlue),
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
        omnibar.omnibarTextInput.showSuggestionsListener = showSuggestionsListener
    }

    override fun onCreateContextMenu(
        menu: ContextMenu,
        view: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
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
                type = hitTestResult.type,
            )

            hitTestResult.type == SRC_IMAGE_ANCHOR_TYPE -> LongPressTarget(
                url = getTargetUrlForImageSource(),
                imageUrl = hitTestResult.extra,
                type = hitTestResult.type,
            )

            else -> LongPressTarget(
                url = hitTestResult.extra,
                type = hitTestResult.type,
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
            is Bookmark -> R.string.bookmarkAddedMessage
            is Favorite -> R.string.favoriteAddedMessage
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
            savedSiteChangedViewState.bookmarkFolder?.id ?: SavedSitesNames.BOOKMARKS_ROOT,
            savedSiteChangedViewState.bookmarkFolder?.name,
        )
        addBookmarkDialog.show(childFragmentManager, ADD_SAVED_SITE_FRAGMENT_TAG)
        addBookmarkDialog.listener = viewModel
        addBookmarkDialog.deleteBookmarkListener = viewModel
    }

    private fun confirmDeleteSavedSite(savedSite: SavedSite) {
        val message = when (savedSite) {
            is Favorite -> getString(R.string.favoriteDeleteConfirmationMessage)
            is Bookmark -> getString(R.string.bookmarkDeleteConfirmationMessage, savedSite.title).html(requireContext())
        }
        viewModel.deleteQuickAccessItem(savedSite)
        binding.rootView.makeSnackbarWithNoBottomInset(
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.insertQuickAccessItem(savedSite)
        }.show()
    }

    private fun fireproofWebsiteConfirmation(entity: FireproofWebsiteEntity) {
        binding.rootView.makeSnackbarWithNoBottomInset(
            HtmlCompat.fromHtml(getString(R.string.fireproofWebsiteSnackbarConfirmation, entity.website()), FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.onFireproofWebsiteSnackbarUndoClicked(entity)
        }.show()
    }

    private fun removeFireproofWebsiteConfirmation(entity: FireproofWebsiteEntity) {
        binding.rootView.makeSnackbarWithNoBottomInset(
            getString(R.string.fireproofDeleteConfirmationMessage),
            Snackbar.LENGTH_LONG,
        ).apply {
            setAction(R.string.fireproofWebsiteSnackbarAction) {
                viewModel.onRemoveFireproofWebsiteSnackbarUndoClicked(entity)
            }
            show()
        }
    }

    private fun privacyProtectionEnabledConfirmation(domain: String) {
        binding.rootView.makeSnackbarWithNoBottomInset(
            HtmlCompat.fromHtml(getString(R.string.privacyProtectionEnabledConfirmationMessage, domain), FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_LONG,
        ).show()
    }

    private fun privacyProtectionDisabledConfirmation(domain: String) {
        binding.rootView.makeSnackbarWithNoBottomInset(
            HtmlCompat.fromHtml(getString(R.string.privacyProtectionDisabledConfirmationMessage, domain), FROM_HTML_MODE_LEGACY),
            Snackbar.LENGTH_LONG,
        ).show()
    }

    private fun launchSharePageChooser(url: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).also {
            it.type = "text/plain"
            it.putExtra(Intent.EXTRA_TEXT, url)
            it.putExtra(Intent.EXTRA_SUBJECT, title)
            it.putExtra(Intent.EXTRA_TITLE, title)
        }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    private fun launchSharePromoRMFPageChooser(url: String, shareTitle: String) {
        val share = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_TITLE, shareTitle)
            type = "text/plain"
        }

        val pi = PendingIntent.getBroadcast(
            requireContext(),
            0,
            Intent(requireContext(), SharePromoLinkRMFBroadCastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            startActivity(Intent.createChooser(share, null, pi.intentSender))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "Activity not found")
        }
    }

    override fun onFindResultReceived(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
        isDoneCounting: Boolean,
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
        isDesktop: Boolean,
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
        when {
            hidden -> onTabHidden()
            else -> onTabVisible()
        }
    }

    private fun onTabHidden() {
        viewModel.onViewHidden()
        downloadMessagesJob.cancel()
        webView?.onPause()
    }

    private fun onTabVisible() {
        webView?.onResume()
        launchDownloadMessagesJob()
        viewModel.onViewVisible()
    }

    private fun launchDownloadMessagesJob() {
        downloadMessagesJob += lifecycleScope.launch {
            viewModel.downloadCommands().cancellable().collect {
                processFileDownloadedCommand(it)
            }
        }
    }

    /**
     * We don't destroy the activity on config changes like orientation, so we need to ensure we update resources which might change based on config
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        newBrowserTab.ddgLogo.setImageResource(com.duckduckgo.mobile.android.R.drawable.logo_full)
        if (newBrowserTab.ctaContainer.isNotEmpty()) {
            renderer.renderHomeCta()
        }
        renderer.recreateDaxDialogCta()
        configureQuickAccessGridLayout(quickAccessItems.quickAccessRecyclerView)
        configureQuickAccessGridLayout(binding.quickAccessSuggestionsRecyclerView)
        decorator.recreatePopupMenu()
        viewModel.onConfigurationChanged()
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
        dismissAppLinkSnackBar()
        pulseAnimation.stop()
        animatorHelper.removeListener()
        supervisorJob.cancel()
        popupMenu.dismiss()
        loginDetectionDialog?.dismiss()
        automaticFireproofDialog?.dismiss()
        browserAutofill.removeJsInterface()
        destroyWebView()
        super.onDestroy()
    }

    private fun destroyWebView() {
        webViewContainer.removeAllViews()
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
        requestUserConfirmation: Boolean,
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            subfolder = Environment.DIRECTORY_DOWNLOADS,
        )

        if (hasWriteStoragePermission()) {
            downloadFile(requestUserConfirmation && !URLUtil.isDataUrl(url))
        } else {
            requestWriteStoragePermission()
        }
    }

    private fun requestImageDownload(
        url: String,
        requestUserConfirmation: Boolean,
    ) {
        pendingFileDownload = PendingFileDownload(
            url = url,
            subfolder = Environment.DIRECTORY_DOWNLOADS,
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
        showDialogHidingPrevious(downloadConfirmationFragment, DOWNLOAD_CONFIRMATION_TAG)
    }

    private fun launchFilePicker(command: Command.ShowFileChooser) {
        pendingUploadTask = command.filePathCallback
        val canChooseMultipleFiles = command.fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(command.fileChooserParams.acceptTypes, canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
    }

    private fun minSdk30(): Boolean {
        return appBuildConfig.sdkInt >= Build.VERSION_CODES.R
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun hasWriteStoragePermission(): Boolean {
        return minSdk30() ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestWriteStoragePermission() {
        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.i("Write external storage permission granted")
                    downloadFile(requestUserConfirmation = true)
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
                        viewModel.onSystemLocationPermissionDeniedTwice()
                    }
                }
            }
        }
    }

    private fun launchPlayStore(appPackage: String) {
        playStoreUtils.launchPlayStore(appPackage)
    }

    @Suppress("NewApi") // we use appBuildConfig
    private fun launchDefaultBrowser() {
        if (appBuildConfig.sdkInt >= Build.VERSION_CODES.N) {
            requireActivity().launchDefaultAppActivity()
        }
    }

    private fun launchAppTPOnboardingScreen() {
        globalActivityStarter.start(requireContext(), AppTrackerOnboardingActivityWithEmptyParamsParams)
    }

    private fun launchSurvey(survey: Survey) {
        context?.let {
            startActivity(SurveyActivity.intent(it, survey, SurveySource.IN_APP))
        }
    }

    private fun finishPartialTrackerAnimation() {
        animatorHelper.finishPartialTrackerAnimation()
    }

    private fun showHideTipsDialog(cta: Cta) {
        context?.let {
            launchHideTipsDialog(it, cta)
        }
    }

    private fun showBackNavigationHistory(history: ShowBackNavigationHistory) {
        activity?.let { context ->
            NavigationHistorySheet(
                context,
                viewLifecycleOwner,
                faviconManager,
                tabId,
                history,
                object : NavigationHistorySheetListener {
                    override fun historicalPageSelected(stackIndex: Int) {
                        viewModel.historicalPageSelected(stackIndex)
                    }
                },
            ).show()
        }
    }

    private fun navigateBackHistoryStack(index: Int) {
        val stepsToMove = (index + 1) * -1
        webView?.goBackOrForward(stepsToMove)
    }

    fun onLongPressBackButton() {
        /*
         It is possible that this can be invoked before Fragment is attached
         If viewModelFactory isn't initialized, ignore long press
         */
        if (this::viewModelFactory.isInitialized) {
            viewModel.onUserLongPressedBack()
        }
    }

    private fun launchHideTipsDialog(
        context: Context,
        cta: Cta,
    ) {
        TextAlertDialogBuilder(context)
            .setTitle(R.string.hideTipsTitle)
            .setMessage(getString(R.string.hideTipsText))
            .setPositiveButton(R.string.hideTipsButton)
            .setNegativeButton(android.R.string.no)
            .addEventListener(
                object : TextAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        launch {
                            ctaViewModel.hideTipsForever(cta)
                        }
                    }
                },
            )
            .show()
    }

    fun omnibarViews(): List<View> = listOf(omnibar.clearTextButton, omnibar.omnibarTextInput, omnibar.searchIcon)

    override fun onAnimationFinished() {
        // NO OP
    }

    private fun showEmailProtectionChooseEmailDialog(address: String) {
        context?.let {
            val url = webView?.url ?: return

            val dialog = credentialAutofillDialogFactory.autofillEmailProtectionEmailChooserDialog(
                url = url,
                personalDuckAddress = address,
                tabId = tabId,
            )
            showDialogHidingPrevious(dialog, EmailProtectionChooseEmailDialog.TAG, url)
        }
    }

    override fun showNativeInContextEmailProtectionSignupPrompt() {
        context?.let {
            val url = webView?.url ?: return

            val dialog = credentialAutofillDialogFactory.emailProtectionInContextSignUpDialog(
                tabId = tabId,
            )
            showDialogHidingPrevious(dialog, EmailProtectionInContextSignUpDialog.TAG, url)
        }
    }

    fun showEmailProtectionInContextWebFlow(verificationUrl: String? = null) {
        context?.let {
            val params = if (verificationUrl == null) {
                EmailProtectionInContextSignUpScreenNoParams
            } else {
                EmailProtectionInContextSignUpHandleVerificationLink(verificationUrl)
            }
            val intent = globalActivityStarter.startIntent(it, params)
            activityResultHandlerEmailProtectionInContextSignup.launch(intent)
            inContextEmailProtectionShowing = true
        }
    }

    override fun showNativeChooseEmailAddressPrompt() {
        viewModel.showEmailProtectionChooseEmailPrompt()
    }

    companion object {
        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val URL_EXTRA_ARG = "URL_EXTRA_ARG"
        private const val SKIP_HOME_ARG = "SKIP_HOME_ARG"
        private const val FAVORITES_ONBOARDING_ARG = "FAVORITES_ONBOARDING_ARG"
        private const val DDG_DOMAIN = "duckduckgo.com"

        private const val ADD_SAVED_SITE_FRAGMENT_TAG = "ADD_SAVED_SITE"
        private const val KEYBOARD_DELAY = 200L

        private const val REQUEST_CODE_CHOOSE_FILE = 100
        private const val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 200
        private const val PERMISSION_REQUEST_GEO_LOCATION = 300

        private const val URL_BUNDLE_KEY = "url"

        private const val DOWNLOAD_CONFIRMATION_TAG = "DOWNLOAD_CONFIRMATION_TAG"
        private const val DAX_DIALOG_DIALOG_TAG = "DAX_DIALOG_TAG"

        private const val MAX_PROGRESS = 100
        private const val TRACKERS_INI_DELAY = 500L
        private const val TRACKERS_SECONDARY_DELAY = 200L

        private const val DEFAULT_CIRCLE_TARGET_TIMES_1_5 = 96

        private const val QUICK_ACCESS_GRID_MAX_COLUMNS = 6

        private const val COOKIES_ANIMATION_DELAY = 400L

        fun newInstance(
            tabId: String,
            query: String? = null,
            skipHome: Boolean,
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

        fun recreatePopupMenu() {
            popupMenu.dismiss()
            createPopupMenu()
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
                webView?.setBottomMatchingBehaviourEnabled(false)
            } else {
                if (viewState.browserShowing) {
                    omnibarScrolling.enableOmnibarScrolling(omnibar.toolbarContainer)
                }
                if (pulseAnimation.isActive) {
                    webView?.setBottomMatchingBehaviourEnabled(true) // only execute if animation is playing
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
                    mapOf(FIRE_BUTTON_STATE to pulseAnimation.isActive.toString()),
                )
            }

            tabsButton?.show()
        }

        private fun createPopupMenu() {
            popupMenu = BrowserPopupMenu(
                context = requireContext(),
                layoutInflater = layoutInflater,
            )
            val menuBinding = PopupWindowBrowserMenuBinding.bind(popupMenu.contentView)
            popupMenu.apply {
                onMenuItemClicked(menuBinding.forwardMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_NAVIGATE_FORWARD_PRESSED)
                    viewModel.onUserPressedForward()
                }
                onMenuItemClicked(menuBinding.backMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_NAVIGATE_BACK_PRESSED)
                    activity?.onBackPressed()
                }
                onMenuItemLongClicked(menuBinding.backMenuItem) {
                    viewModel.onUserLongPressedBack()
                }
                onMenuItemClicked(menuBinding.refreshMenuItem) {
                    viewModel.onRefreshRequested()
                    pixel.fire(AppPixelName.MENU_ACTION_REFRESH_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.newTabMenuItem) {
                    viewModel.userRequestedOpeningNewTab()
                    pixel.fire(AppPixelName.MENU_ACTION_NEW_TAB_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.bookmarksMenuItem) {
                    browserActivity?.launchBookmarks()
                    pixel.fire(AppPixelName.MENU_ACTION_BOOKMARKS_PRESSED.pixelName)
                }
                onMenuItemClicked(menuBinding.fireproofWebsiteMenuItem) {
                    viewModel.onFireproofWebsiteMenuClicked()
                }
                onMenuItemClicked(menuBinding.addBookmarksMenuItem) {
                    viewModel.onBookmarkMenuClicked()
                }
                onMenuItemClicked(menuBinding.addFavoriteMenuItem) {
                    viewModel.onFavoriteMenuClicked()
                }
                onMenuItemClicked(menuBinding.findInPageMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_FIND_IN_PAGE_PRESSED)
                    viewModel.onFindInPageSelected()
                }
                onMenuItemClicked(menuBinding.privacyProtectionMenuItem) { viewModel.onPrivacyProtectionMenuClicked() }
                onMenuItemClicked(menuBinding.brokenSiteMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_REPORT_BROKEN_SITE_PRESSED)
                    viewModel.onBrokenSiteSelected()
                }
                onMenuItemClicked(menuBinding.downloadsMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_DOWNLOADS_PRESSED)
                    browserActivity?.launchDownloads()
                }
                onMenuItemClicked(menuBinding.settingsMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_SETTINGS_PRESSED)
                    browserActivity?.launchSettings()
                }
                onMenuItemClicked(menuBinding.changeBrowserModeMenuItem) {
                    viewModel.onChangeBrowserModeClicked()
                }
                onMenuItemClicked(menuBinding.sharePageMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_SHARE_PRESSED)
                    viewModel.onShareSelected()
                }
                onMenuItemClicked(menuBinding.addToHomeMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_ADD_TO_HOME_PRESSED)
                    viewModel.onPinPageToHomeSelected()
                }
                onMenuItemClicked(menuBinding.createAliasMenuItem) { viewModel.consumeAliasAndCopyToClipboard() }
                onMenuItemClicked(menuBinding.openInAppMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_APP_LINKS_OPEN_PRESSED)
                    viewModel.openAppLink()
                }
                onMenuItemClicked(menuBinding.printPageMenuItem) {
                    viewModel.onPrintSelected()
                }
                onMenuItemClicked(menuBinding.autofillMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_AUTOFILL_PRESSED)
                    viewModel.onAutofillMenuSelected()
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
        private var lastSeenPrivacyShieldViewState: PrivacyShieldViewState? = null

        fun renderPrivacyShield(viewState: PrivacyShieldViewState) {
            renderIfChanged(viewState, lastSeenPrivacyShieldViewState) {
                if (viewState.privacyShield != UNKNOWN) {
                    lastSeenPrivacyShieldViewState = viewState
                    privacyShieldView.setAnimationView(omnibar.shieldIcon, viewState.privacyShield)
                    cancelTrackersAnimation()
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
                    if (viewState.forceExpand) {
                        omnibar.appBarLayout.setExpanded(true, true)
                    }
                    if (viewState.shouldMoveCaretToEnd) {
                        omnibar.omnibarTextInput.setSelection(viewState.omnibarText.length)
                    }
                }

                lastSeenBrowserViewState?.let {
                    renderToolbarMenus(it)
                }
            }
        }

        private fun renderVoiceSearch(viewState: BrowserViewState) {
            if (viewState.showVoiceSearch) {
                omnibar.voiceSearchButton.visibility = VISIBLE
                omnibar.voiceSearchButton.setOnClickListener {
                    webView?.onPause()
                    hideKeyboardImmediately()
                    voiceSearchLauncher.launch(requireActivity())
                }
            } else {
                omnibar.voiceSearchButton.visibility = GONE
            }
        }

        @SuppressLint("SetTextI18n")
        fun renderLoadingIndicator(viewState: LoadingViewState) {
            renderIfChanged(viewState, lastSeenLoadingViewState) {
                lastSeenLoadingViewState = viewState

                if (viewState.progress == MAX_PROGRESS) {
                    webView?.setBottomMatchingBehaviourEnabled(true)
                }

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
                if (isHidden) {
                    return@launch
                }
                if (lastSeenOmnibarViewState?.isEditing != true) {
                    val site = viewModel.siteLiveData.value
                    val events = site?.orderedTrackerBlockedEntities()
                    activity?.let { activity ->
                        animatorHelper.startTrackersAnimation(
                            context = activity,
                            shouldRunPartialAnimation = lastSeenCtaViewState?.cta is DaxTrackersBlockedCta,
                            shieldAnimationView = omnibar.shieldIcon,
                            trackersAnimationView = omnibar.trackersAnimation,
                            omnibarViews = omnibarViews(),
                            entities = events,
                        )
                    }
                }
            }
        }

        fun cancelTrackersAnimation() {
            animatorHelper.cancelAnimations(omnibarViews())
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
                val errorChanged = viewState.browserError != lastSeenBrowserViewState?.browserError

                lastSeenBrowserViewState = viewState
                if (browserShowingChanged) {
                    if (browserShowing) {
                        showBrowser()
                    } else {
                        showHome()
                    }
                } else if (errorChanged) {
                    if (viewState.browserError != OMITTED) {
                        showError(viewState.browserError, webView?.url)
                    } else {
                        if (browserShowing) {
                            showBrowser()
                        } else {
                            showHome()
                        }
                    }
                }

                renderToolbarMenus(viewState)
                popupMenu.renderState(browserShowing, viewState)
                renderFullscreenMode(viewState)
                renderVoiceSearch(viewState)
                omnibar.spacer.isVisible = viewState.showVoiceSearch && lastSeenBrowserViewState?.showClearButton ?: false
            }
        }

        private fun renderFullscreenMode(viewState: BrowserViewState) {
            if (!this@BrowserTabFragment.isVisible) return
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
                        "from ${webView.settings.textZoom} to ${viewState.fontSize.toInt()}",
                )

                webView.settings.textZoom = viewState.fontSize.toInt()
            }

            if (this@BrowserTabFragment.isHidden && viewState.refreshWebView) return
            if (viewState.refreshWebView) {
                Timber.v("Accessibility: UpdateAccessibilitySetting forceZoomChanged")
                refresh()
            }
        }

        private fun renderToolbarMenus(viewState: BrowserViewState) {
            if (viewState.browserShowing) {
                omnibar.daxIcon?.isVisible = viewState.showDaxIcon
                omnibar.shieldIcon?.isInvisible = !viewState.showPrivacyShield || viewState.showDaxIcon
                omnibar.clearTextButton?.isVisible = viewState.showClearButton
                omnibar.searchIcon?.isVisible = viewState.showSearchIcon
            } else {
                omnibar.daxIcon.isVisible = false
                omnibar.shieldIcon?.isVisible = false
                omnibar.clearTextButton?.isVisible = viewState.showClearButton
                omnibar.searchIcon?.isVisible = true
            }

            omnibar.spacer.isVisible = viewState.showClearButton && lastSeenBrowserViewState?.showVoiceSearch ?: false

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
        }

        fun renderCtaViewState(viewState: CtaViewState) {
            if (isHidden) {
                return
            }

            renderIfChanged(viewState, lastSeenCtaViewState) {
                val newMessage = (viewState.message?.id != lastSeenCtaViewState?.message?.id)
                lastSeenCtaViewState = viewState
                removeNewTabLayoutClickListener()
                Timber.v("RMF: render $newMessage, $viewState")
                when {
                    viewState.cta != null -> {
                        showCta(viewState.cta, viewState.favorites)
                    }

                    viewState.message != null -> {
                        showRemoteMessage(viewState.message, newMessage)
                        showHomeBackground(viewState.favorites, hideLogo = true)
                        hideHomeCta()
                    }

                    else -> {
                        hideHomeCta()
                        hideDaxCta()
                        newBrowserTab.messageCta.gone()
                        showHomeBackground(viewState.favorites)
                    }
                }
            }
        }

        private fun showRemoteMessage(
            message: RemoteMessage,
            newMessage: Boolean,
        ) {
            val shouldRender = newMessage || newBrowserTab.messageCta.isGone

            if (shouldRender) {
                Timber.i("RMF: render $message")
                newBrowserTab.messageCta.show()
                viewModel.onMessageShown()
                newBrowserTab.messageCta.setMessage(message.asMessage())
                newBrowserTab.messageCta.onCloseButtonClicked {
                    viewModel.onMessageCloseButtonClicked()
                }
                newBrowserTab.messageCta.onPrimaryActionClicked {
                    viewModel.onMessagePrimaryButtonClicked()
                }
                newBrowserTab.messageCta.onSecondaryActionClicked {
                    viewModel.onMessageSecondaryButtonClicked()
                }
                newBrowserTab.messageCta.onPromoActionClicked {
                    viewModel.onMessageActionButtonClicked()
                }
            }
        }

        private fun showCta(
            configuration: Cta,
            favorites: List<QuickAccessFavorite>,
        ) {
            when (configuration) {
                is HomePanelCta -> showHomeCta(configuration, favorites)
                is DaxBubbleCta -> showDaxCta(configuration)
                is BubbleCta -> showBubbleCta(configuration)
                is DialogCta -> showDaxDialogCta(configuration)
            }
            newBrowserTab.messageCta.gone()
        }

        fun recreateDaxDialogCta() {
            val configuration = lastSeenCtaViewState?.cta
            if (configuration is DaxDialogCta) {
                activity?.let { activity ->
                    val listener = if (configuration is DaxAutoconsentCta) daxAutoconsentListener else daxListener
                    configuration.createCta(activity, listener).apply {
                        showDialogHidingPrevious(this, DAX_DIALOG_DIALOG_TAG)
                    }
                }
            }
        }

        private fun showDaxDialogCta(configuration: DialogCta) {
            hideHomeCta()
            hideDaxCta()
            activity?.let { activity ->
                val listener = if (configuration is DaxAutoconsentCta) daxAutoconsentListener else daxListener
                configuration.createCta(activity, listener).apply {
                    showDialogIfNotExist(this, DAX_DIALOG_DIALOG_TAG)
                }
                viewModel.onCtaShown()
            }
        }

        private val daxAutoconsentListener = object : DaxDialogListener {
            override fun onDaxDialogDismiss() {
                autoconsent.firstPopUpHandled()
                viewModel.onDaxDialogDismissed()
            }

            override fun onDaxDialogHideClick() {
                viewModel.onUserHideDaxDialog()
            }

            override fun onDaxDialogPrimaryCtaClick() {
                webView?.let { autoconsent.setAutoconsentOptOut(it) }
                viewModel.onUserClickCtaOkButton()
            }

            override fun onDaxDialogSecondaryCtaClick() {
                autoconsent.setAutoconsentOptIn()
                viewModel.onUserClickCtaSecondaryButton()
            }
        }

        private val daxListener = object : DaxDialogListener {
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
        }

        private fun showDaxCta(configuration: DaxBubbleCta) {
            hideHomeBackground()
            hideHomeCta()
            configuration.showCta(daxDialogCta.daxCtaContainer)
            newBrowserTab.newTabLayout.setOnClickListener { daxDialogCta.dialogTextCta.finishAnimation() }

            viewModel.onCtaShown()
        }

        private fun showBubbleCta(configuration: BubbleCta) {
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
            favorites: List<QuickAccessFavorite>,
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

        private fun showHomeBackground(
            favorites: List<QuickAccessFavorite>,
            hideLogo: Boolean = false,
        ) {
            if (favorites.isEmpty()) {
                if (hideLogo) homeBackgroundLogo.hideLogo() else homeBackgroundLogo.showLogo()
                quickAccessItems.quickAccessRecyclerView.gone()
            } else {
                homeBackgroundLogo.hideLogo()
                quickAccessAdapter.submitList(favorites)
                quickAccessItems.quickAccessRecyclerView.show()
            }

            newBrowserTab.newTabQuickAccessItemsLayout.show()
        }

        private fun hideHomeBackground() {
            homeBackgroundLogo.hideLogo()
            newBrowserTab.newTabQuickAccessItemsLayout.gone()
        }

        private fun hideDaxCta() {
            daxDialogCta.dialogTextCta.cancelAnimation()
            daxDialogCta.daxCtaContainer.hide()
        }

        private fun hideHomeCta() {
            newBrowserTab.ctaContainer.gone()
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
            binding.webViewFullScreenContainer.show()
            activity?.toggleFullScreen()
            showToast(R.string.fullScreenMessage, Toast.LENGTH_SHORT)
        }

        private fun exitFullScreen() {
            binding.webViewFullScreenContainer.removeAllViews()
            binding.webViewFullScreenContainer.gone()
            activity?.toggleFullScreen()
            binding.focusDummy.requestFocus()
        }

        private fun shouldUpdateOmnibarTextInput(
            viewState: OmnibarViewState,
            omnibarInput: String?,
        ) =
            (!viewState.isEditing || omnibarInput.isNullOrEmpty()) && omnibar.omnibarTextInput.isDifferent(omnibarInput)
    }

    private fun launchPrint(url: String, defaultMediaSize: PrintAttributes.MediaSize) {
        (activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager)?.let { printManager ->
            webView?.createPrintDocumentAdapter(url)?.let { printAdapter ->
                printManager.print(
                    url,
                    printAdapter,
                    PrintAttributes.Builder().setMediaSize(defaultMediaSize).build(),
                )
            }
        }
    }

    private fun showSitePermissionsDialog(
        permissionsToRequest: Array<String>,
        request: PermissionRequest,
    ) {
        if (!isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        activity?.let {
            sitePermissionsDialogLauncher.askForSitePermission(it, request.origin.toString(), tabId, permissionsToRequest, request, this)
        }
    }

    private fun grantSitePermissionRequest(
        sitePermissionsToGrant: Array<String>,
        request: PermissionRequest,
    ) {
        request.grant(sitePermissionsToGrant)
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

    override fun permissionsGrantedOnWhereby() {
        val roomParameters = "?skipMediaPermissionPrompt"
        webView?.loadUrl("${webView?.url.orEmpty()}$roomParameters")
    }

    override fun systemAutofillPerformed() {
        systemAutofillUsageMonitor.onSystemAutofillUsed()
    }
}
