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
import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.*
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.*
import android.view.View.*
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.EditorInfo
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebView.FindListener
import android.webkit.WebView.HitTestResult
import android.webkit.WebView.HitTestResult.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.text.toSpannable
import androidx.core.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.transaction
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.brokensite.BrokenSiteActivity
import com.duckduckgo.app.browser.BrowserTabViewModel.FileChooserRequestedParams
import com.duckduckgo.app.browser.BrowserTabViewModel.LocationPermission
import com.duckduckgo.app.browser.R.string
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.WebViewErrorResponse.LOADING
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.applinks.AppLinksLauncher
import com.duckduckgo.app.browser.applinks.AppLinksSnackBarConfigurator
import com.duckduckgo.app.browser.autocomplete.BrowserAutoCompleteSuggestionsAdapter
import com.duckduckgo.app.browser.commands.Command
import com.duckduckgo.app.browser.commands.Command.ShowBackNavigationHistory
import com.duckduckgo.app.browser.commands.NavigationCommand
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.customtabs.CustomTabActivity
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.browser.customtabs.CustomTabViewModel.Companion.CUSTOM_TAB_NAME_PREFIX
import com.duckduckgo.app.browser.databinding.ContentSiteLocationPermissionDialogBinding
import com.duckduckgo.app.browser.databinding.ContentSystemLocationPermissionDialogBinding
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.HttpAuthenticationBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBinding
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.app.browser.downloader.BlobConverterInjector
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.filechooser.FileChooserIntentBuilder
import com.duckduckgo.app.browser.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher
import com.duckduckgo.app.browser.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.CouldNotCapturePermissionDenied
import com.duckduckgo.app.browser.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.ErrorAccessingMediaApp
import com.duckduckgo.app.browser.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.MediaCaptured
import com.duckduckgo.app.browser.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult.NoMediaCaptured
import com.duckduckgo.app.browser.history.NavigationHistorySheet
import com.duckduckgo.app.browser.history.NavigationHistorySheet.NavigationHistorySheetListener
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.menu.BrowserPopupMenu
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.newtab.FocusedViewProvider
import com.duckduckgo.app.browser.newtab.NewTabPageProvider
import com.duckduckgo.app.browser.omnibar.OmnibarScrolling
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.animations.TrackersAnimatorListener
import com.duckduckgo.app.browser.print.PrintDocumentAdapterFactory
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.browser.print.SinglePrintSafeguardFeature
import com.duckduckgo.app.browser.remotemessage.SharePromoLinkRMFBroadCastReceiver
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.shortcut.ShortcutBuilder
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewGenerator
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.ui.dialogs.AutomaticFireproofDialogOptions
import com.duckduckgo.app.browser.ui.dialogs.LaunchInExternalAppOptions
import com.duckduckgo.app.browser.urlextraction.DOMUrlExtractor
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebView
import com.duckduckgo.app.browser.urlextraction.UrlExtractingWebViewClient
import com.duckduckgo.app.browser.viewstate.AccessibilityViewState
import com.duckduckgo.app.browser.viewstate.AutoCompleteViewState
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.CtaViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.GlobalLayoutViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.browser.viewstate.PrivacyShieldViewState
import com.duckduckgo.app.browser.viewstate.SavedSiteChangedViewState
import com.duckduckgo.app.browser.webshare.WebShareChooser
import com.duckduckgo.app.browser.webview.WebContentDebugging
import com.duckduckgo.app.browser.webview.WebViewBlobDownloadFeature
import com.duckduckgo.app.browser.webview.safewebview.SafeWebViewFeature
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.website
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.global.view.NonDismissibleBehavior
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.global.view.isFullScreen
import com.duckduckgo.app.global.view.isImmersiveModeEnabled
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.global.view.toggleFullScreen
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privatesearch.PrivateSearchScreenNoParams
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FIRE_BUTTON_STATE
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.ui.GridViewColumnCalculator
import com.duckduckgo.app.tabs.ui.TabSwitcherActivity
import com.duckduckgo.app.widget.AddWidgetLauncher
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreenDirectlyViewCredentialsParams
import com.duckduckgo.autofill.api.AutofillScreens.AutofillSettingsScreenShowSuggestionsForSiteParams
import com.duckduckgo.autofill.api.AutofillSettingsLaunchSource
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
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.ExactMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UrlOnlyMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMatch
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.UsernameMissing
import com.duckduckgo.autofill.api.UseGeneratedPasswordDialog
import com.duckduckgo.autofill.api.credential.saving.DuckAddressLoginCreator
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.emailprotection.EmailInjector
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.store.BrowserAppTheme
import com.duckduckgo.common.ui.view.DaxDialog
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.dialog.PromoBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.makeSnackbarWithNoBottomInset
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.playstore.PlayStoreUtils
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_DELAY
import com.duckduckgo.downloads.api.DOWNLOAD_SNACKBAR_LENGTH
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadConfirmation
import com.duckduckgo.downloads.api.DownloadConfirmationDialogListener
import com.duckduckgo.downloads.api.DownloadsFileActions
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.duckplayer.api.DuckPlayerSettingsNoParams
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import com.duckduckgo.mobile.android.app.tracking.ui.AppTrackingProtectionScreens.AppTrackerOnboardingActivityWithEmptyParamsParams
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.privacy.dashboard.api.ui.PrivacyDashboardHybridScreen
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopup
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupFactory
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.bookmarks.BookmarksBottomSheetDialog
import com.duckduckgo.savedsites.impl.bookmarks.FaviconPromptSheet
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.site.permissions.api.SitePermissionsDialogLauncher
import com.duckduckgo.site.permissions.api.SitePermissionsGrantedListener
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissions
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.user.agent.api.ClientBrandHintProvider
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
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
    EmailProtectionUserPromptListener {

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
    private val customTabToolbarColor get() = requireArguments().getInt(CUSTOM_TAB_TOOLBAR_COLOR_ARG)
    private val tabDisplayedInCustomTabScreen get() = requireArguments().getBoolean(TAB_DISPLAYED_IN_CUSTOM_TAB_SCREEN_ARG)

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
    lateinit var autofillCapabilityChecker: AutofillCapabilityChecker

    @Inject
    lateinit var sitePermissionsDialogLauncher: SitePermissionsDialogLauncher

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    @Named("ContentScopeScripts")
    lateinit var contentScopeScripts: JsMessaging

    @Inject
    @Named("DuckPlayer")
    lateinit var duckPlayerScripts: JsMessaging

    @Inject
    lateinit var webContentDebugging: WebContentDebugging

    @Inject
    lateinit var externalCameraLauncher: UploadFromExternalMediaAppLauncher

    @Inject
    lateinit var downloadConfirmation: DownloadConfirmation

    @Inject
    lateinit var privacyProtectionsPopupFactory: PrivacyProtectionsPopupFactory

    @Inject
    lateinit var appLinksSnackBarConfigurator: AppLinksSnackBarConfigurator

    @Inject
    lateinit var appLinksLauncher: AppLinksLauncher

    @Inject
    lateinit var clientBrandHintProvider: ClientBrandHintProvider

    @Inject
    lateinit var subscriptions: Subscriptions

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var webViewVersionProvider: WebViewVersionProvider

    @Inject
    lateinit var webViewBlobDownloadFeature: WebViewBlobDownloadFeature

    @Inject
    lateinit var newTabPageProvider: NewTabPageProvider

    @Inject
    lateinit var focusedViewProvider: FocusedViewProvider

    @Inject
    lateinit var singlePrintSafeguardFeature: SinglePrintSafeguardFeature

    @Inject
    lateinit var safeWebViewFeature: SafeWebViewFeature

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

    private lateinit var popupMenu: BrowserPopupMenu
    private lateinit var ctaBottomSheet: PromoBottomSheetDialog

    private lateinit var autoCompleteSuggestionsAdapter: BrowserAutoCompleteSuggestionsAdapter

    // Used to represent a file to download, but may first require permission
    private var pendingFileDownload: PendingFileDownload? = null

    private var pendingUploadTask: ValueCallback<Array<Uri>>? = null

    private lateinit var renderer: BrowserTabFragmentRenderer

    private lateinit var decorator: BrowserTabFragmentDecorator

    @Inject
    lateinit var autofillFragmentResultListeners: PluginPoint<AutofillFragmentResultsPlugin>

    private var isActiveTab: Boolean = false

    private val downloadMessagesJob = ConflatedJob()

    private val viewModel: BrowserTabViewModel by lazy {
        val viewModel = ViewModelProvider(this, viewModelFactory).get(BrowserTabViewModel::class.java)
        viewModel.loadData(tabId, initialUrl, skipHome)
        launchDownloadMessagesJob()
        viewModel
    }

    private val binding: FragmentBrowserTabBinding by viewBinding()

    private lateinit var omnibar: IncludeOmnibarToolbarBinding

    private lateinit var webViewContainer: FrameLayout

    private var bookmarksBottomSheetDialog: BookmarksBottomSheetDialog.Builder? = null

    private val findInPage
        get() = omnibar.findInPage

    private val newBrowserTab
        get() = binding.includeNewBrowserTab

    private val errorView
        get() = binding.includeErrorView

    private val sslErrorView
        get() = binding.sslErrorWarningLayout

    private val daxDialogIntroBubbleCta
        get() = binding.includeNewBrowserTab.includeDaxDialogIntroBubbleCta

    private val daxDialogOnboardingCta
        get() = binding.includeOnboardingDaxDialog

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
        override fun onFirstPopUpHandled() {}

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

    private val ctaViewStateObserver = Observer<CtaViewState> {
        it?.let { renderer.renderCtaViewState(it) }
    }

    private var alertDialog: DaxAlertDialog? = null

    private var appLinksSnackBar: Snackbar? = null

    private var loginDetectionDialog: DaxAlertDialog? = null

    private var automaticFireproofDialog: DaxAlertDialog? = null

    private val pulseAnimation: PulseAnimation = PulseAnimation(this)

    private var webShareRequest =
        registerForActivityResult(WebShareChooser()) {
            contentScopeScripts.onResponse(it)
        }

    // Instantiating a private class that contains an implementation detail of BrowserTabFragment but is separated for tidiness
    // see discussion in https://github.com/duckduckgo/Android/pull/4027#discussion_r1433373625
    private val jsOrientationHandler = JsOrientationHandler()

    private lateinit var privacyProtectionsPopup: PrivacyProtectionsPopup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate called for tabId=$tabId")

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
        externalCameraLauncher.registerForResult(this) {
            when (it) {
                is MediaCaptured -> pendingUploadTask?.onReceiveValue(arrayOf(Uri.fromFile(it.file)))
                is CouldNotCapturePermissionDenied -> {
                    pendingUploadTask?.onReceiveValue(null)
                    activity?.let { activity ->
                        externalCameraLauncher.showPermissionRationaleDialog(activity, it.inputAction)
                    }
                }

                is NoMediaCaptured -> pendingUploadTask?.onReceiveValue(null)
                is ErrorAccessingMediaApp -> {
                    pendingUploadTask?.onReceiveValue(null)
                    Snackbar.make(binding.root, it.messageId, BaseTransientBottomBar.LENGTH_SHORT).show()
                }
            }
            pendingUploadTask = null
        }
    }

    private fun resumeWebView() {
        webView?.let {
            if (it.isShown) it.onResume()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        omnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
        webViewContainer = binding.webViewContainer
        configureObservers()
        configurePrivacyShield()
        configureWebView()
        configureSwipeRefresh()
        viewModel.registerWebViewListener(webViewClient, webChromeClient)
        configureOmnibarTextInput()
        configureFindInPage()
        configureAutoComplete()
        configureNewTab()
        initPrivacyProtectionsPopup()

        if (tabDisplayedInCustomTabScreen) {
            configureCustomTab()
        }

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

    private fun configureCustomTab() {
        omnibar.omniBarContainer.hide()
        omnibar.fireIconMenu.hide()
        omnibar.tabsMenu.hide()

        omnibar.toolbar.background = ColorDrawable(customTabToolbarColor)
        omnibar.toolbarContainer.background = ColorDrawable(customTabToolbarColor)

        omnibar.customTabToolbarContainer.customTabToolbar.show()

        omnibar.customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            requireActivity().finish()
        }

        omnibar.customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            val params = PrivacyDashboardHybridScreen.PrivacyDashboardHybridWithTabIdParam(tabId)
            val intent = globalActivityStarter.startIntent(requireContext(), params)
            intent?.let { startActivity(it) }
            pixel.fire(CustomTabPixelNames.CUSTOM_TABS_PRIVACY_DASHBOARD_OPENED)
        }

        omnibar.customTabToolbarContainer.customTabDomain.text = viewModel.url?.extractDomain()
        omnibar.customTabToolbarContainer.customTabDomainOnly.text = viewModel.url?.extractDomain()
        omnibar.customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateBlackOrWhite(customTabToolbarColor)
        omnibar.customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        omnibar.customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        omnibar.customTabToolbarContainer.customTabDomainOnly.setTextColor(foregroundColor)
        omnibar.customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        omnibar.browserMenuImageView.setColorFilter(foregroundColor)

        requireActivity().window.navigationBarColor = customTabToolbarColor
        requireActivity().window.statusBarColor = customTabToolbarColor
    }

    private fun calculateBlackOrWhite(color: Int): Int {
        // Handle the case where we did not receive a color.
        if (color == 0) {
            return if ((context as DuckDuckGoActivity).isDarkThemeEnabled()) Color.WHITE else Color.BLACK
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return Color.BLACK
        }
        val greyValue = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
        return if (greyValue < 186) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun initPrivacyProtectionsPopup() {
        privacyProtectionsPopup = privacyProtectionsPopupFactory.createPopup(
            anchor = omnibar.shieldIcon,
        )
        privacyProtectionsPopup.events
            .onEach(viewModel::onPrivacyProtectionsPopupUiEvent)
            .launchIn(lifecycleScope)
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
        val fragment = fragmentManager?.findFragmentByTag(DOWNLOAD_CONFIRMATION_TAG) as? BottomSheetDialogFragment
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

    private fun isActiveCustomTab(): Boolean {
        return tabId.startsWith(CUSTOM_TAB_NAME_PREFIX) && fragmentIsVisible()
    }

    private fun fragmentIsVisible(): Boolean {
        // using isHidden rather than isVisible, as isVisible will incorrectly return false when windowToken is not yet initialized.
        // changes on isHidden will be received in onHiddenChanged
        return !isHidden
    }

    private fun showHome() {
        viewModel.onHomeShown()
        dismissAppLinkSnackBar()
        errorSnackbar.dismiss()
        newBrowserTab.newTabLayout.show()
        newBrowserTab.newTabContainerLayout.show()
        binding.browserLayout.gone()
        webViewContainer.gone()
        omnibarScrolling.disableOmnibarScrolling(omnibar.toolbarContainer)
        omnibar.appBarLayout.setExpanded(true)
        webView?.onPause()
        webView?.hide()
        errorView.errorLayout.gone()
        sslErrorView.gone()
    }

    private fun showBrowser() {
        newBrowserTab.newTabLayout.gone()
        newBrowserTab.newTabContainerLayout.gone()
        binding.browserLayout.show()
        webViewContainer.show()
        webView?.show()
        webView?.onResume()
        errorView.errorLayout.gone()
        sslErrorView.gone()
    }

    private fun showError(
        errorType: WebViewErrorResponse,
        url: String?,
    ) {
        webViewContainer.gone()
        newBrowserTab.newTabLayout.gone()
        newBrowserTab.newTabContainerLayout.gone()
        sslErrorView.gone()
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

    private fun showSSLWarning(
        handler: SslErrorHandler,
        errorResponse: SslErrorResponse,
    ) {
        webViewContainer.gone()
        newBrowserTab.newTabLayout.gone()
        newBrowserTab.newTabContainerLayout.gone()
        webView?.onPause()
        webView?.hide()
        omnibar.appBarLayout.setExpanded(true)
        omnibar.shieldIcon.isInvisible = true
        omnibar.searchIcon.isInvisible = true
        omnibar.daxIcon.isInvisible = true
        errorView.errorLayout.gone()
        binding.browserLayout.gone()
        sslErrorView.bind(handler, errorResponse) { action ->
            viewModel.onSSLCertificateWarningAction(action, errorResponse.url)
        }
        sslErrorView.show()
    }

    private fun hideSSLWarning() {
        val navList = webView?.safeCopyBackForwardList()
        val currentIndex = navList?.currentIndex ?: 0

        if (currentIndex >= 0) {
            Timber.d("SSLError: hiding warning page and triggering a reload of the previous")
            viewModel.recoverFromSSLWarningPage(true)
            refresh()
        } else {
            Timber.d("SSLError: no previous page to load, showing home")
            viewModel.recoverFromSSLWarningPage(false)
        }
    }

    fun submitQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    private fun navigate(
        url: String,
        headers: Map<String, String>,
    ) {
        clientBrandHintProvider.setOn(webView?.safeSettings, url)
        hideKeyboard()
        renderer.hideFindInPage()
        viewModel.registerDaxBubbleCtaDismissed()
        webView?.loadUrl(url, headers)
    }

    private fun onRefreshRequested() {
        viewModel.onRefreshRequested(triggeredByUser = true)
    }

    override fun onAutofillStateChange() {
        viewModel.onRefreshRequested(triggeredByUser = false)
    }

    override fun onRejectGeneratedPassword(originalUrl: String) {
        rejectGeneratedPassword(originalUrl)
    }

    override fun onAcceptGeneratedPassword(originalUrl: String) {
        acceptGeneratedPassword(originalUrl)
    }

    override fun onUseEmailProtectionPrivateAlias(
        originalUrl: String,
        duckAddress: String,
    ) {
        viewModel.usePrivateDuckAddress(originalUrl, duckAddress)
    }

    override fun onUseEmailProtectionPersonalAddress(
        originalUrl: String,
        duckAddress: String,
    ) {
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

    override fun onShareCredentialsForAutofill(
        originalUrl: String,
        selectedCredentials: LoginCredentials,
    ) {
        injectAutofillCredentials(originalUrl, selectedCredentials)
    }

    fun refresh() {
        webView?.reload()
        viewModel.onWebViewRefreshed()
        viewModel.resetErrors()
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
                if (isActiveCustomTab()) {
                    (activity as CustomTabActivity).openMessageInNewFragmentInCustomTab(it.message, this, customTabToolbarColor)
                } else {
                    browserActivity?.openMessageInNewTab(it.message, it.sourceTabId)
                }
            }

            is Command.OpenInNewBackgroundTab -> {
                openInNewBackgroundTab()
            }

            is Command.LaunchNewTab -> browserActivity?.launchNewTab()
            is Command.ShowSavedSiteAddedConfirmation -> savedSiteAdded(it.savedSiteChangedViewState)
            is Command.ShowEditSavedSiteDialog -> editSavedSite(it.savedSiteChangedViewState)
            is Command.DeleteFavoriteConfirmation -> confirmDeleteSavedSite(
                it.savedSite,
                getString(string.favoriteDeleteConfirmationMessage).toSpannable(),
            ) {
                viewModel.onDeleteFavoriteSnackbarDismissed(it)
            }

            is Command.DeleteSavedSiteConfirmation -> confirmDeleteSavedSite(
                it.savedSite,
                getString(com.duckduckgo.saved.sites.impl.R.string.bookmarkDeleteConfirmationMessage, it.savedSite.title).html(requireContext()),
            ) {
                viewModel.onDeleteSavedSiteSnackbarDismissed(it)
            }

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
                val navList = webView?.safeCopyBackForwardList()
                val currentIndex = navList?.currentIndex ?: 0
                clientBrandHintProvider.setOn(webView?.safeSettings, navList?.getItemAtIndex(currentIndex - 1)?.url.toString())
                viewModel.refreshBrowserError()
                webView?.goBackOrForward(-it.steps)
            }

            is NavigationCommand.NavigateForward -> {
                dismissAppLinkSnackBar()
                val navList = webView?.safeCopyBackForwardList()
                val currentIndex = navList?.currentIndex ?: 0
                clientBrandHintProvider.setOn(webView?.safeSettings, navList?.getItemAtIndex(currentIndex + 1)?.url.toString())
                viewModel.refreshBrowserError()
                webView?.goForward()
            }

            is Command.ResetHistory -> {
                resetWebView()
            }

            is Command.LaunchPrivacyPro -> {
                activity?.let { context ->
                    subscriptions.launchPrivacyPro(context, it.uri)
                }
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
            is Command.ShowFileChooser -> launchFilePicker(it.filePathCallback, it.fileChooserParams)
            is Command.ShowExistingImageOrCameraChooser -> launchImageOrCameraChooser(it.fileChooserParams, it.filePathCallback, it.inputAction)
            is Command.ShowImageCamera -> launchCameraCapture(it.filePathCallback, it.fileChooserParams, MediaStore.ACTION_IMAGE_CAPTURE)
            is Command.ShowVideoCamera -> launchCameraCapture(it.filePathCallback, it.fileChooserParams, MediaStore.ACTION_VIDEO_CAPTURE)
            is Command.ShowSoundRecorder -> launchCameraCapture(it.filePathCallback, it.fileChooserParams, MediaStore.Audio.Media.RECORD_SOUND_ACTION)

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
            is Command.HideWebContent -> webView?.hide()
            is Command.ShowWebContent -> webView?.show()
            is Command.CheckSystemLocationPermission -> checkSystemLocationPermission(it.domain, it.deniedForever)
            is Command.RequestSystemLocationPermission -> requestLocationPermissions()
            is Command.AskDomainPermission -> askSiteLocationPermission(it.locationPermission)
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
            is Command.LaunchAutofillSettings -> launchAutofillManagementScreen(it.privacyProtectionEnabled)
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
            is Command.ShowUserCredentialSavedOrUpdatedConfirmation -> showAuthenticationSavedOrUpdatedSnackbar(
                loginCredentials = it.credentials,
                messageResourceId = it.messageResourceId,
                includeShortcutToViewCredential = it.includeShortcutToViewCredential,
            )

            is Command.WebViewError -> showError(it.errorType, it.url)
            is Command.SendResponseToJs -> contentScopeScripts.onResponse(it.data)
            is Command.SendResponseToDuckPlayer -> duckPlayerScripts.onResponse(it.data)
            is Command.WebShareRequest -> webShareRequest.launch(it.data)
            is Command.ScreenLock -> screenLock(it.data)
            is Command.ScreenUnlock -> screenUnlock()
            is Command.ShowFaviconsPrompt -> showFaviconsPrompt()
            is Command.ShowWebPageTitle -> showWebPageTitleInCustomTab(it.title, it.url)
            is Command.ShowSSLError -> showSSLWarning(it.handler, it.error)
            is Command.HideSSLError -> hideSSLWarning()
            is Command.LaunchScreen -> launchScreen(it.screen, it.payload)
            is Command.HideOnboardingDaxDialog -> hideOnboardingDaxDialog(it.onboardingCta)
            is Command.OpenDuckPlayerSettings -> globalActivityStarter.start(binding.root.context, DuckPlayerSettingsNoParams)
            else -> {
                // NO OP
            }
        }
    }

    private fun launchScreen(
        screen: String,
        payload: String,
    ) {
        context?.let {
            globalActivityStarter.start(it, DeeplinkActivityParams(screenName = screen, jsonArguments = payload), null)
        }
    }

    private fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
    ) {
        if (isActiveCustomTab()) {
            omnibar.customTabToolbarContainer.customTabTitle.text = title

            val redirectedDomain = url?.extractDomain()
            redirectedDomain?.let {
                omnibar.customTabToolbarContainer.customTabDomain.text = redirectedDomain
            }

            omnibar.customTabToolbarContainer.customTabTitle.show()
            omnibar.customTabToolbarContainer.customTabDomainOnly.hide()
            omnibar.customTabToolbarContainer.customTabDomain.show()
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

    private fun askSiteLocationPermission(locationPermission: LocationPermission) {
        if (!isActiveCustomTab() && !isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        val binding = ContentSiteLocationPermissionDialogBinding.inflate(layoutInflater)

        val domain = locationPermission.origin
        val title = domain.websiteFromGeoLocationsApiOrigin()
        binding.sitePermissionDialogTitle.text = getString(R.string.preciseLocationSiteDialogTitle, title)
        binding.sitePermissionDialogSubtitle.text = if (title == DDG_DOMAIN) {
            getString(R.string.preciseLocationDDGDialogSubtitle)
        } else {
            getString(R.string.preciseLocationSiteDialogSubtitle)
        }

        val dialog = MaterialAlertDialogBuilder(requireActivity())
            .setView(binding.root)
            .setOnCancelListener {
                // Called when user clicks outside the dialog - deny to be safe
                locationPermission.callback.invoke(locationPermission.origin, false, false)
            }
            .create()

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
        appLinksSnackBar = appLinksSnackBarConfigurator.configureAppLinkSnackBar(view = view, appLink = appLink, viewModel = viewModel)
        appLinksSnackBar?.show()
    }

    private fun openAppLink(appLink: SpecialUrlDetector.UrlType.AppLink) {
        appLinksLauncher.openAppLink(context = context, appLink = appLink, viewModel = viewModel)
    }

    private fun dismissAppLinkSnackBar() {
        appLinksSnackBar?.dismiss()
        appLinksSnackBar = null
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
                        launchDialogForIntent(it, pm, fallbackIntent, fallbackActivities, useFirstActivityFound, viewModel.linkOpenedInNewTab())
                    }

                    fallbackUrl != null -> {
                        webView?.let { webView ->
                            if (viewModel.linkOpenedInNewTab()) {
                                webView.post {
                                    webView.loadUrl(fallbackUrl, headers)
                                }
                            } else {
                                webView.loadUrl(fallbackUrl, headers)
                            }
                        }
                    }

                    else -> {
                        showToast(R.string.unableToOpenLink)
                    }
                }
            } else {
                launchDialogForIntent(it, pm, intent, activities, useFirstActivityFound, viewModel.linkOpenedInNewTab())
            }
        }
    }

    private fun launchDialogForIntent(
        context: Context,
        pm: PackageManager?,
        intent: Intent,
        activities: List<ResolveInfo>,
        useFirstActivityFound: Boolean,
        isOpenedInNewTab: Boolean,
    ) {
        if (!isActiveCustomTab() && !isActiveTab && !isOpenedInNewTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        runCatching {
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
        }.onFailure { exception ->
            Timber.e(exception, "Failed to launch external app")
            showToast(R.string.unableToOpenLink)
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

    private fun showToast(
        @StringRes messageId: Int,
        length: Int = Toast.LENGTH_LONG,
    ) {
        Toast.makeText(context?.applicationContext, messageId, length).show()
    }

    private fun showAuthenticationDialog(request: BasicAuthenticationRequest) {
        if (!isActiveCustomTab() && !isActiveTab) {
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
                viewModel.userSelectedAutocomplete(it)
            },
            editableSearchClickListener = {
                viewModel.onUserSelectedToEditQuery(it.phrase)
            },
            autoCompleteInAppMessageDismissedListener = {
                viewModel.onUserDismissedAutoCompleteInAppMessage()
            },
            autoCompleteOpenSettingsClickListener = {
                viewModel.onUserDismissedAutoCompleteInAppMessage()
                globalActivityStarter.start(context, PrivateSearchScreenNoParams)
            },
        )
        binding.autoCompleteSuggestionsList.adapter = autoCompleteSuggestionsAdapter
    }

    private fun configureNewTab() {
        newBrowserTab.newTabLayout.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (omnibar.omniBarContainer.isPressed) {
                omnibar.omnibarTextInput.hideKeyboard()
                binding.focusDummy.requestFocus()
                omnibar.omniBarContainer.isPressed = false
            }
        }
    }

    private fun configurePrivacyShield() {
        omnibar.shieldIcon.setOnClickListener {
            browserActivity?.launchPrivacyDashboard()
            viewModel.onPrivacyShieldSelected()
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
                    omnibar.omniBarContainer.isPressed = true
                } else {
                    omnibar.omnibarTextInput.hideKeyboard()
                    binding.focusDummy.requestFocus()
                    omnibar.omniBarContainer.isPressed = false
                }
            }

        omnibar.omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                viewModel.sendPixelsOnBackKeyPressed()
                omnibar.omnibarTextInput.hideKeyboard()
                binding.focusDummy.requestFocus()
                omnibar.omniBarContainer.isPressed = false
                //  Allow the event to be handled by the next receiver.
                return false
            }
        }

        omnibar.omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    viewModel.sendPixelsOnEnterKeyPressed()
                    userEnteredQuery(omnibar.omnibarTextInput.text.toString())
                    return@OnEditorActionListener true
                }
                false
            },
        )

        omnibar.omnibarTextInput.setOnTouchListener { _, event ->
            viewModel.onUserTouchedOmnibarTextInput(event.action)
            false
        }

        omnibar.clearTextButton.setOnClickListener {
            viewModel.onClearOmnibarTextInput()
            omnibar.omnibarTextInput.setText("")
        }
    }

    private fun userEnteredQuery(query: String) {
        viewModel.onUserSubmittedQuery(query)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        binding.daxDialogOnboardingCtaContent.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        webView = layoutInflater.inflate(
            R.layout.include_duckduckgo_browser_webview,
            binding.webViewContainer,
            true,
        ).findViewById(R.id.browserWebView) as DuckDuckGoWebView

        webView?.let {
            it.isSafeWebViewEnabled = safeWebViewFeature.self().isEnabled()
            it.webViewClient = webViewClient
            it.webChromeClient = webChromeClient
            it.clearSslPreferences()

            it.settings.apply {
                clientBrandHintProvider.setDefault(this)
                webViewClient.clientProvider = clientBrandHintProvider
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
                if (accessibilitySettingsDataStore.overrideSystemFontSize) {
                    textZoom = accessibilitySettingsDataStore.fontSize.toInt()
                }
                setAlgorithmicDarkeningAllowed(this)
            }

            it.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                lifecycleScope.launch(dispatchers.main()) {
                    viewModel.requestFileDownload(url, contentDisposition, mimeType, true, isBlobDownloadWebViewFeatureEnabled(it))
                }
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
            emailInjector.addJsInterface(
                it,
                onSignedInEmailProtectionPromptShown = { viewModel.showEmailProtectionChooseEmailPrompt() },
                onInContextEmailProtectionSignupPromptShown = { showNativeInContextEmailProtectionSignupPrompt() },
            )
            configureWebViewForBlobDownload(it)
            configureWebViewForAutofill(it)
            printInjector.addJsInterface(it) { viewModel.printFromWebView() }
            autoconsent.addJsInterface(it, autoconsentCallback)
            contentScopeScripts.register(
                it,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        viewModel.processJsCallbackMessage(featureName, method, id, data)
                    }
                },
            )
            duckPlayerScripts.register(
                it,
                object : JsMessageCallback() {
                    override fun process(
                        featureName: String,
                        method: String,
                        id: String?,
                        data: JSONObject?,
                    ) {
                        viewModel.processJsCallbackMessage(featureName, method, id, data)
                    }
                },
            )
        }

        WebView.setWebContentsDebuggingEnabled(webContentDebugging.isEnabled())
    }

    private fun screenLock(data: JsCallbackData) {
        val returnData = jsOrientationHandler.updateOrientation(data, this)
        contentScopeScripts.onResponse(returnData)
    }

    private fun screenUnlock() {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun showFaviconsPrompt() {
        val faviconPrompt = FaviconPromptSheet.Builder(requireContext())
            .addEventListener(
                object : FaviconPromptSheet.EventListener() {
                    override fun onFaviconsFetchingPromptDismissed(fetchingEnabled: Boolean) {
                        viewModel.onFaviconsFetchingEnabled(fetchingEnabled)
                    }
                },
            )
        faviconPrompt.show()
    }

    private fun hideOnboardingDaxDialog(experimentCta: OnboardingDaxDialogCta) {
        experimentCta.hideOnboardingCta(binding)
    }

    private fun configureWebViewForBlobDownload(webView: DuckDuckGoWebView) {
        lifecycleScope.launch(dispatchers.main()) {
            if (isBlobDownloadWebViewFeatureEnabled(webView)) {
                val script = blobDownloadScript()
                WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))

                webView.safeAddWebMessageListener(
                    dispatchers,
                    webViewVersionProvider,
                    "ddgBlobDownloadObj",
                    setOf("*"),
                    object : WebViewCompat.WebMessageListener {
                        override fun onPostMessage(
                            view: WebView,
                            message: WebMessageCompat,
                            sourceOrigin: Uri,
                            isMainFrame: Boolean,
                            replyProxy: JavaScriptReplyProxy,
                        ) {
                            if (message.data?.startsWith("data:") == true) {
                                requestFileDownload(message.data!!, null, "", true)
                                return
                            }
                            viewModel.saveReplyProxyForBlobDownload(sourceOrigin.toString(), replyProxy)
                        }
                    },
                )
            } else {
                blobConverterInjector.addJsInterface(webView) { url, mimeType ->
                    viewModel.requestFileDownload(
                        url = url,
                        contentDisposition = null,
                        mimeType = mimeType,
                        requestUserConfirmation = true,
                        isBlobDownloadWebViewFeatureEnabled = false,
                    )
                }
            }
        }
    }

    private fun blobDownloadScript(): String {
        val script = """
            window.__url_to_blob_collection = {};
        
            const original_createObjectURL = URL.createObjectURL;
        
            URL.createObjectURL = function () {
                const blob = arguments[0];
                const url = original_createObjectURL.call(this, ...arguments);
                if (blob instanceof Blob) {
                    __url_to_blob_collection[url] = blob;
                }
                return url;
            }
            
            function blobToBase64DataUrl(blob) {
                return new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onloadend = function() {
                        resolve(reader.result);
                    }
                    reader.onerror = function() {
                        reject(new Error('Failed to read Blob object'));
                    }
                    reader.readAsDataURL(blob);
                });
            }
        
            ddgBlobDownloadObj.postMessage('Ping')
                    
            ddgBlobDownloadObj.onmessage = function(event) {
                if (event.data.startsWith('blob:')) {
                    const blob = window.__url_to_blob_collection[event.data];
                    if (blob) {
                        blobToBase64DataUrl(blob).then((dataUrl) => {
                            ddgBlobDownloadObj.postMessage(dataUrl);
                        });
                    }
                }
            }
        """.trimIndent()

        return script
    }

    private suspend fun isBlobDownloadWebViewFeatureEnabled(webView: DuckDuckGoWebView): Boolean {
        return withContext(dispatchers.io()) { webViewBlobDownloadFeature.self().isEnabled() } &&
            webView.isWebMessageListenerSupported(dispatchers, webViewVersionProvider) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
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
                    context?.let {
                        val screen = AutofillSettingsScreenDirectlyViewCredentialsParams(
                            loginCredentials = loginCredentials,
                            source = AutofillSettingsLaunchSource.BrowserSnackbar,
                        )
                        globalActivityStarter.start(it, screen)
                    }
                }
            }
            snackbar.show()
        }
    }

    private fun launchAutofillManagementScreen(privacyProtectionEnabled: Boolean) {
        val screen = AutofillSettingsScreenShowSuggestionsForSiteParams(
            currentUrl = webView?.url,
            source = AutofillSettingsLaunchSource.BrowserOverflow,
            privacyProtectionEnabled = privacyProtectionEnabled,
        )
        globalActivityStarter.start(requireContext(), screen)
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
            if ((isActiveCustomTab() || isActiveTab) && urlMatch) {
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

    private fun configureSwipeRefresh() {
        val metrics = resources.displayMetrics
        val distanceToTrigger = (DEFAULT_CIRCLE_TARGET_TIMES_1_5 * metrics.density).toInt()
        binding.swipeRefreshContainer.setDistanceToTriggerSync(distanceToTrigger)
        binding.swipeRefreshContainer.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), com.duckduckgo.mobile.android.R.color.cornflowerBlue),
        )

        binding.swipeRefreshContainer.setOnRefreshListener {
            onRefreshRequested()
            pixel.fire(AppPixelName.BROWSER_PULL_TO_REFRESH)
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

    @Suppress("NewApi") // This API and the behaviour described only apply to apps with targetSdkVersion ≥ TIRAMISU.
    private fun setAlgorithmicDarkeningAllowed(settings: WebSettings) {
        // https://developer.android.com/reference/androidx/webkit/WebSettingsCompat#setAlgorithmicDarkeningAllowed(android.webkit.WebSettings,boolean)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, settingsDataStore.experimentalWebsiteDarkMode)
        }
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
        webView?.safeHitTestResult?.let {
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
        runCatching {
            webView?.safeHitTestResult?.let {
                val target = getLongPressTarget(it)
                if (target != null && viewModel.userSelectedItemFromLongPressMenu(target, item)) {
                    return true
                }
            }
        }.onFailure { exception ->
            Timber.e(exception, "Failed to get HitTestResult")
        }
        return super.onContextItemSelected(item)
    }

    private fun savedSiteAdded(savedSiteChangedViewState: SavedSiteChangedViewState) {
        val dismissHandler = Handler(Looper.getMainLooper())
        val dismissRunnable = Runnable {
            bookmarksBottomSheetDialog?.dialog?.dismiss()
        }
        val title = getBookmarksBottomSheetTitle(savedSiteChangedViewState.bookmarkFolder)

        bookmarksBottomSheetDialog = BookmarksBottomSheetDialog.Builder(requireContext())
            .setTitle(title)
            .setPrimaryItem(
                getString(com.duckduckgo.saved.sites.impl.R.string.addToFavorites),
                icon = com.duckduckgo.mobile.android.R.drawable.ic_favorite_24,
            )
            .setSecondaryItem(
                getString(com.duckduckgo.saved.sites.impl.R.string.editBookmark),
                icon = com.duckduckgo.mobile.android.R.drawable.ic_edit_24,
            )
            .addEventListener(
                object : BookmarksBottomSheetDialog.EventListener() {
                    override fun onPrimaryItemClicked() {
                        viewModel.onFavoriteMenuClicked()
                        dismissHandler.removeCallbacks(dismissRunnable)
                    }

                    override fun onSecondaryItemClicked() {
                        if (savedSiteChangedViewState.savedSite is Bookmark) {
                            pixel.fire(AppPixelName.ADD_BOOKMARK_CONFIRM_EDITED)
                            editSavedSite(
                                savedSiteChangedViewState.copy(
                                    savedSite = savedSiteChangedViewState.savedSite.copy(
                                        isFavorite = viewModel.browserViewState.value?.favorite != null,
                                    ),
                                ),
                            )
                            dismissHandler.removeCallbacks(dismissRunnable)
                        }
                    }
                },
            )
        bookmarksBottomSheetDialog?.show()

        dismissHandler.postDelayed(dismissRunnable, BOOKMARKS_BOTTOM_SHEET_DURATION)
    }

    private fun getBookmarksBottomSheetTitle(bookmarkFolder: BookmarkFolder?): SpannableString {
        val folderName = bookmarkFolder?.name ?: ""
        val fullText = getString(com.duckduckgo.saved.sites.impl.R.string.bookmarkAddedInBookmarks, folderName)
        val spannableString = SpannableString(fullText)

        val boldStart = fullText.indexOf(folderName)
        val boldEnd = boldStart + folderName.length
        spannableString.setSpan(StyleSpan(Typeface.BOLD), boldStart, boldEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
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

    private fun confirmDeleteSavedSite(
        savedSite: SavedSite,
        message: Spanned,
        onDeleteSnackbarDismissed: (SavedSite) -> Unit,
    ) {
        binding.rootView.makeSnackbarWithNoBottomInset(
            message,
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.fireproofWebsiteSnackbarAction) {
            viewModel.undoDelete(savedSite)
        }
            .addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(
                        transientBottomBar: Snackbar?,
                        event: Int,
                    ) {
                        if (event != DISMISS_EVENT_ACTION) {
                            onDeleteSnackbarDismissed(savedSite)
                        }
                    }
                },
            )
            .show()
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

    private fun launchSharePageChooser(
        url: String,
        title: String,
    ) {
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

    private fun launchSharePromoRMFPageChooser(
        url: String,
        shareTitle: String,
    ) {
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
            omnibar.omniBarContainer.isPressed = false
        }
    }

    private fun hideKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now hiding")
            omnibar.omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibar.omnibarTextInput?.hideKeyboard() }
            binding.focusDummy.requestFocus()
            omnibar.omniBarContainer.isPressed = false
        }
    }

    private fun showKeyboard() {
        if (!isHidden) {
            Timber.v("Keyboard now showing")
            omnibar.omnibarTextInput.postDelayed(KEYBOARD_DELAY) { omnibar.omnibarTextInput?.showKeyboard() }
            omnibar.omniBarContainer.isPressed = true
        }
    }

    private fun refreshUserAgent(
        url: String?,
        isDesktop: Boolean,
    ) {
        val currentAgent = webView?.safeSettings?.userAgentString
        val newAgent = userAgentProvider.userAgent(url, isDesktop)
        if (newAgent != currentAgent) {
            webView?.safeSettings?.userAgentString = newAgent
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
        if (!isAdded) return
        viewModel.onViewHidden()
        downloadMessagesJob.cancel()
        webView?.onPause()
    }

    private fun onTabVisible() {
        if (!isAdded) return
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

        renderer.renderHomeCta()
        decorator.recreatePopupMenu()
        privacyProtectionsPopup.onConfigurationChanged()
        viewModel.onConfigurationChanged()
    }

    fun onBackPressed(isCustomTab: Boolean = false): Boolean {
        if (!isAdded) return false
        return viewModel.onUserPressedBack(isCustomTab)
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
        if (::popupMenu.isInitialized) popupMenu.dismiss()
        loginDetectionDialog?.dismiss()
        automaticFireproofDialog?.dismiss()
        browserAutofill.removeJsInterface()
        destroyWebView()
        super.onDestroy()
    }

    private fun destroyWebView() {
        if (::webViewContainer.isInitialized) webViewContainer.removeAllViews()
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

        val downloadConfirmationFragment = downloadConfirmation.instance(pendingDownload)
        showDialogHidingPrevious(downloadConfirmationFragment, DOWNLOAD_CONFIRMATION_TAG)
    }

    private fun launchFilePicker(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
    ) {
        pendingUploadTask = filePathCallback
        val canChooseMultipleFiles = fileChooserParams.filePickingMode == FileChooserParams.MODE_OPEN_MULTIPLE
        val intent = fileChooserIntentBuilder.intent(fileChooserParams.acceptMimeTypes.toTypedArray(), canChooseMultipleFiles)
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_FILE)
    }

    private fun launchCameraCapture(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserRequestedParams,
        inputAction: String,
    ) {
        if (Intent(inputAction).resolveActivity(requireContext().packageManager) == null) {
            launchFilePicker(filePathCallback, fileChooserParams)
            return
        }

        pendingUploadTask = filePathCallback
        externalCameraLauncher.launch(inputAction)
    }

    private fun launchImageOrCameraChooser(
        fileChooserParams: FileChooserRequestedParams,
        filePathCallback: ValueCallback<Array<Uri>>,
        inputAction: String,
    ) {
        context?.let {
            val cameraString = getString(R.string.imageCaptureCameraGalleryDisambiguationCameraOption)
            val cameraIcon = com.duckduckgo.mobile.android.R.drawable.ic_camera_24

            val galleryString = getString(R.string.imageCaptureCameraGalleryDisambiguationGalleryOption)
            val galleryIcon = com.duckduckgo.mobile.android.R.drawable.ic_image_24

            ActionBottomSheetDialog.Builder(it)
                .setTitle(getString(R.string.imageCaptureCameraGalleryDisambiguationTitle))
                .setPrimaryItem(galleryString, galleryIcon)
                .setSecondaryItem(cameraString, cameraIcon)
                .addEventListener(
                    object : ActionBottomSheetDialog.EventListener() {
                        override fun onPrimaryItemClicked() {
                            launchFilePicker(filePathCallback, fileChooserParams)
                        }

                        override fun onSecondaryItemClicked() {
                            launchCameraCapture(filePathCallback, fileChooserParams, inputAction)
                        }

                        override fun onBottomSheetDismissed() {
                            filePathCallback.onReceiveValue(null)
                            pendingUploadTask = null
                        }
                    },
                )
                .show()
        }
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

    private fun launchDefaultBrowser() {
        requireActivity().launchDefaultAppActivity()
    }

    private fun launchAppTPOnboardingScreen() {
        globalActivityStarter.start(requireContext(), AppTrackerOnboardingActivityWithEmptyParamsParams)
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
        val navList = webView?.safeCopyBackForwardList()
        val currentIndex = navList?.currentIndex ?: 0

        clientBrandHintProvider.setOn(webView?.safeSettings, navList?.getItemAtIndex(currentIndex + stepsToMove)?.url.toString())
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
        private const val CUSTOM_TAB_TOOLBAR_COLOR_ARG = "CUSTOM_TAB_TOOLBAR_COLOR_ARG"
        private const val TAB_DISPLAYED_IN_CUSTOM_TAB_SCREEN_ARG = "TAB_DISPLAYED_IN_CUSTOM_TAB_SCREEN_ARG"
        private const val TAB_ID_ARG = "TAB_ID_ARG"
        private const val URL_EXTRA_ARG = "URL_EXTRA_ARG"
        private const val SKIP_HOME_ARG = "SKIP_HOME_ARG"
        private const val DDG_DOMAIN = "duckduckgo.com"

        const val ADD_SAVED_SITE_FRAGMENT_TAG = "ADD_SAVED_SITE"
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

        private const val COOKIES_ANIMATION_DELAY = 400L

        private const val BOOKMARKS_BOTTOM_SHEET_DURATION = 3500L

        private const val WEB_MESSAGE_LISTENER_WEBVIEW_VERSION = "126.0.6478.40"

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

        fun newInstanceForCustomTab(
            tabId: String,
            query: String? = null,
            skipHome: Boolean,
            toolbarColor: Int,
        ): BrowserTabFragment {
            val fragment = BrowserTabFragment()
            val args = Bundle()
            args.putString(TAB_ID_ARG, tabId)
            args.putBoolean(SKIP_HOME_ARG, skipHome)
            args.putInt(CUSTOM_TAB_TOOLBAR_COLOR_ARG, toolbarColor)
            args.putBoolean(TAB_DISPLAYED_IN_CUSTOM_TAB_SCREEN_ARG, true)
            query.let {
                args.putString(URL_EXTRA_ARG, query)
            }
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
            tabsButton?.isVisible = viewState.showTabsButton && !tabDisplayedInCustomTabScreen
            fireMenuButton?.isVisible = viewState.fireButton is HighlightableButton.Visible && !tabDisplayedInCustomTabScreen
            menuButton?.isVisible = viewState.showMenuButton is HighlightableButton.Visible

            val targetView = if (viewState.showMenuButton.isHighlighted()) {
                omnibar.browserMenuImageView
            } else if (viewState.fireButton.isHighlighted()) {
                omnibar.fireIconImageView
            } else if (viewState.showPrivacyShield.isHighlighted()) {
                omnibar.placeholder
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
                viewModel.onFireMenuSelected()
            }

            tabsButton?.show()
        }

        private fun createPopupMenu() {
            popupMenu = BrowserPopupMenu(
                context = requireContext(),
                layoutInflater = layoutInflater,
                displayedInCustomTabScreen = tabDisplayedInCustomTabScreen,
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
                    viewModel.onRefreshRequested(triggeredByUser = true)
                    if (isActiveCustomTab()) {
                        pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_REFRESH)
                    } else {
                        pixel.fire(AppPixelName.MENU_ACTION_REFRESH_PRESSED.pixelName)
                    }
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
                onMenuItemClicked(menuBinding.findInPageMenuItem) {
                    pixel.fire(AppPixelName.MENU_ACTION_FIND_IN_PAGE_PRESSED)
                    viewModel.onFindInPageSelected()
                }
                onMenuItemClicked(menuBinding.privacyProtectionMenuItem) { viewModel.onPrivacyProtectionMenuClicked(isActiveCustomTab()) }
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

                onMenuItemClicked(menuBinding.openInDdgBrowserMenuItem) {
                    viewModel.url?.let {
                        launchCustomTabUrlInDdg(it)
                        pixel.fire(CustomTabPixelNames.CUSTOM_TABS_OPEN_IN_DDG)
                    }
                }
            }
            omnibar.browserMenu.setOnClickListener {
                viewModel.onBrowserMenuClicked()
                hideKeyboardImmediately()
                launchTopAnchoredPopupMenu()
            }
        }

        private fun launchCustomTabUrlInDdg(url: String) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
            }
            startActivity(intent)
        }

        private fun launchTopAnchoredPopupMenu() {
            popupMenu.show(binding.rootView, omnibar.toolbar)
            if (isActiveCustomTab()) {
                pixel.fire(CustomTabPixelNames.CUSTOM_TABS_MENU_OPENED)
            } else {
                pixel.fire(AppPixelName.MENU_ACTION_POPUP_OPENED.pixelName)
            }
        }

        private fun configureShowTabSwitcherListener() {
            tabsButton?.setOnClickListener {
                launch { viewModel.userLaunchingTabSwitcher() }
            }
        }

        private fun configureLongClickOpensNewTabListener() {
            tabsButton?.setOnLongClickListener {
                launch { viewModel.userRequestedOpeningNewTab(longPress = true) }
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
                    val animationViewHolder = if (isActiveCustomTab()) omnibar.customTabToolbarContainer.customTabShieldIcon else omnibar.shieldIcon
                    privacyShieldView.setAnimationView(animationViewHolder, viewState.privacyShield)
                    cancelTrackersAnimation()
                }
            }
        }

        fun renderAutocomplete(viewState: AutoCompleteViewState) {
            renderIfChanged(viewState, lastSeenAutoCompleteViewState) {
                lastSeenAutoCompleteViewState = viewState

                // viewState.showFavourites needs to be moved to FocusedViewModel
                if (viewState.showSuggestions || viewState.showFavorites) {
                    if (viewState.favorites.isNotEmpty() && viewState.showFavorites) {
                        showFocusedView()
                        viewModel.autoCompleteSuggestionsGone()
                        binding.autoCompleteSuggestionsList.gone()
                    } else {
                        binding.autoCompleteSuggestionsList.show()
                        autoCompleteSuggestionsAdapter.updateData(viewState.searchResults.query, viewState.searchResults.suggestions)
                        hideFocusedView()
                    }
                } else {
                    viewModel.autoCompleteSuggestionsGone()
                    binding.autoCompleteSuggestionsList.gone()
                    hideFocusedView()
                }
            }
        }

        private fun showFocusedView() {
            binding.focusedViewContainerLayout.show()
            configureFocusedView()
        }

        private fun configureFocusedView() {
            if (binding.focusedViewContainerLayout.childCount == 0) {
                focusedViewProvider.provideFocusedViewVersion().onEach { focusedView ->
                    binding.focusedViewContainerLayout.addView(
                        focusedView.getView(requireContext()),
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                        ),
                    )
                }.launchIn(lifecycleScope)
            }
        }

        private fun hideFocusedView() {
            binding.focusedViewContainerLayout.gone()
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
                    if (lastSeenBrowserViewState?.browserError == LOADING) {
                        showBrowser()
                        viewModel.resetBrowserError()
                    }
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
                val privacyProtectionsPopupVisible = lastSeenBrowserViewState
                    ?.privacyProtectionsPopupViewState is PrivacyProtectionsPopupViewState.Visible
                if (lastSeenOmnibarViewState?.isEditing != true && !privacyProtectionsPopupVisible) {
                    val site = viewModel.siteLiveData.value
                    val events = site?.orderedTrackerBlockedEntities()
                    activity?.let { activity ->
                        animatorHelper.startTrackersAnimation(
                            context = activity,
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
                val sslErrorChanged = viewState.sslError != lastSeenBrowserViewState?.sslError

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
                } else if (sslErrorChanged) {
                    if (viewState.sslError == NONE) {
                        if (browserShowing) {
                            showBrowser()
                        } else {
                            showHome()
                        }
                    }
                }

                renderToolbarMenus(viewState)
                popupMenu.renderState(browserShowing, viewState, tabDisplayedInCustomTabScreen)
                renderFullscreenMode(viewState)
                renderVoiceSearch(viewState)
                omnibar.spacer.isVisible = viewState.showVoiceSearch && lastSeenBrowserViewState?.showClearButton ?: false
                privacyProtectionsPopup.setViewState(viewState.privacyProtectionsPopupViewState)

                bookmarksBottomSheetDialog?.dialog?.toggleSwitch(viewState.favorite != null)
                val bookmark = viewModel.browserViewState.value?.bookmark?.copy(isFavorite = viewState.favorite != null)
                viewModel.browserViewState.value = viewModel.browserViewState.value?.copy(bookmark = bookmark)
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
                omnibar.duckPlayerIcon.isVisible = viewState.showDuckPlayerIcon
                omnibar.shieldIcon?.isInvisible = !viewState.showPrivacyShield.isEnabled() || viewState.showDaxIcon || viewState.showDuckPlayerIcon
                omnibar.clearTextButton?.isVisible = viewState.showClearButton
                omnibar.searchIcon?.isVisible = viewState.showSearchIcon
            } else {
                omnibar.daxIcon.isVisible = false
                omnibar.duckPlayerIcon.isVisible = false
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
            if (isHidden || isActiveCustomTab()) {
                return
            }

            renderIfChanged(viewState, lastSeenCtaViewState) {
                lastSeenCtaViewState = viewState
                when {
                    viewState.cta != null -> {
                        hideNewTab()
                        showCta(viewState.cta)
                    }

                    viewState.isBrowserShowing -> {
                        hideNewTab()
                    }

                    viewState.daxOnboardingComplete -> {
                        showNewTab()
                    }
                }
            }
        }

        private fun showCta(configuration: Cta) {
            when (configuration) {
                is HomePanelCta -> showHomeCta(configuration)
                is DaxBubbleCta -> showDaxOnboardingBubbleCta(configuration)
                is OnboardingDaxDialogCta -> showOnboardingDialogCta(configuration)
            }
        }

        private fun showDaxOnboardingBubbleCta(configuration: DaxBubbleCta) {
            hideNewTab()
            configuration.apply {
                showCta(daxDialogIntroBubbleCta.daxCtaContainer) {
                    setOnOptionClicked { userEnteredQuery(it.link) }
                }
            }
            newBrowserTab.newTabLayout.setOnClickListener { daxDialogIntroBubbleCta.dialogTextCta.finishAnimation() }

            if (appTheme.isLightModeEnabled()) {
                newBrowserTab.browserBackground.setBackgroundResource(R.drawable.onboarding_experiment_background_bitmap_light)
            } else {
                newBrowserTab.browserBackground.setBackgroundResource(R.drawable.onboarding_experiment_background_bitmap_dark)
            }

            viewModel.onCtaShown()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun showOnboardingDialogCta(configuration: OnboardingDaxDialogCta) {
            hideNewTab()
            val onTypingAnimationFinished = if (configuration is OnboardingDaxDialogCta.DaxTrackersBlockedCta) {
                { viewModel.onOnboardingDaxTypingAnimationFinished() }
            } else {
                {}
            }
            configuration.showOnboardingCta(binding, { viewModel.onUserClickCtaOkButton(configuration) }, onTypingAnimationFinished)
            if (configuration is OnboardingDaxDialogCta.DaxSiteSuggestionsCta) {
                configuration.setOnOptionClicked(
                    daxDialogOnboardingCta,
                ) {
                    userEnteredQuery(it.link)
                    viewModel.onUserClickCtaOkButton(configuration)
                }
            }
            if (appTheme.isLightModeEnabled()) {
                binding.includeOnboardingDaxDialog.onboardingDaxDialogBackground
                    .setBackgroundResource(R.drawable.onboarding_experiment_background_bitmap_light)
            } else {
                binding.includeOnboardingDaxDialog.onboardingDaxDialogBackground
                    .setBackgroundResource(R.drawable.onboarding_experiment_background_bitmap_dark)
            }
            binding.webViewContainer.setOnClickListener { daxDialogIntroBubbleCta.dialogTextCta.finishAnimation() }
            viewModel.onCtaShown()
        }

        private fun removeNewTabLayoutClickListener() {
            newBrowserTab.newTabLayout.setOnClickListener(null)
        }

        private fun showHomeCta(
            configuration: HomePanelCta,
        ) {
            hideDaxCta()

            if (!::ctaBottomSheet.isInitialized) {
                ctaBottomSheet = PromoBottomSheetDialog.Builder(requireContext())
                    .setIcon(configuration.image)
                    .setTitle(getString(configuration.title))
                    .setContent(getString(configuration.description))
                    .setPrimaryButton(getString(configuration.okButton))
                    .setSecondaryButton(getString(configuration.dismissButton))
                    .addEventListener(
                        object : PromoBottomSheetDialog.EventListener() {
                            override fun onPrimaryButtonClicked() {
                                super.onPrimaryButtonClicked()
                                viewModel.onUserClickCtaOkButton(configuration)
                            }

                            override fun onSecondaryButtonClicked() {
                                super.onSecondaryButtonClicked()
                                viewModel.onUserClickCtaSecondaryButton(configuration)
                            }

                            override fun onBottomSheetDismissed() {
                                super.onBottomSheetDismissed()
                                viewModel.onUserClickCtaSecondaryButton(configuration)
                            }
                        },
                    )
                    .build()
                ctaBottomSheet.show()
            } else {
                if (!ctaBottomSheet.isShowing) {
                    ctaBottomSheet.show()
                }
            }
            viewModel.onCtaShown()
        }

        private fun showNewTab() {
            newTabPageProvider.provideNewTabPageVersion().onEach { newTabPage ->
                if (newBrowserTab.newTabContainerLayout.childCount == 0) {
                    newBrowserTab.newTabContainerLayout.addView(
                        newTabPage.getView(requireContext()),
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            }
                .launchIn(lifecycleScope)
            newBrowserTab.newTabContainerLayout.show()
            newBrowserTab.newTabLayout.show()
            omnibarScrolling.disableOmnibarScrolling(omnibar.toolbarContainer)
            viewModel.onNewTabShown()
        }

        private fun hideNewTab() {
            newBrowserTab.newTabContainerLayout.gone()
        }

        private fun hideDaxCta() {
            daxDialogOnboardingCta.dialogTextCta.cancelAnimation()
            daxDialogOnboardingCta.daxCtaContainer.gone()
        }

        fun renderHomeCta() {
            if (::ctaBottomSheet.isInitialized) {
                if (ctaBottomSheet.isShowing) {
                    // the bottom sheet might be visible but not fully expanded
                    val bottomSheet = ctaBottomSheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    if (bottomSheet != null) {
                        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }
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

    private fun launchPrint(
        url: String,
        defaultMediaSize: PrintAttributes.MediaSize,
    ) {
        if (viewModel.isPrinting()) return

        (activity?.getSystemService(Context.PRINT_SERVICE) as? PrintManager)?.let { printManager ->
            webView?.createSafePrintDocumentAdapter(url)?.let { webViewPrintDocumentAdapter ->

                val printAdapter = if (singlePrintSafeguardFeature.self().isEnabled()) {
                    PrintDocumentAdapterFactory.createPrintDocumentAdapter(
                        webViewPrintDocumentAdapter,
                        onStartCallback = { viewModel.onStartPrint() },
                        onFinishCallback = { viewModel.onFinishPrint() },
                    )
                } else {
                    webViewPrintDocumentAdapter
                }
                printManager.print(
                    url,
                    printAdapter,
                    PrintAttributes.Builder().setMediaSize(defaultMediaSize).build(),
                )
            }
        }
    }

    private fun showSitePermissionsDialog(
        permissionsToRequest: SitePermissions,
        request: PermissionRequest,
    ) {
        if (!isActiveCustomTab() && !isActiveTab) {
            Timber.v("Will not launch a dialog for an inactive tab")
            return
        }

        activity?.let {
            sitePermissionsDialogLauncher.askForSitePermission(it, request.origin.toString(), tabId, permissionsToRequest, request, this)
        }
    }

    override fun continueDownload(pendingFileDownload: PendingFileDownload) {
        Timber.i("Continuing to download %s", pendingFileDownload)
        viewModel.download(pendingFileDownload)
    }

    override fun cancelDownload() {
        viewModel.closeAndReturnToSourceIfBlankTab()
    }

    fun onFireDialogVisibilityChanged(isVisible: Boolean) {
        if (!isAdded) return

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
}

private class JsOrientationHandler {

    /**
     * Updates the activity's orientation based on provided JS data
     *
     * @return response data
     */
    fun updateOrientation(
        data: JsCallbackData,
        browserTabFragment: BrowserTabFragment,
    ): JsCallbackData {
        val activity = browserTabFragment.activity
        val response = if (activity == null) {
            NO_ACTIVITY_ERROR
        } else if (!activity.isFullScreen()) {
            NOT_FULL_SCREEN_ERROR
        } else {
            val requestedOrientation = data.params.optString("orientation")
            val matchedOrientation = JsToNativeScreenOrientationMap.entries.find { it.jsValue == requestedOrientation }

            if (matchedOrientation == null) {
                String.format(TYPE_ERROR, requestedOrientation)
            } else {
                activity.requestedOrientation = matchedOrientation.nativeValue
                EMPTY
            }
        }

        return JsCallbackData(
            JSONObject(response),
            data.featureName,
            data.method,
            data.id,
        )
    }

    private enum class JsToNativeScreenOrientationMap(
        val jsValue: String,
        val nativeValue: Int,
    ) {
        ANY("any", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        NATURAL("natural", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        LANDSCAPE("landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        PORTRAIT("portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        PORTRAIT_PRIMARY("portrait-primary", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        PORTRAIT_SECONDARY("portrait-secondary", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
        LANDSCAPE_PRIMARY("landscape-primary", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        LANDSCAPE_SECONDARY("landscape-secondary", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    }

    companion object {
        const val EMPTY = """{}"""
        const val NOT_FULL_SCREEN_ERROR = """{"failure":{"name":"InvalidStateError","message":
            "The page needs to be fullscreen in order to call screen.orientation.lock()"}}"""
        const val TYPE_ERROR = """{"failure":{"name":"TypeError","message":
            "Failed to execute 'lock' on 'ScreenOrientation': The provided value '%s' is not a valid enum value of type OrientationLockType."}}"""
        const val NO_ACTIVITY_ERROR = """{"failure":{"name":"InvalidStateError","message":"The page is not tied to an activity"}}"""
    }
}
