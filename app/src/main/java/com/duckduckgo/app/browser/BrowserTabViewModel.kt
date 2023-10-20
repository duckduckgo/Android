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
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Message
import android.print.PrintAttributes
import android.util.Patterns
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsSharedPreferences.Companion.FONT_SIZE_DEFAULT
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.DeleteBookmarkListener
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.*
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Browser
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Invalidated
import com.duckduckgo.app.browser.BrowserTabViewModel.HighlightableButton.Visible
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.NonHttpAppLink
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource.ImageFavicon
import com.duckduckgo.app.browser.favicon.FaviconSource.UrlFavicon
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
import com.duckduckgo.app.browser.history.NavigationHistoryAdapter.NavigationHistoryListener
import com.duckduckgo.app.browser.history.NavigationHistoryEntry
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler
import com.duckduckgo.app.browser.logindetection.FireproofDialogsEventHandler.Event
import com.duckduckgo.app.browser.logindetection.LoginDetected
import com.duckduckgo.app.browser.logindetection.NavigationAwareLoginDetector
import com.duckduckgo.app.browser.logindetection.NavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.omnibar.QueryOrigin
import com.duckduckgo.app.browser.remotemessage.RemoteMessagingModel
import com.duckduckgo.app.browser.remotemessage.asBrowserTabCommand
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.urlextraction.UrlExtractionListener
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ALWAYS
import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting.ASK_EVERY_TIME
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.extensions.asLocationPermissionOrigin
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.domainMatchesUrl
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FAVORITE_MENU_ITEM_STATE
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.survey.notification.SurveyNotificationScheduler
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.passwordgeneration.AutomaticSavedLoginsMonitor
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.AutofillFireproofDialogSuppressor
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.downloads.api.DownloadCommand
import com.duckduckgo.downloads.api.DownloadStateListener
import com.duckduckgo.downloads.api.FileDownloader
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.duckduckgo.privacy.config.api.*
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchAvailabilityPixelLogger
import com.jakewharton.rxrelay2.PublishRelay
import dagger.Lazy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

@ContributesViewModel(FragmentScope::class)
class BrowserTabViewModel @Inject constructor(
    private val statisticsUpdater: StatisticsUpdater,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val siteFactory: SiteFactory,
    private val tabRepository: TabRepository,
    private val userAllowListDao: UserAllowListDao,
    private val contentBlocking: ContentBlocking,
    private val networkLeaderboardDao: NetworkLeaderboardDao,
    private val savedSitesRepository: SavedSitesRepository,
    private val fireproofWebsiteRepository: FireproofWebsiteRepository,
    private val locationPermissionsRepository: LocationPermissionsRepository,
    private val geoLocationPermissions: GeoLocationPermissions,
    private val navigationAwareLoginDetector: NavigationAwareLoginDetector,
    private val autoComplete: AutoComplete,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val longPressHandler: LongPressHandler,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val specialUrlDetector: SpecialUrlDetector,
    private val faviconManager: FaviconManager,
    private val addToHomeCapabilityDetector: AddToHomeCapabilityDetector,
    private val remoteMessagingModel: Lazy<RemoteMessagingModel>,
    private val ctaViewModel: CtaViewModel,
    private val searchCountDao: SearchCountDao,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val userEventsStore: UserEventsStore,
    private val fileDownloader: FileDownloader,
    private val gpc: Gpc,
    private val fireproofDialogsEventHandler: FireproofDialogsEventHandler,
    private val emailManager: EmailManager,
    private val accessibilitySettingsDataStore: AccessibilitySettingsDataStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appLinksHandler: AppLinksHandler,
    private val ampLinks: AmpLinks,
    private val trackingParameters: TrackingParameters,
    private val downloadCallback: DownloadStateListener,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val voiceSearchPixelLogger: VoiceSearchAvailabilityPixelLogger,
    private val settingsDataStore: SettingsDataStore,
    private val autofillStore: AutofillStore,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val adClickManager: AdClickManager,
    private val sitePermissionsManager: SitePermissionsManager,
    private val autofillFireproofDialogSuppressor: AutofillFireproofDialogSuppressor,
    private val automaticSavedLoginsMonitor: AutomaticSavedLoginsMonitor,
    private val surveyNotificationScheduler: SurveyNotificationScheduler,
    private val device: DeviceInfo,
) : WebViewClientListener,
    EditSavedSiteListener,
    DeleteBookmarkListener,
    UrlExtractionListener,
    ViewModel(),
    NavigationHistoryListener {

    private var buildingSiteFactoryJob: Job? = null

    sealed class GlobalLayoutViewState {
        data class Browser(val isNewTabState: Boolean = true) : GlobalLayoutViewState()
        object Invalidated : GlobalLayoutViewState()
    }

    data class CtaViewState(
        val cta: Cta? = null,
        val message: RemoteMessage? = null,
        val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite> = emptyList(),
    )

    data class SavedSiteChangedViewState(
        val savedSite: SavedSite,
        val bookmarkFolder: BookmarkFolder?,
    )

    data class BrowserViewState(
        val browserShowing: Boolean = false,
        val isFullScreen: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val canChangeBrowsingMode: Boolean = false,
        val showPrivacyShield: Boolean = false,
        val showSearchIcon: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val fireButton: HighlightableButton = Visible(),
        val showMenuButton: HighlightableButton = Visible(),
        val canSharePage: Boolean = false,
        val canSaveSite: Boolean = false,
        val bookmark: Bookmark? = null,
        val addFavorite: HighlightableButton = Visible(enabled = false),
        val favorite: Favorite? = null,
        val canFireproofSite: Boolean = false,
        val isFireproofWebsite: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val canChangePrivacyProtection: Boolean = false,
        val isPrivacyProtectionEnabled: Boolean = false,
        val canReportSite: Boolean = false,
        val addToHomeEnabled: Boolean = false,
        val addToHomeVisible: Boolean = false,
        val showDaxIcon: Boolean = false,
        val isEmailSignedIn: Boolean = false,
        var previousAppLink: AppLink? = null,
        val canFindInPage: Boolean = false,
        val forceRenderingTicker: Long = System.currentTimeMillis(),
        val canPrintPage: Boolean = false,
        val showAutofill: Boolean = false,
        val browserError: WebViewErrorResponse = OMITTED,
    )

    sealed class HighlightableButton {
        data class Visible(
            val enabled: Boolean = true,
            val highlighted: Boolean = false,
        ) : HighlightableButton()

        object Gone : HighlightableButton()

        fun isHighlighted(): Boolean {
            return when (this) {
                is Visible -> this.highlighted
                is Gone -> false
            }
        }

        fun isEnabled(): Boolean {
            return when (this) {
                is Visible -> this.enabled
                is Gone -> false
            }
        }
    }

    data class OmnibarViewState(
        val omnibarText: String = "",
        val isEditing: Boolean = false,
        val shouldMoveCaretToEnd: Boolean = false,
        val showVoiceSearch: Boolean = false,
        val forceExpand: Boolean = true,
    )

    data class LoadingViewState(
        val isLoading: Boolean = false,
        val privacyOn: Boolean = true,
        val progress: Int = 0,
    )

    data class AccessibilityViewState(
        val fontSize: Float = FONT_SIZE_DEFAULT,
        val forceZoom: Boolean = false,
        val refreshWebView: Boolean = false,
    )

    data class FindInPageViewState(
        val visible: Boolean = false,
        val showNumberMatches: Boolean = false,
        val activeMatchIndex: Int = 0,
        val searchTerm: String = "",
        val numberMatches: Int = 0,
    )

    data class PrivacyShieldViewState(
        val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
    )

    data class AutoCompleteViewState(
        val showSuggestions: Boolean = false,
        val showFavorites: Boolean = false,
        val searchResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
        val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite> = emptyList(),
    )

    data class LocationPermission(
        val origin: String,
        val callback: GeolocationPermissions.Callback,
    )

    sealed class Command {
        class OpenInNewTab(
            val query: String,
            val sourceTabId: String? = null,
        ) : Command()

        class OpenMessageInNewTab(
            val message: Message,
            val sourceTabId: String? = null,
        ) : Command()

        class OpenInNewBackgroundTab(val query: String) : Command()
        object LaunchNewTab : Command()
        object ResetHistory : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
        object ShowKeyboard : Command()
        object HideKeyboard : Command()
        class ShowFullScreen(val view: View) : Command()
        class DownloadImage(
            val url: String,
            val requestUserConfirmation: Boolean,
        ) : Command()

        class ShowSavedSiteAddedConfirmation(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
        class ShowEditSavedSiteDialog(val savedSiteChangedViewState: SavedSiteChangedViewState) : Command()
        class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
        class ShowFireproofWebSiteConfirmation(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Command()
        class DeleteFireproofConfirmation(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Command()
        class ShowPrivacyProtectionEnabledConfirmation(val domain: String) : Command()
        class ShowPrivacyProtectionDisabledConfirmation(val domain: String) : Command()
        object AskToDisableLoginDetection : Command()
        class AskToFireproofWebsite(val fireproofWebsite: FireproofWebsiteEntity) : Command()
        class AskToAutomateFireproofWebsite(val fireproofWebsite: FireproofWebsiteEntity) : Command()
        class ShareLink(val url: String) : Command()
        class SharePromoLinkRMF(val url: String, val shareTitle: String) : Command()
        class PrintLink(val url: String, val mediaSize: PrintAttributes.MediaSize) : Command()
        class CopyLink(val url: String) : Command()
        class FindInPageCommand(val searchTerm: String) : Command()
        class BrokenSiteFeedback(val data: BrokenSiteData) : Command()
        object DismissFindInPage : Command()
        class ShowFileChooser(
            val filePathCallback: ValueCallback<Array<Uri>>,
            val fileChooserParams: WebChromeClient.FileChooserParams,
        ) : Command()

        class HandleNonHttpAppLink(
            val nonHttpAppLink: NonHttpAppLink,
            val headers: Map<String, String>,
        ) : Command()

        class ShowAppLinkPrompt(val appLink: AppLink) : Command()
        class OpenAppLink(val appLink: AppLink) : Command()
        class ExtractUrlFromCloakedAmpLink(val initialUrl: String) : Command()
        class LoadExtractedUrl(val extractedUrl: String) : Command()
        class AddHomeShortcut(
            val title: String,
            val url: String,
            val icon: Bitmap? = null,
        ) : Command()

        class SubmitUrl(val url: String) : Command()
        class LaunchPlayStore(val appPackage: String) : Command()
        class LaunchSurvey(val survey: Survey) : Command()
        object LaunchDefaultBrowser : Command()
        object LaunchAppTPOnboarding : Command()
        object LaunchAddWidget : Command()
        class RequiresAuthentication(val request: BasicAuthenticationRequest) : Command()
        class SaveCredentials(
            val request: BasicAuthenticationRequest,
            val credentials: BasicAuthenticationCredentials,
        ) : Command()

        object GenerateWebViewPreviewImage : Command()
        object LaunchTabSwitcher : Command()
        object HideWebContent : Command()
        object ShowWebContent : Command()
        class CheckSystemLocationPermission(
            val domain: String,
            val deniedForever: Boolean,
        ) : Command()

        class AskDomainPermission(val domain: String) : Command()
        object RequestSystemLocationPermission : Command()
        class RefreshUserAgent(
            val url: String?,
            val isDesktop: Boolean,
        ) : Command()

        class ShowErrorWithAction(
            val textResId: Int,
            val action: () -> Unit,
        ) : Command()

        class ShowDomainHasPermissionMessage(val domain: String) : Command()
        class ConvertBlobToDataUri(
            val url: String,
            val mimeType: String,
        ) : Command()

        class RequestFileDownload(
            val url: String,
            val contentDisposition: String?,
            val mimeType: String,
            val requestUserConfirmation: Boolean,
        ) : Command()

        object ChildTabClosed : Command()

        class CopyAliasToClipboard(val alias: String) : Command()
        class InjectEmailAddress(
            val duckAddress: String,
            val originalUrl: String,
            val autoSaveLogin: Boolean,
        ) : Command()
        class ShowEmailProtectionChooseEmailPrompt(val address: String) : Command()
        object ShowEmailProtectionInContextSignUpPrompt : Command()
        sealed class DaxCommand : Command() {
            object FinishPartialTrackerAnimation : DaxCommand()
            class HideDaxDialog(val cta: Cta) : DaxCommand()
        }

        class CancelIncomingAutofillRequest(val url: String) : Command()
        object LaunchAutofillSettings : Command()
        class EditWithSelectedQuery(val query: String) : Command()
        class ShowBackNavigationHistory(val history: List<NavigationHistoryEntry>) : Command()
        class NavigateToHistory(val historyStackIndex: Int) : Command()
        object EmailSignEvent : Command()
        class ShowSitePermissionsDialog(
            val permissionsToRequest: Array<String>,
            val request: PermissionRequest,
        ) : Command()

        class GrantSitePermissionRequest(
            val sitePermissionsToGrant: Array<String>,
            val request: PermissionRequest,
        ) : Command()

        class ShowUserCredentialSavedOrUpdatedConfirmation(
            val credentials: LoginCredentials,
            val includeShortcutToViewCredential: Boolean,
            val messageResourceId: Int,
        ) : Command()

        data class WebViewError(val errorType: WebViewErrorResponse, val url: String) : Command()
    }

    sealed class NavigationCommand : Command() {
        class NavigateToHistory(val historyStackIndex: Int) : Command()
        object Refresh : NavigationCommand()
        class Navigate(
            val url: String,
            val headers: Map<String, String>,
        ) : NavigationCommand()

        class NavigateBack(val steps: Int) : NavigationCommand()
        object NavigateForward : NavigationCommand()
    }

    val autoCompleteViewState: MutableLiveData<AutoCompleteViewState> = MutableLiveData()
    val browserViewState: MutableLiveData<BrowserViewState> = MutableLiveData()
    val globalLayoutState: MutableLiveData<GlobalLayoutViewState> = MutableLiveData()
    val loadingViewState: MutableLiveData<LoadingViewState> = MutableLiveData()
    val omnibarViewState: MutableLiveData<OmnibarViewState> = MutableLiveData()
    val findInPageViewState: MutableLiveData<FindInPageViewState> = MutableLiveData()
    val accessibilityViewState: MutableLiveData<AccessibilityViewState> = MutableLiveData()
    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    var siteLiveData: MutableLiveData<Site> = MutableLiveData()
    val privacyShieldViewState: MutableLiveData<PrivacyShieldViewState> = MutableLiveData()

    var skipHome = false
    var hasCtaBeenShownForCurrentPage: AtomicBoolean = AtomicBoolean(false)
    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val liveSelectedTab: LiveData<TabEntity> = tabRepository.liveSelectedTab
    val survey: LiveData<Survey> = ctaViewModel.surveyLiveData
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    private var refreshOnViewVisible = MutableStateFlow(true)
    private var ctaChangedTicker = MutableStateFlow("")

    /*
      Used to prevent autofill credential picker from automatically showing
      Useful if user has done something that would result in a strange UX to then show the picker
      This only prevents against automatically showing; if the user taps on an autofill field directly, the dialog can still show
     */
    private var canAutofillSelectCredentialsDialogCanAutomaticallyShow = true

    val url: String?
        get() = site?.url

    val title: String?
        get() = site?.title

    private var showFavoritesOnboarding = false
        set(value) {
            if (value != field) {
                if (value) {
                    browserViewState.observeForever(favoritesOnboardingObserver)
                } else {
                    browserViewState.removeObserver(favoritesOnboardingObserver)
                }
            }
            field = value
        }
    private var locationPermission: LocationPermission? = null
    private val locationPermissionMessages: MutableMap<String, Boolean> = mutableMapOf()
    private val locationPermissionSession: MutableMap<String, LocationPermissionType> = mutableMapOf()

    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private val fireproofWebsiteState: LiveData<List<FireproofWebsiteEntity>> = fireproofWebsiteRepository.getFireproofWebsites()

    @ExperimentalCoroutinesApi
    @FlowPreview
    private val showPulseAnimation: LiveData<Boolean> = ctaViewModel.showFireButtonPulseAnimation.asLiveData(
        context = viewModelScope.coroutineContext,
    )

    private var autoCompleteDisposable: Disposable? = null
    private var site: Site? = null
    private lateinit var tabId: String
    private var webNavigationState: WebNavigationState? = null
    private var httpsUpgraded = false
    private val browserStateModifier = BrowserStateModifier()
    private var faviconPrefetchJob: Job? = null
    private var deferredBlankSite: Job? = null
    private var accessibilityObserver: Job? = null
    private var isProcessingTrackingLink = false
    private var isLinkOpenedInNewTab = false

    private val fireproofWebsitesObserver = Observer<List<FireproofWebsiteEntity>> {
        browserViewState.value = currentBrowserViewState().copy(isFireproofWebsite = isFireproofWebsite())
    }

    private val favoritesOnboardingObserver = Observer<BrowserViewState> { state ->
        val shouldShowAnimation = state.browserShowing
        val menuButton = currentBrowserViewState().showMenuButton
        if (menuButton is HighlightableButton.Visible && menuButton.highlighted != shouldShowAnimation) {
            browserViewState.value = currentBrowserViewState().copy(showMenuButton = HighlightableButton.Visible(highlighted = shouldShowAnimation))
        }
    }

    private val fireproofDialogEventObserver = Observer<Event> { event ->
        command.value = when (event) {
            is Event.AskToDisableLoginDetection -> AskToDisableLoginDetection
            is Event.FireproofWebSiteSuccess -> ShowFireproofWebSiteConfirmation(event.fireproofWebsiteEntity)
        }
    }

    @ExperimentalCoroutinesApi
    private val fireButtonAnimation = Observer<Boolean> { shouldShowAnimation ->
        Timber.i("shouldShowAnimation $shouldShowAnimation")
        if (currentBrowserViewState().fireButton is HighlightableButton.Visible) {
            browserViewState.value = currentBrowserViewState().copy(fireButton = HighlightableButton.Visible(highlighted = shouldShowAnimation))
        }

        if (shouldShowAnimation) {
            registerAndScheduleDismissAction()
        }
    }

    private fun registerAndScheduleDismissAction() {
        viewModelScope.launch(dispatchers.io()) {
            val fireButtonHighlightedEvent = userEventsStore.getUserEvent(UserEventKey.FIRE_BUTTON_HIGHLIGHTED)
            if (fireButtonHighlightedEvent == null) {
                userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_HIGHLIGHTED)
            }
            val pulseElapsedTime = System.currentTimeMillis() - (fireButtonHighlightedEvent?.timestamp ?: System.currentTimeMillis())
            val pulsePendingTime = ONE_HOUR_IN_MS - pulseElapsedTime
            delay(pulsePendingTime)
            ctaViewModel.dismissPulseAnimation()
        }
    }

    private val loginDetectionObserver = Observer<LoginDetected> { loginEvent ->
        Timber.i("LoginDetection for $loginEvent")
        viewModelScope.launch(dispatchers.io()) {
            val canPromptAboutFireproofing = !autofillFireproofDialogSuppressor.isAutofillPreventingFireproofPrompts()

            if (!isFireproofWebsite(loginEvent.forwardedToDomain)) {
                withContext(dispatchers.main()) {
                    val showAutomaticFireproofDialog =
                        settingsDataStore.automaticFireproofSetting == ASK_EVERY_TIME && settingsDataStore.showAutomaticFireproofDialog
                    when {
                        showAutomaticFireproofDialog -> {
                            if (canPromptAboutFireproofing) {
                                command.value = AskToAutomateFireproofWebsite(FireproofWebsiteEntity(loginEvent.forwardedToDomain))
                            }
                        }

                        settingsDataStore.automaticFireproofSetting == ALWAYS ->
                            fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(loginEvent.forwardedToDomain)

                        else -> {
                            if (canPromptAboutFireproofing) {
                                command.value = AskToFireproofWebsite(FireproofWebsiteEntity(loginEvent.forwardedToDomain))
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        initializeViewStates()
        configureAutoComplete()
        logVoiceSearchAvailability()

        fireproofWebsiteState.observeForever(fireproofWebsitesObserver)
        fireproofDialogsEventHandler.event.observeForever(fireproofDialogEventObserver)
        navigationAwareLoginDetector.loginEventLiveData.observeForever(loginDetectionObserver)
        showPulseAnimation.observeForever(fireButtonAnimation)

        tabRepository.childClosedTabs.onEach { closedTab ->
            if (this@BrowserTabViewModel::tabId.isInitialized && tabId == closedTab) {
                command.value = ChildTabClosed
            }
        }.launchIn(viewModelScope)

        emailManager.signedInFlow().onEach { isSignedIn ->
            browserViewState.value = currentBrowserViewState().copy(isEmailSignedIn = isSignedIn)
            command.value = EmailSignEvent
        }.launchIn(viewModelScope)

        observeAccessibilitySettings()

        savedSitesRepository.getFavorites().map { favoriteSites ->
            val favorites = favoriteSites.map { FavoritesQuickAccessAdapter.QuickAccessFavorite(it) }
            ctaViewState.value = currentCtaViewState().copy(favorites = favorites)
            autoCompleteViewState.value = currentAutoCompleteViewState().copy(favorites = favorites)
            val favorite = favoriteSites.firstOrNull { it.url == url }
            browserViewState.value = currentBrowserViewState().copy(favorite = favorite)
        }.launchIn(viewModelScope)

        savedSitesRepository.getBookmarks()
            .flowOn(dispatchers.io())
            .map { bookmarks ->
                val bookmark = bookmarks.firstOrNull { it.url == url }
                browserViewState.value = currentBrowserViewState().copy(bookmark = bookmark)
            }
            .flowOn(dispatchers.main())
            .launchIn(viewModelScope)

        viewModelScope.launch(dispatchers.io()) {
            remoteMessagingModel.get().activeMessages
                .combine(ctaChangedTicker.asStateFlow(), ::Pair)
                .onEach { (activeMessage, ticker) ->
                    Timber.v("RMF: $ticker-$activeMessage")

                    if (ticker.isEmpty()) return@onEach
                    if (currentBrowserViewState().browserShowing) return@onEach

                    val cta = currentCtaViewState().cta?.takeUnless { it ->
                        activeMessage != null && it is HomePanelCta
                    }

                    withContext(dispatchers.main()) {
                        ctaViewState.value = currentCtaViewState().copy(
                            cta = cta,
                            message = if (cta == null) activeMessage else null,
                        )
                    }
                }
                .flowOn(dispatchers.io())
                .launchIn(viewModelScope)
        }
    }

    fun loadData(
        tabId: String,
        initialUrl: String?,
        skipHome: Boolean,
        favoritesOnboarding: Boolean,
    ) {
        Timber.i("favoritesOnboarding loadData $initialUrl, $skipHome, $favoritesOnboarding")
        this.tabId = tabId
        this.skipHome = skipHome
        this.showFavoritesOnboarding = favoritesOnboarding
        siteLiveData = tabRepository.retrieveSiteData(tabId)
        site = siteLiveData.value

        initialUrl?.let { buildSiteFactory(it) }
    }

    fun onViewReady() {
        url?.let {
            onUserSubmittedQuery(it)
        }
    }

    fun onViewRecreated() {
        observeAccessibilitySettings()
    }

    fun observeAccessibilitySettings() {
        accessibilityObserver?.cancel()
        accessibilityObserver = accessibilitySettingsDataStore.settingsFlow()
            .combine(refreshOnViewVisible.asStateFlow(), ::Pair)
            .onEach { (settings, viewVisible) ->
                Timber.v("Accessibility: newSettings $settings, $viewVisible")
                val shouldRefreshWebview =
                    (currentAccessibilityViewState().forceZoom != settings.forceZoom) || currentAccessibilityViewState().refreshWebView
                accessibilityViewState.value =
                    currentAccessibilityViewState().copy(
                        fontSize = settings.fontSize,
                        forceZoom = settings.forceZoom,
                        refreshWebView = shouldRefreshWebview,
                    )
            }.launchIn(viewModelScope)
    }

    fun onMessageProcessed() {
        showBrowser()
    }

    fun downloadCommands(): Flow<DownloadCommand> {
        return downloadCallback.commands()
    }

    private fun buildSiteFactory(
        url: String,
        title: String? = null,
    ) {
        if (buildingSiteFactoryJob?.isCompleted == false) {
            Timber.i("Cancelling existing work to build SiteMonitor for $url")
            buildingSiteFactoryJob?.cancel()
        }

        site = siteFactory.buildSite(url, title, httpsUpgraded)
        onSiteChanged()
        buildingSiteFactoryJob = viewModelScope.launch {
            site?.let {
                withContext(dispatchers.io()) {
                    siteFactory.loadFullSiteDetails(it)
                    onSiteChanged()
                }
            }
        }
    }

    private fun logVoiceSearchAvailability() {
        if (voiceSearchAvailability.isVoiceSearchSupported) voiceSearchPixelLogger.log()
    }

    @SuppressLint("CheckResult")
    private fun configureAutoComplete() {
        autoCompleteDisposable = autoCompletePublishSubject
            .debounce(300, TimeUnit.MILLISECONDS)
            .switchMap { autoComplete.autoComplete(it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    onAutoCompleteResultReceived(result)
                },
                { t: Throwable? -> Timber.w(t, "Failed to get search results") },
            )
    }

    private fun onAutoCompleteResultReceived(result: AutoCompleteResult) {
        val currentViewState = currentAutoCompleteViewState()
        autoCompleteViewState.value = currentViewState.copy(searchResults = AutoCompleteResult(result.query, result.suggestions))
    }

    @VisibleForTesting
    public override fun onCleared() {
        buildingSiteFactoryJob?.cancel()
        autoCompleteDisposable?.dispose()
        autoCompleteDisposable = null
        fireproofWebsiteState.removeObserver(fireproofWebsitesObserver)
        browserViewState.removeObserver(favoritesOnboardingObserver)
        navigationAwareLoginDetector.loginEventLiveData.removeObserver(loginDetectionObserver)
        fireproofDialogsEventHandler.event.removeObserver(fireproofDialogEventObserver)
        showPulseAnimation.removeObserver(fireButtonAnimation)
        super.onCleared()
    }

    fun registerWebViewListener(
        browserWebViewClient: BrowserWebViewClient,
        browserChromeClient: BrowserChromeClient,
    ) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onViewResumed() {
        if (currentGlobalLayoutState() is Invalidated && currentBrowserViewState().browserShowing) {
            showErrorWithAction()
        }
    }

    fun onViewVisible() {
        setAdClickActiveTabData(url)

        // we expect refreshCta to be called when a site is fully loaded if browsingShowing -trackers data available-.
        if (!currentBrowserViewState().browserShowing) {
            viewModelScope.launch {
                val cta = refreshCta()
                showOrHideKeyboard(cta) // we hide the keyboard when showing a DialogCta and HomeCta type in the home screen otherwise we show it
            }
        } else {
            command.value = HideKeyboard
        }

        omnibarViewState.value = currentOmnibarViewState().copy(
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(
                hasFocus = currentOmnibarViewState().isEditing,
                query = currentOmnibarViewState().omnibarText,
                hasQueryChanged = false,
                urlLoaded = url ?: "",
            ),
        )
        viewModelScope.launch {
            refreshOnViewVisible.emit(true)
        }
    }

    fun onViewHidden() {
        skipHome = false
        viewModelScope.launch {
            downloadCallback
            refreshOnViewVisible.emit(false)
        }
    }

    suspend fun fireAutocompletePixel(suggestion: AutoCompleteSuggestion) {
        val currentViewState = currentAutoCompleteViewState()

        val hasBookmarks = withContext(dispatchers.io()) {
            savedSitesRepository.hasBookmarks()
        }
        val hasBookmarkResults = currentViewState.searchResults.suggestions.any { it is AutoCompleteBookmarkSuggestion }
        val params = mapOf(
            PixelParameter.SHOWED_BOOKMARKS to hasBookmarkResults.toString(),
            PixelParameter.BOOKMARK_CAPABLE to hasBookmarks.toString(),
        )
        val pixelName = when (suggestion) {
            is AutoCompleteBookmarkSuggestion -> AppPixelName.AUTOCOMPLETE_BOOKMARK_SELECTION
            is AutoCompleteSearchSuggestion -> AppPixelName.AUTOCOMPLETE_SEARCH_SELECTION
        }

        pixel.fire(pixelName, params)
    }

    fun onUserLongPressedBack() {
        val navigationHistory = webNavigationState?.navigationHistory ?: return

        // we don't want the current page, so drop the first entry. Also don't want too many, so take only most recent ones.
        val stack = navigationHistory
            .drop(1)
            .take(10)

        if (stack.isNotEmpty()) {
            command.value = ShowBackNavigationHistory(stack)
        }
    }

    fun onUserSubmittedQuery(
        query: String,
        queryOrigin: QueryOrigin = QueryOrigin.FromUser,
    ) {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NewQuerySubmitted)

        if (query.isBlank()) {
            return
        }

        if (currentGlobalLayoutState() is Invalidated) {
            recoverTabWithQuery(query)
            return
        }

        command.value = HideKeyboard
        val trimmedInput = query.trim()

        viewModelScope.launch(dispatchers.io()) {
            searchCountDao.incrementSearchCount()
        }

        val verticalParameter = extractVerticalParameter(url)
        var urlToNavigate = queryUrlConverter.convertQueryToUrl(trimmedInput, verticalParameter, queryOrigin)

        when (val type = specialUrlDetector.determineType(trimmedInput)) {
            is NonHttpAppLink -> {
                nonHttpAppLinkClicked(type)
            }

            is SpecialUrlDetector.UrlType.CloakedAmpLink -> {
                handleCloakedAmpLink(type.ampUrl)
            }

            else -> {
                if (type is SpecialUrlDetector.UrlType.ExtractedAmpLink) {
                    Timber.d("AMP link detection: Using extracted URL: ${type.extractedUrl}")
                    urlToNavigate = type.extractedUrl
                } else if (type is SpecialUrlDetector.UrlType.TrackingParameterLink) {
                    Timber.d("Loading parameter cleaned URL: ${type.cleanedUrl}")
                    urlToNavigate = type.cleanedUrl
                }

                if (shouldClearHistoryOnNewQuery()) {
                    command.value = ResetHistory
                }

                fireQueryChangedPixel(trimmedInput)

                if (!appSettingsPreferencesStore.showAppLinksPrompt) {
                    appLinksHandler.updatePreviousUrl(urlToNavigate)
                    appLinksHandler.setUserQueryState(true)
                } else {
                    clearPreviousUrl()
                }

                command.value = NavigationCommand.Navigate(urlToNavigate, getUrlHeaders(urlToNavigate))
            }
        }

        globalLayoutState.value = Browser(isNewTabState = false)
        findInPageViewState.value = FindInPageViewState(visible = false)
        omnibarViewState.value = currentOmnibarViewState().copy(
            omnibarText = trimmedInput,
            shouldMoveCaretToEnd = false,
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(urlLoaded = urlToNavigate),
            forceExpand = true,
        )
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true, showClearButton = false, browserError = OMITTED)
        autoCompleteViewState.value =
            currentAutoCompleteViewState().copy(showSuggestions = false, showFavorites = false, searchResults = AutoCompleteResult("", emptyList()))
    }

    private fun getUrlHeaders(url: String?): Map<String, String> {
        url?.let {
            return gpc.getHeaders(url)
        }
        return emptyMap()
    }

    private fun extractVerticalParameter(currentUrl: String?): String? {
        val url = currentUrl ?: return null

        return if (duckDuckGoUrlDetector.isDuckDuckGoVerticalUrl(url)) {
            duckDuckGoUrlDetector.extractVertical(url)
        } else {
            null
        }
    }

    private fun fireQueryChangedPixel(omnibarText: String) {
        val oldQuery = currentOmnibarViewState().omnibarText.toUri()
        val newQuery = omnibarText.toUri()

        if (Patterns.WEB_URL.matcher(oldQuery.toString()).matches()) return

        if (oldQuery == newQuery) {
            pixel.fire(String.format(Locale.US, AppPixelName.SERP_REQUERY.pixelName, PixelParameter.SERP_QUERY_NOT_CHANGED))
        } else if (oldQuery.toString().isNotBlank()) { // blank means no previous search, don't send pixel
            pixel.fire(String.format(Locale.US, AppPixelName.SERP_REQUERY.pixelName, PixelParameter.SERP_QUERY_CHANGED))
        }
    }

    private fun shouldClearHistoryOnNewQuery(): Boolean {
        val navigation = webNavigationState ?: return true
        return !currentBrowserViewState().browserShowing && navigation.hasNavigationHistory
    }

    private suspend fun removeCurrentTabFromRepository() {
        val currentTab = tabRepository.liveSelectedTab.value
        currentTab?.let {
            adClickManager.clearTabId(it.tabId)
            tabRepository.deleteTabAndSelectSource(it.tabId)
        }
    }

    override fun willOverrideUrl(newUrl: String) {
        navigationAwareLoginDetector.onEvent(NavigationEvent.Redirect(newUrl))
        val previousSiteStillLoading = currentLoadingViewState().isLoading
        if (previousSiteStillLoading) {
            showBlankContentfNewContentDelayed()
        }
    }

    override fun prefetchFavicon(url: String) {
        faviconPrefetchJob?.cancel()
        faviconPrefetchJob = viewModelScope.launch {
            val faviconFile = faviconManager.tryFetchFaviconForUrl(tabId = tabId, url = url)
            if (faviconFile != null) {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun iconReceived(
        url: String,
        icon: Bitmap,
    ) {
        val currentTab = tabRepository.liveSelectedTab.value ?: return
        val currentUrl = currentTab.url ?: return
        if (currentUrl != url) {
            Timber.d("Favicon received for a url $url, different than the current one $currentUrl")
            return
        }
        viewModelScope.launch(dispatchers.io()) {
            val faviconFile = faviconManager.storeFavicon(currentTab.tabId, ImageFavicon(icon, url))
            faviconFile?.let {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun iconReceived(
        visitedUrl: String,
        iconUrl: String,
    ) {
        val currentTab = tabRepository.liveSelectedTab.value ?: return
        val currentUrl = currentTab.url ?: return
        if (currentUrl.toUri().host != visitedUrl.toUri().host) {
            Timber.d("Favicon received for a url $visitedUrl, different than the current one $currentUrl")
            return
        }
        viewModelScope.launch {
            val faviconFile = faviconManager.storeFavicon(currentTab.tabId, UrlFavicon(iconUrl, visitedUrl))
            faviconFile?.let {
                tabRepository.updateTabFavicon(tabId, faviconFile.name)
            }
        }
    }

    override fun isDesktopSiteEnabled(): Boolean = currentBrowserViewState().isDesktopBrowsingMode

    override fun closeCurrentTab() {
        viewModelScope.launch { removeCurrentTabFromRepository() }
    }

    fun closeAndReturnToSourceIfBlankTab() {
        if (url == null) {
            closeAndSelectSourceTab()
        }
    }

    override fun closeAndSelectSourceTab() {
        viewModelScope.launch { removeAndSelectTabFromRepository() }
    }

    private suspend fun removeAndSelectTabFromRepository() {
        removeCurrentTabFromRepository()
    }

    fun onUserPressedForward() {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NavigateForward)
        if (!currentBrowserViewState().browserShowing) {
            browserViewState.value = browserStateModifier.copyForBrowserShowing(currentBrowserViewState())
            command.value = NavigationCommand.Refresh
        } else {
            command.value = NavigationCommand.NavigateForward
        }
    }

    fun onRefreshRequested() {
        val omnibarContent = currentOmnibarViewState().omnibarText
        if (!Patterns.WEB_URL.matcher(omnibarContent).matches()) {
            fireQueryChangedPixel(currentOmnibarViewState().omnibarText)
        }
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.Refresh)
        if (currentGlobalLayoutState() is Invalidated) {
            recoverTabWithQuery(url.orEmpty())
        } else {
            command.value = NavigationCommand.Refresh
        }
    }

    /**
     * Handles back navigation. Returns false if navigation could not be
     * handled at this level, giving system an opportunity to handle it
     *
     * @return true if navigation handled, otherwise false
     */
    fun onUserPressedBack(): Boolean {
        navigationAwareLoginDetector.onEvent(NavigationEvent.UserAction.NavigateBack)
        val navigation = webNavigationState ?: return false
        val hasSourceTab = tabRepository.liveSelectedTab.value?.sourceTabId != null

        if (currentFindInPageViewState().visible) {
            dismissFindInView()
            return true
        }

        if (!currentBrowserViewState().browserShowing) {
            return false
        }

        if (navigation.canGoBack) {
            command.value = NavigationCommand.NavigateBack(navigation.stepsToPreviousPage)
            return true
        } else if (hasSourceTab) {
            viewModelScope.launch {
                removeCurrentTabFromRepository()
            }
            return true
        } else if (!skipHome) {
            navigateHome()
            command.value = ShowKeyboard
            return true
        }

        Timber.d("User pressed back and tab is set to skip home; need to generate WebView preview now")
        command.value = GenerateWebViewPreviewImage
        return false
    }

    private fun navigateHome() {
        site = null
        onSiteChanged()
        webNavigationState = null

        val browserState = browserStateModifier.copyForHomeShowing(currentBrowserViewState()).copy(
            canGoForward = currentGlobalLayoutState() !is Invalidated,
        )
        browserViewState.value = browserState

        findInPageViewState.value = FindInPageViewState()
        omnibarViewState.value = currentOmnibarViewState().copy(
            omnibarText = "",
            shouldMoveCaretToEnd = false,
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(),
            forceExpand = true,
        )
        loadingViewState.value = currentLoadingViewState().copy(isLoading = false)

        deleteTabPreview(tabId)
    }

    override fun goFullScreen(view: View) {
        command.value = ShowFullScreen(view)

        val currentState = currentBrowserViewState()
        browserViewState.value = currentState.copy(isFullScreen = true)
    }

    override fun exitFullScreen() {
        val currentState = currentBrowserViewState()
        browserViewState.value = currentState.copy(isFullScreen = false)
    }

    override fun navigationStateChanged(newWebNavigationState: WebNavigationState) {
        val stateChange = newWebNavigationState.compare(webNavigationState)
        webNavigationState = newWebNavigationState

        if (!currentBrowserViewState().browserShowing) return

        canAutofillSelectCredentialsDialogCanAutomaticallyShow = true

        browserViewState.value = currentBrowserViewState().copy(
            canGoBack = newWebNavigationState.canGoBack || !skipHome,
            canGoForward = newWebNavigationState.canGoForward,
        )

        Timber.v("navigationStateChanged: $stateChange")
        when (stateChange) {
            is WebNavigationStateChange.NewPage -> pageChanged(stateChange.url, stateChange.title)
            is WebNavigationStateChange.PageCleared -> pageCleared()
            is WebNavigationStateChange.UrlUpdated -> urlUpdated(stateChange.url)
            is WebNavigationStateChange.PageNavigationCleared -> disableUserNavigation()
            else -> {}
        }

        if (newWebNavigationState.progress ?: 0 >= SHOW_CONTENT_MIN_PROGRESS) {
            showWebContent()
        }
        navigationAwareLoginDetector.onEvent(NavigationEvent.WebNavigationEvent(stateChange))
    }

    private fun showBlankContentfNewContentDelayed() {
        Timber.i("Blank: cancel job $deferredBlankSite")
        deferredBlankSite?.cancel()
        deferredBlankSite = viewModelScope.launch {
            delay(timeMillis = NEW_CONTENT_MAX_DELAY_MS)
            withContext(dispatchers.main()) {
                command.value = HideWebContent
            }
        }
        Timber.i("Blank: schedule new blank $deferredBlankSite")
    }

    private fun showWebContent() {
        Timber.i("Blank: onsite changed cancel $deferredBlankSite")
        deferredBlankSite?.cancel()
        command.value = ShowWebContent
    }

    private fun pageChanged(
        url: String,
        title: String?,
    ) {
        Timber.v("Page changed: $url")
        hasCtaBeenShownForCurrentPage.set(false)
        buildSiteFactory(url, title)
        setAdClickActiveTabData(url)

        val currentOmnibarViewState = currentOmnibarViewState()
        val omnibarText = omnibarTextForUrl(url)
        omnibarViewState.value = currentOmnibarViewState.copy(
            omnibarText = omnibarText,
            shouldMoveCaretToEnd = false,
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(urlLoaded = url),
            forceExpand = true,
        )
        val currentBrowserViewState = currentBrowserViewState()
        val domain = site?.domain
        val addFavorite = if (!currentBrowserViewState.addFavorite.isEnabled()) {
            HighlightableButton.Visible(enabled = true)
        } else {
            currentBrowserViewState.addFavorite
        }
        findInPageViewState.value = FindInPageViewState(visible = false)

        browserViewState.value = currentBrowserViewState.copy(
            browserShowing = true,
            canSaveSite = domain != null,
            addFavorite = addFavorite,
            addToHomeEnabled = domain != null,
            canSharePage = domain != null,
            showPrivacyShield = true,
            canReportSite = domain != null,
            canChangePrivacyProtection = domain != null,
            isPrivacyProtectionEnabled = false,
            showSearchIcon = false,
            showClearButton = false,
            canFindInPage = true,
            canChangeBrowsingMode = true,
            canFireproofSite = domain != null,
            isFireproofWebsite = isFireproofWebsite(),
            showDaxIcon = shouldShowDaxIcon(url, true),
            canPrintPage = domain != null,
        )

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            statisticsUpdater.refreshSearchRetentionAtb()
        }

        domain?.let { viewModelScope.launch { updateLoadingStatePrivacy(domain) } }
        domain?.let { viewModelScope.launch { updatePrivacyProtectionState(domain) } }

        viewModelScope.launch { updateBookmarkAndFavoriteState(url) }

        val permissionOrigin = site?.uri?.host?.asLocationPermissionOrigin()
        permissionOrigin?.let { viewModelScope.launch { notifyPermanentLocationPermission(permissionOrigin) } }

        registerSiteVisit()

        cacheAppLink(url)

        appLinksHandler.setUserQueryState(false)
        appLinksHandler.updatePreviousUrl(url)

        ampLinks.lastAmpLinkInfo?.let { lastAmpLinkInfo ->
            if (lastAmpLinkInfo.destinationUrl == null) {
                lastAmpLinkInfo.destinationUrl = url
            }
        }

        trackingParameters.lastCleanedUrl?.let {
            trackingParameters.lastCleanedUrl = null
            enableUrlParametersRemovedFlag()
        }

        isProcessingTrackingLink = false
        isLinkOpenedInNewTab = false

        automaticSavedLoginsMonitor.clearAutoSavedLoginId(tabId)
    }

    private fun setAdClickActiveTabData(url: String?) {
        val sourceTabId = tabRepository.liveSelectedTab.value?.sourceTabId
        val sourceTabUrl = tabRepository.liveTabs.value?.firstOrNull { it.tabId == sourceTabId }?.url
        adClickManager.setActiveTabId(tabId, url, sourceTabId, sourceTabUrl)
    }

    private fun cacheAppLink(url: String?) {
        val urlType = specialUrlDetector.determineType(url)
        if (urlType is AppLink) {
            updatePreviousAppLink(urlType)
        } else {
            clearPreviousAppLink()
        }
    }

    private fun shouldShowDaxIcon(
        currentUrl: String?,
        showPrivacyShield: Boolean,
    ): Boolean {
        val url = currentUrl ?: return false
        return showPrivacyShield && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
    }

    private suspend fun updateLoadingStatePrivacy(domain: String) {
        val isAllowListed = isAllowListed(domain)
        withContext(dispatchers.main()) {
            loadingViewState.value = currentLoadingViewState().copy(privacyOn = !isAllowListed)
        }
    }

    private suspend fun updatePrivacyProtectionState(domain: String) {
        val isAllowListed = isAllowListed(domain)
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionEnabled = isAllowListed)
        }
    }

    private suspend fun isAllowListed(domain: String): Boolean {
        return withContext(dispatchers.io()) {
            userAllowListDao.contains(domain) || contentBlocking.isAnException(domain)
        }
    }

    private suspend fun updateBookmarkAndFavoriteState(url: String) {
        val bookmark = getBookmark(url)
        val favorite = getFavorite(url)
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(
                bookmark = bookmark,
                favorite = favorite,
            )
        }
    }

    private suspend fun getBookmark(url: String): SavedSite.Bookmark? {
        return withContext(dispatchers.io()) {
            savedSitesRepository.getBookmark(url)
        }
    }

    private suspend fun getBookmarkFolder(bookmark: SavedSite.Bookmark?): BookmarkFolder? {
        if (bookmark == null) return null
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFolder(bookmark.parentId)
        }
    }

    private suspend fun getFavorite(url: String): SavedSite.Favorite? {
        return withContext(dispatchers.io()) {
            savedSitesRepository.getFavorite(url)
        }
    }

    private suspend fun notifyPermanentLocationPermission(domain: String) {
        if (!geoLocationPermissions.isDeviceLocationEnabled()) {
            viewModelScope.launch(dispatchers.io()) {
                onDeviceLocationDisabled()
            }
            return
        }

        if (!appSettingsPreferencesStore.appLocationPermission) {
            return
        }

        val permissionEntity = locationPermissionsRepository.getDomainPermission(domain)
        permissionEntity?.let {
            if (it.permission == LocationPermissionType.ALLOW_ALWAYS) {
                if (!locationPermissionMessages.containsKey(domain)) {
                    setDomainHasLocationPermissionShown(domain)
                    command.postValue(ShowDomainHasPermissionMessage(domain))
                }
            }
        }
    }

    private fun setDomainHasLocationPermissionShown(domain: String) {
        locationPermissionMessages[domain] = true
    }

    private fun urlUpdated(url: String) {
        Timber.v("Page url updated: $url")
        site?.url = url
        onSiteChanged()
        val currentOmnibarViewState = currentOmnibarViewState()
        val omnibarText = omnibarTextForUrl(url)
        omnibarViewState.postValue(
            currentOmnibarViewState.copy(
                omnibarText = omnibarText,
                shouldMoveCaretToEnd = false,
                showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(urlLoaded = url),
                forceExpand = false,
            ),
        )
        browserViewState.postValue(currentBrowserViewState().copy(isFireproofWebsite = isFireproofWebsite()))
        viewModelScope.launch { updateBookmarkAndFavoriteState(url) }
    }

    private fun omnibarTextForUrl(url: String?): String {
        if (url == null) return ""

        return if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            duckDuckGoUrlDetector.extractQuery(url) ?: url
        } else {
            url
        }
    }

    private fun pageCleared() {
        Timber.v("Page cleared: $url")
        site = null
        onSiteChanged()

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            canSaveSite = false,
            addFavorite = HighlightableButton.Visible(enabled = false),
            addToHomeEnabled = false,
            canSharePage = false,
            showPrivacyShield = false,
            canReportSite = false,
            showSearchIcon = true,
            showClearButton = true,
            canFireproofSite = false,
            showDaxIcon = false,
            canPrintPage = false,
        )
        Timber.d("showPrivacyShield=false, showSearchIcon=true, showClearButton=true")
    }

    override fun pageRefreshed(refreshedUrl: String) {
        if (url == null || refreshedUrl == url) {
            Timber.v("Page refreshed: $refreshedUrl")
            pageChanged(refreshedUrl, title)
        }
    }

    override fun progressChanged(newProgress: Int) {
        Timber.v("Loading in progress $newProgress")
        if (!currentBrowserViewState().browserShowing) return

        val isLoading = newProgress < 100 || isProcessingTrackingLink
        val progress = currentLoadingViewState()
        if (progress.progress == newProgress) return
        val visualProgress = if (newProgress < FIXED_PROGRESS || isProcessingTrackingLink) {
            FIXED_PROGRESS
        } else {
            newProgress
        }

        loadingViewState.value = progress.copy(isLoading = isLoading, progress = visualProgress)

        if (newProgress == 100) {
            command.value = RefreshUserAgent(url, currentBrowserViewState().isDesktopBrowsingMode)
            navigationAwareLoginDetector.onEvent(NavigationEvent.PageFinished)
        }
    }

    override fun onSitePermissionRequested(
        request: PermissionRequest,
        sitePermissionsAllowedToAsk: Array<String>,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            val url = request.origin.toString()
            val sitePermissionsGranted = sitePermissionsManager.getSitePermissionsGranted(url, tabId, sitePermissionsAllowedToAsk)
            val sitePermissionsToAsk = sitePermissionsAllowedToAsk.filter { !sitePermissionsGranted.contains(it) }.toTypedArray()
            if (sitePermissionsGranted.isNotEmpty()) {
                command.postValue(GrantSitePermissionRequest(sitePermissionsGranted, request))
            }
            if (sitePermissionsToAsk.isNotEmpty()) {
                command.postValue(ShowSitePermissionsDialog(sitePermissionsToAsk, request))
            }
        }
    }

    override fun onSiteLocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        locationPermission = LocationPermission(origin, callback)

        if (!geoLocationPermissions.isDeviceLocationEnabled()) {
            viewModelScope.launch(dispatchers.io()) {
                onDeviceLocationDisabled()
            }
            onSiteLocationPermissionAlwaysDenied()
            return
        }

        if (site?.domainMatchesUrl(origin) == false) {
            onSiteLocationPermissionAlwaysDenied()
            return
        }

        if (!appSettingsPreferencesStore.appLocationPermission) {
            onSiteLocationPermissionAlwaysDenied()
            return
        }

        viewModelScope.launch {
            val previouslyDeniedForever = appSettingsPreferencesStore.appLocationPermissionDeniedForever
            val permissionEntity = locationPermissionsRepository.getDomainPermission(origin)
            if (permissionEntity == null) {
                if (locationPermissionSession.containsKey(origin)) {
                    reactToSiteSessionPermission(locationPermissionSession[origin]!!)
                } else {
                    command.postValue(CheckSystemLocationPermission(origin, previouslyDeniedForever))
                }
            } else {
                if (permissionEntity.permission == LocationPermissionType.DENY_ALWAYS) {
                    onSiteLocationPermissionAlwaysDenied()
                } else {
                    command.postValue(CheckSystemLocationPermission(origin, previouslyDeniedForever))
                }
            }
        }
    }

    fun onSiteLocationPermissionSelected(
        domain: String,
        permission: LocationPermissionType,
    ) {
        locationPermission?.let { locationPermission ->
            when (permission) {
                LocationPermissionType.ALLOW_ALWAYS -> {
                    onSiteLocationPermissionAlwaysAllowed()
                    setDomainHasLocationPermissionShown(domain)
                    pixel.fire(AppPixelName.PRECISE_LOCATION_SITE_DIALOG_ALLOW_ALWAYS)
                    viewModelScope.launch {
                        locationPermissionsRepository.savePermission(domain, permission)
                        faviconManager.persistCachedFavicon(tabId, domain)
                    }
                }

                LocationPermissionType.ALLOW_ONCE -> {
                    pixel.fire(AppPixelName.PRECISE_LOCATION_SITE_DIALOG_ALLOW_ONCE)
                    locationPermissionSession[domain] = permission
                    locationPermission.callback.invoke(locationPermission.origin, true, false)
                }

                LocationPermissionType.DENY_ALWAYS -> {
                    pixel.fire(AppPixelName.PRECISE_LOCATION_SITE_DIALOG_DENY_ALWAYS)
                    onSiteLocationPermissionAlwaysDenied()
                    viewModelScope.launch {
                        locationPermissionsRepository.savePermission(domain, permission)
                        faviconManager.persistCachedFavicon(tabId, domain)
                    }
                }

                LocationPermissionType.DENY_ONCE -> {
                    pixel.fire(AppPixelName.PRECISE_LOCATION_SITE_DIALOG_DENY_ONCE)
                    locationPermissionSession[domain] = permission
                    locationPermission.callback.invoke(locationPermission.origin, false, false)
                }
            }
        }
    }

    private fun onSiteLocationPermissionAlwaysAllowed() {
        locationPermission?.let { locationPermission ->
            geoLocationPermissions.allow(locationPermission.origin)
            locationPermission.callback.invoke(locationPermission.origin, true, false)
        }
    }

    fun onSiteLocationPermissionAlwaysDenied() {
        locationPermission?.let { locationPermission ->
            geoLocationPermissions.clear(locationPermission.origin)
            locationPermission.callback.invoke(locationPermission.origin, false, false)
        }
    }

    private suspend fun onDeviceLocationDisabled() {
        geoLocationPermissions.clearAll()
    }

    private fun reactToSitePermission(permission: LocationPermissionType) {
        locationPermission?.let { locationPermission ->
            when (permission) {
                LocationPermissionType.ALLOW_ALWAYS -> {
                    onSiteLocationPermissionAlwaysAllowed()
                }

                LocationPermissionType.ALLOW_ONCE -> {
                    command.postValue(AskDomainPermission(locationPermission.origin))
                }

                LocationPermissionType.DENY_ALWAYS -> {
                    onSiteLocationPermissionAlwaysDenied()
                }

                LocationPermissionType.DENY_ONCE -> {
                    command.postValue(AskDomainPermission(locationPermission.origin))
                }
            }
        }
    }

    private fun reactToSiteSessionPermission(permission: LocationPermissionType) {
        locationPermission?.let { locationPermission ->
            if (permission == LocationPermissionType.ALLOW_ONCE) {
                locationPermission.callback.invoke(locationPermission.origin, true, false)
            } else {
                locationPermission.callback.invoke(locationPermission.origin, false, false)
            }
        }
    }

    fun onSystemLocationPermissionAllowed() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_ENABLE)
        command.postValue(RequestSystemLocationPermission)
    }

    fun onSystemLocationPermissionNotAllowed() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_LATER)
        onSiteLocationPermissionAlwaysDenied()
    }

    fun onSystemLocationPermissionNeverAllowed() {
        locationPermission?.let { locationPermission ->
            onSiteLocationPermissionSelected(locationPermission.origin, LocationPermissionType.DENY_ALWAYS)
            pixel.fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_NEVER)
        }
    }

    fun onSystemLocationPermissionGranted() {
        locationPermission?.let { locationPermission ->
            appSettingsPreferencesStore.appLocationPermissionDeniedForever = false
            appSettingsPreferencesStore.appLocationPermission = true
            pixel.fire(AppPixelName.PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_ENABLE)
            viewModelScope.launch {
                val permissionEntity = locationPermissionsRepository.getDomainPermission(locationPermission.origin)
                if (permissionEntity == null) {
                    command.postValue(AskDomainPermission(locationPermission.origin))
                } else {
                    reactToSitePermission(permissionEntity.permission)
                }
            }
        }
    }

    fun onSystemLocationPermissionDeniedOneTime() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_DISABLE)
        onSiteLocationPermissionAlwaysDenied()
    }

    fun onSystemLocationPermissionDeniedTwice() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SETTINGS_LOCATION_PERMISSION_DISABLE)
        onSystemLocationPermissionDeniedForever()
    }

    fun onSystemLocationPermissionDeniedForever() {
        appSettingsPreferencesStore.appLocationPermissionDeniedForever = true
        onSiteLocationPermissionAlwaysDenied()
    }

    private fun registerSiteVisit() {
        Schedulers.io().scheduleDirect {
            networkLeaderboardDao.incrementSitesVisited()
        }
    }

    override fun dosAttackDetected() {
        invalidateBrowsingActions()
        showErrorWithAction(R.string.dosErrorMessage)
    }

    override fun titleReceived(newTitle: String) {
        site?.title = newTitle
        onSiteChanged()
    }

    @AnyThread
    override fun sendEmailRequested(emailAddress: String) {
        command.postValue(SendEmail(emailAddress))
    }

    @AnyThread
    override fun dialTelephoneNumberRequested(telephoneNumber: String) {
        command.postValue(DialNumber(telephoneNumber))
    }

    @AnyThread
    override fun sendSmsRequested(telephoneNumber: String) {
        command.postValue(SendSms(telephoneNumber))
    }

    override fun surrogateDetected(surrogate: SurrogateResponse) {
        site?.surrogateDetected(surrogate)
    }

    // This is called when interceptor upgrades to https
    override fun upgradedToHttps() {
        httpsUpgraded = true
    }

    override fun trackerDetected(event: TrackingEvent) {
        Timber.d("Tracker detected while on $url and the document was ${event.documentUrl}")
        if (site?.domainMatchesUrl(event.documentUrl) == true) {
            site?.trackerDetected(event)
            onSiteChanged()
        }
        updateNetworkLeaderboard(event)
    }

    private fun updateNetworkLeaderboard(event: TrackingEvent) {
        val networkName = event.entity?.name ?: return
        networkLeaderboardDao.incrementNetworkCount(networkName)
    }

    override fun pageHasHttpResources(page: String) {
        if (site?.domainMatchesUrl(page) == true) {
            site?.hasHttpResources = true
            onSiteChanged()
        }
    }

    override fun onCertificateReceived(certificate: SslCertificate?) {
        site?.certificate = certificate
    }

    private fun enableUrlParametersRemovedFlag() {
        site?.urlParametersRemoved = true
        onSiteChanged()
    }

    fun onAutoconsentResultReceived(
        consentManaged: Boolean,
        optOutFailed: Boolean,
        selfTestFailed: Boolean,
        isCosmetic: Boolean?,
    ) {
        site?.consentManaged = consentManaged
        site?.consentOptOutFailed = optOutFailed
        site?.consentSelfTestFailed = selfTestFailed
        site?.consentCosmeticHide = isCosmetic
    }

    private fun onSiteChanged() {
        httpsUpgraded = false
        viewModelScope.launch {
            val privacyProtection: PrivacyShield = withContext(dispatchers.io()) {
                site?.privacyProtection() ?: PrivacyShield.UNKNOWN
            }
            Timber.i("Shield: privacyProtection $privacyProtection")
            withContext(dispatchers.main()) {
                siteLiveData.value = site
                privacyShieldViewState.value = currentPrivacyShieldState().copy(privacyShield = privacyProtection)
            }
            withContext(dispatchers.io()) {
                tabRepository.update(tabId, site)
            }
        }
    }

    override fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ) {
        command.value = ShowFileChooser(filePathCallback, fileChooserParams)
    }

    private fun currentGlobalLayoutState(): GlobalLayoutViewState = globalLayoutState.value!!
    private fun currentAutoCompleteViewState(): AutoCompleteViewState = autoCompleteViewState.value!!
    private fun currentBrowserViewState(): BrowserViewState = browserViewState.value!!
    private fun currentFindInPageViewState(): FindInPageViewState = findInPageViewState.value!!
    private fun currentAccessibilityViewState(): AccessibilityViewState = accessibilityViewState.value!!
    private fun currentOmnibarViewState(): OmnibarViewState = omnibarViewState.value!!
    private fun currentLoadingViewState(): LoadingViewState = loadingViewState.value!!
    private fun currentCtaViewState(): CtaViewState = ctaViewState.value!!
    private fun currentPrivacyShieldState(): PrivacyShieldViewState = privacyShieldViewState.value!!

    fun triggerAutocomplete(
        query: String,
        hasFocus: Boolean,
        hasQueryChanged: Boolean,
    ) {
        // determine if empty list to be shown, or existing search results
        val autoCompleteSearchResults = if (query.isBlank() || !hasFocus) {
            AutoCompleteResult(query, emptyList())
        } else {
            currentAutoCompleteViewState().searchResults
        }

        val autoCompleteSuggestionsEnabled = appSettingsPreferencesStore.autoCompleteSuggestionsEnabled
        val showAutoCompleteSuggestions = hasFocus && query.isNotBlank() && hasQueryChanged && autoCompleteSuggestionsEnabled
        val showFavoritesAsSuggestions = if (!showAutoCompleteSuggestions) {
            val urlFocused = hasFocus && query.isNotBlank() && !hasQueryChanged && UriString.isWebUrl(query)
            val emptyQueryBrowsing = query.isBlank() && currentBrowserViewState().browserShowing
            val favoritesAvailable = currentAutoCompleteViewState().favorites.isNotEmpty()
            hasFocus && (urlFocused || emptyQueryBrowsing) && favoritesAvailable
        } else {
            false
        }

        autoCompleteViewState.value = currentAutoCompleteViewState()
            .copy(
                showSuggestions = showAutoCompleteSuggestions,
                showFavorites = showFavoritesAsSuggestions,
                searchResults = autoCompleteSearchResults,
            )

        if (hasFocus && autoCompleteSuggestionsEnabled) {
            autoCompletePublishSubject.accept(query.trim())
        }
    }

    fun onOmnibarInputStateChanged(
        query: String,
        hasFocus: Boolean,
        hasQueryChanged: Boolean,
    ) {
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()
        val showPrivacyShield = !hasFocus
        val showSearchIcon = hasFocus

        omnibarViewState.value = currentOmnibarViewState().copy(
            isEditing = hasFocus,
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(
                hasFocus = hasFocus,
                query = query,
                hasQueryChanged = hasQueryChanged,
                urlLoaded = url ?: "",
            ),
            omnibarText = query,
            forceExpand = true,
        )

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            showPrivacyShield = showPrivacyShield,
            showSearchIcon = showSearchIcon,
            showTabsButton = showControls,
            fireButton = if (showControls) {
                HighlightableButton.Visible(highlighted = showPulseAnimation.value ?: false)
            } else {
                HighlightableButton.Gone
            },
            showMenuButton = if (showControls) {
                HighlightableButton.Visible()
            } else {
                HighlightableButton.Gone
            },
            showClearButton = showClearButton,
            showDaxIcon = shouldShowDaxIcon(url, showPrivacyShield),
        )

        Timber.d("showPrivacyShield=$showPrivacyShield, showSearchIcon=$showSearchIcon, showClearButton=$showClearButton")
    }

    fun onBookmarkMenuClicked() {
        val url = url ?: return
        viewModelScope.launch {
            val bookmark = currentBrowserViewState().bookmark
            if (bookmark != null) {
                pixel.fire(AppPixelName.MENU_ACTION_EDIT_BOOKMARK_PRESSED.pixelName)
                onEditSavedSiteRequested(bookmark)
            } else {
                pixel.fire(AppPixelName.MENU_ACTION_ADD_BOOKMARK_PRESSED.pixelName)
                saveSiteBookmark(url, title ?: "")
            }
        }
    }

    fun onFavoriteMenuClicked() {
        val url = url ?: return
        viewModelScope.launch {
            val favorite = currentBrowserViewState().favorite
            if (favorite != null) {
                pixel.fire(AppPixelName.MENU_ACTION_REMOVE_FAVORITE_PRESSED.pixelName)
                removeFavoriteSite(favorite)
            } else {
                val buttonHighlighted = currentBrowserViewState().addFavorite.isHighlighted()
                pixel.fire(
                    AppPixelName.MENU_ACTION_ADD_FAVORITE_PRESSED.pixelName,
                    mapOf(FAVORITE_MENU_ITEM_STATE to buttonHighlighted.toString()),
                )
                saveFavoriteSite(url, title ?: "")
            }
        }
    }

    private suspend fun saveSiteBookmark(
        url: String,
        title: String,
    ) {
        val savedBookmark = withContext(dispatchers.io()) {
            if (url.isNotBlank()) {
                faviconManager.persistCachedFavicon(tabId, url)
            }
            savedSitesRepository.insertBookmark(url, title)
        }
        val bookmarkFolder = getBookmarkFolder(savedBookmark)
        withContext(dispatchers.main()) {
            command.value = ShowSavedSiteAddedConfirmation(SavedSiteChangedViewState(savedBookmark, bookmarkFolder))
        }
    }

    private fun removeFavoriteSite(favorite: SavedSite.Favorite) {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                savedSitesRepository.delete(favorite)
            }
            withContext(dispatchers.main()) {
                command.value = DeleteSavedSiteConfirmation(favorite)
            }
        }
    }

    private fun saveFavoriteSite(
        url: String,
        title: String,
    ) {
        viewModelScope.launch {
            val favorite = withContext(dispatchers.io()) {
                if (url.isNotBlank()) {
                    faviconManager.persistCachedFavicon(tabId, url)
                    savedSitesRepository.insertFavorite(title = title, url = url)
                } else {
                    null
                }
            }
            favorite?.let {
                withContext(dispatchers.main()) {
                    command.value = ShowSavedSiteAddedConfirmation(SavedSiteChangedViewState(it, null))
                }
            }
        }
    }

    fun onFireproofWebsiteMenuClicked() {
        val domain = site?.domain ?: return
        viewModelScope.launch {
            if (currentBrowserViewState().isFireproofWebsite) {
                val fireproofWebsiteEntity = FireproofWebsiteEntity(domain)
                fireproofWebsiteRepository.removeFireproofWebsite(fireproofWebsiteEntity)
                command.value = DeleteFireproofConfirmation(fireproofWebsiteEntity = fireproofWebsiteEntity)
                pixel.fire(AppPixelName.FIREPROOF_WEBSITE_REMOVE)
            } else {
                fireproofWebsiteRepository.fireproofWebsite(domain)?.let {
                    pixel.fire(AppPixelName.FIREPROOF_WEBSITE_ADDED)
                    command.value = ShowFireproofWebSiteConfirmation(fireproofWebsiteEntity = it)
                    faviconManager.persistCachedFavicon(tabId, url = domain)
                }
            }
        }
    }

    fun onFireproofLoginDialogShown() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onFireproofLoginDialogShown()
        }
    }

    fun onUserConfirmedFireproofDialog(domain: String) {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserConfirmedFireproofDialog(domain)
        }
    }

    fun onUserDismissedFireproofLoginDialog() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserDismissedFireproofLoginDialog()
        }
    }

    fun onDisableLoginDetectionDialogShown() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onDisableLoginDetectionDialogShown()
        }
    }

    fun onUserConfirmedDisableLoginDetectionDialog() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserConfirmedDisableLoginDetectionDialog()
        }
    }

    fun onUserDismissedDisableLoginDetectionDialog() {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserDismissedDisableLoginDetectionDialog()
        }
    }

    fun onFireproofWebsiteSnackbarUndoClicked(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofWebsiteRepository.removeFireproofWebsite(fireproofWebsiteEntity)
            pixel.fire(AppPixelName.FIREPROOF_WEBSITE_UNDO)
        }
    }

    fun onUserDismissedAutomaticFireproofLoginDialog() {
        viewModelScope.launch {
            fireproofDialogsEventHandler.onUserDismissedAutomaticFireproofLoginDialog()
        }
    }

    fun onUserFireproofSiteInAutomaticFireproofLoginDialog(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserRequestedAskEveryTime(domain)
        }
    }

    fun onUserEnabledAutomaticFireproofLoginDialog(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofDialogsEventHandler.onUserEnabledAutomaticFireproofing(domain)
        }
    }

    fun onRemoveFireproofWebsiteSnackbarUndoClicked(fireproofWebsiteEntity: FireproofWebsiteEntity) {
        viewModelScope.launch(dispatchers.io()) {
            fireproofWebsiteRepository.fireproofWebsite(fireproofWebsiteEntity.domain)
            pixel.fire(AppPixelName.FIREPROOF_REMOVE_WEBSITE_UNDO)
        }
    }

    override fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    override fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId)
        }
    }

    override fun onSavedSiteDeleted(savedSite: SavedSite) {
        command.value = DeleteSavedSiteConfirmation(savedSite)
        delete(savedSite)
    }

    private fun delete(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            faviconManager.deletePersistedFavicon(savedSite.url)
            savedSitesRepository.delete(savedSite)
        }
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            val bookmarkFolder =
                if (savedSite is SavedSite.Bookmark) {
                    getBookmarkFolder(savedSite)
                } else {
                    null
                }

            withContext(dispatchers.main()) {
                command.value = ShowEditSavedSiteDialog(
                    SavedSiteChangedViewState(
                        savedSite,
                        bookmarkFolder,
                    ),
                )
            }
        }
    }

    fun onDeleteQuickAccessItemRequested(savedSite: SavedSite) {
        command.value = DeleteSavedSiteConfirmation(savedSite)
    }

    fun onBrokenSiteSelected() {
        command.value = BrokenSiteFeedback(BrokenSiteData.fromSite(site))
    }

    fun onPrivacyProtectionMenuClicked() {
        val domain = site?.domain ?: return
        appCoroutineScope.launch(dispatchers.io()) {
            if (isAllowListed(domain)) {
                removeFromAllowList(domain)
            } else {
                addToAllowList(domain)
            }
            command.postValue(NavigationCommand.Refresh)
        }
    }

    private suspend fun addToAllowList(domain: String) {
        pixel.fire(AppPixelName.BROWSER_MENU_ALLOWLIST_ADD)
        withContext(dispatchers.io()) {
            userAllowListDao.insert(domain)
        }
        withContext(dispatchers.main()) {
            command.value = ShowPrivacyProtectionDisabledConfirmation(domain)
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionEnabled = true)
        }
    }

    private suspend fun removeFromAllowList(domain: String) {
        pixel.fire(AppPixelName.BROWSER_MENU_ALLOWLIST_REMOVE)
        withContext(dispatchers.io()) {
            userAllowListDao.delete(domain)
        }
        withContext(dispatchers.main()) {
            command.value = ShowPrivacyProtectionEnabledConfirmation(domain)
            browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionEnabled = false)
        }
    }

    fun onDisablePrivacyProtectionSnackbarUndoClicked(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            userAllowListDao.insert(domain)
            withContext(dispatchers.main()) {
                browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionEnabled = true)
                command.value = NavigationCommand.Refresh
            }
        }
    }

    fun onEnablePrivacyProtectionSnackbarUndoClicked(domain: String) {
        viewModelScope.launch(dispatchers.io()) {
            userAllowListDao.delete(domain)
            withContext(dispatchers.main()) {
                browserViewState.value = currentBrowserViewState().copy(isPrivacyProtectionEnabled = false)
                command.value = NavigationCommand.Refresh
            }
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = EditWithSelectedQuery(query)
    }

    fun userLongPressedInWebView(
        target: LongPressTarget,
        menu: ContextMenu,
    ) {
        Timber.i("Long pressed on ${target.type}, (url=${target.url}), (image url = ${target.imageUrl})")
        longPressHandler.handleLongPress(target.type, target.url, menu)
    }

    fun userSelectedItemFromLongPressMenu(
        longPressTarget: LongPressTarget,
        item: MenuItem,
    ): Boolean {
        val requiredAction = longPressHandler.userSelectedMenuItem(longPressTarget, item)
        Timber.d("Required action from long press is $requiredAction")

        return when (requiredAction) {
            is RequiredAction.OpenInNewTab -> {
                command.value = GenerateWebViewPreviewImage
                command.value = OpenInNewTab(query = requiredAction.url, sourceTabId = tabId)
                true
            }

            is RequiredAction.OpenInNewBackgroundTab -> {
                command.value = GenerateWebViewPreviewImage
                viewModelScope.launch { openInNewBackgroundTab(requiredAction.url) }
                true
            }

            is RequiredAction.DownloadFile -> {
                command.value = DownloadImage(requiredAction.url, false)
                true
            }

            is RequiredAction.ShareLink -> {
                command.value = ShareLink(requiredAction.url)
                true
            }

            is RequiredAction.CopyLink -> {
                command.value = CopyLink(requiredAction.url)
                true
            }

            RequiredAction.None -> {
                false
            }
        }
    }

    suspend fun openInNewBackgroundTab(url: String) {
        tabRepository.addNewTabAfterExistingTab(url, tabId)
        command.value = OpenInNewBackgroundTab(url)
    }

    fun onFindInPageSelected() {
        findInPageViewState.value = FindInPageViewState(visible = true)
    }

    fun userFindingInPage(searchTerm: String) {
        val currentViewState = currentFindInPageViewState()
        var findInPage = currentViewState.copy(visible = true, searchTerm = searchTerm)
        if (searchTerm.isEmpty()) {
            findInPage = findInPage.copy(showNumberMatches = false)
        }
        findInPageViewState.value = findInPage
        command.value = FindInPageCommand(searchTerm)
    }

    fun dismissFindInView() {
        findInPageViewState.value = currentFindInPageViewState().copy(visible = false, searchTerm = "")
        command.value = DismissFindInPage
    }

    fun onFindResultsReceived(
        activeMatchOrdinal: Int,
        numberOfMatches: Int,
    ) {
        val activeIndex = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
        val currentViewState = currentFindInPageViewState()
        findInPageViewState.value = currentViewState.copy(
            showNumberMatches = true,
            activeMatchIndex = activeIndex,
            numberMatches = numberOfMatches,
        )
    }

    fun onWebSessionRestored() {
        globalLayoutState.value = Browser(isNewTabState = false)
    }

    fun onChangeBrowserModeClicked() {
        val currentBrowserViewState = currentBrowserViewState()
        val desktopSiteRequested = !currentBrowserViewState().isDesktopBrowsingMode
        browserViewState.value = currentBrowserViewState.copy(isDesktopBrowsingMode = desktopSiteRequested)
        command.value = RefreshUserAgent(site?.uri?.toString(), desktopSiteRequested)

        val uri = site?.uri ?: return

        pixel.fire(
            if (desktopSiteRequested) {
                AppPixelName.MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED
            } else {
                AppPixelName.MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED
            },
        )

        if (desktopSiteRequested && uri.isMobileSite) {
            val desktopUrl = uri.toDesktopUri().toString()
            Timber.i("Original URL $url - attempting $desktopUrl with desktop site UA string")
            command.value = NavigationCommand.Navigate(desktopUrl, getUrlHeaders(desktopUrl))
        } else {
            command.value = NavigationCommand.Refresh
        }
    }

    private fun initializeViewStates() {
        initializeDefaultViewStates()
        viewModelScope.launch {
            initializeViewStatesFromPersistedData()
        }
    }

    private fun initializeDefaultViewStates() {
        globalLayoutState.value = Browser()
        browserViewState.value = BrowserViewState()
        loadingViewState.value = LoadingViewState()
        autoCompleteViewState.value = AutoCompleteViewState()
        omnibarViewState.value = OmnibarViewState()
        findInPageViewState.value = FindInPageViewState()
        ctaViewState.value = CtaViewState()
        privacyShieldViewState.value = PrivacyShieldViewState()
        accessibilityViewState.value = AccessibilityViewState()
    }

    private suspend fun initializeViewStatesFromPersistedData() {
        withContext(dispatchers.io()) {
            val addToHomeSupported = addToHomeCapabilityDetector.isAddToHomeSupported()
            val showAutofill = autofillCapabilityChecker.canAccessCredentialManagementScreen()
            val showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch()

            withContext(dispatchers.main()) {
                browserViewState.value = currentBrowserViewState().copy(
                    addToHomeVisible = addToHomeSupported,
                    showAutofill = showAutofill,
                )
                omnibarViewState.value = currentOmnibarViewState().copy(
                    showVoiceSearch = showVoiceSearch,
                )
            }
        }
    }

    fun onShareSelected() {
        url?.let {
            command.value = ShareLink(removeAtbAndSourceParamsFromSearch(it))
        }
    }

    fun determineShowBrowser() {
        val showBrowser = currentBrowserViewState().browserShowing || !url.isNullOrBlank()
        browserViewState.value = currentBrowserViewState().copy(browserShowing = showBrowser)
    }

    private fun showBrowser() {
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true)
        globalLayoutState.value = Browser(isNewTabState = false)
    }

    override fun historicalPageSelected(stackIndex: Int) {
        command.value = NavigationCommand.NavigateToHistory(stackIndex)
    }

    private fun removeAtbAndSourceParamsFromSearch(url: String): String {
        if (!duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            return url
        }

        val uri = Uri.parse(url)
        val paramsToRemove = arrayOf(AppUrl.ParamKey.ATB, AppUrl.ParamKey.SOURCE)
        val parameterNames = uri.queryParameterNames.filterNot { paramsToRemove.contains(it) }
        val builder = uri.buildUpon()
        builder.clearQuery()

        for (paramName in parameterNames) {
            builder.appendQueryParameter(paramName, uri.getQueryParameter(paramName))
        }

        return builder.build().toString()
    }

    fun saveWebViewState(
        webView: WebView?,
        tabId: String,
    ) {
        webViewSessionStorage.saveSession(webView, tabId)
    }

    fun restoreWebViewState(
        webView: WebView?,
        lastUrl: String,
    ) {
        val sessionRestored = webViewSessionStorage.restoreSession(webView, tabId)
        if (sessionRestored) {
            Timber.v("Successfully restored session")
            onWebSessionRestored()
        } else {
            if (lastUrl.isNotBlank()) {
                Timber.w("Restoring last url but page history has been lost - url=[$lastUrl]")
                onUserSubmittedQuery(lastUrl)
            }
        }
    }

    @SuppressLint("CheckResult")
    fun onPinPageToHomeSelected() {
        val currentPage = url ?: return
        val title = if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(currentPage)) {
            duckDuckGoUrlDetector.extractQuery(currentPage) ?: currentPage
        } else {
            currentPage.toUri().baseHost ?: currentPage
        }

        viewModelScope.launch {
            val favicon: Bitmap? = faviconManager.loadFromDisk(tabId = tabId, url = currentPage)
            command.value = AddHomeShortcut(title, currentPage, favicon)
        }
    }

    fun onBrowserMenuClicked() {
        Timber.i("favoritesOnboarding onBrowserMenuClicked")
        val menuHighlighted = currentBrowserViewState().showMenuButton.isHighlighted()
        if (menuHighlighted) {
            this.showFavoritesOnboarding = false
            browserViewState.value = currentBrowserViewState().copy(
                showMenuButton = HighlightableButton.Visible(highlighted = false),
                addFavorite = HighlightableButton.Visible(highlighted = true),
            )
        }
    }

    fun onBrowserMenuClosed() {
        viewModelScope.launch {
            Timber.i("favoritesOnboarding onBrowserMenuClosed")
            if (currentBrowserViewState().addFavorite.isHighlighted()) {
                browserViewState.value = currentBrowserViewState().copy(
                    addFavorite = HighlightableButton.Visible(highlighted = false),
                )
            }
        }
    }

    fun userRequestedOpeningNewTab() {
        command.value = GenerateWebViewPreviewImage
        command.value = LaunchNewTab
    }

    fun onSurveyChanged(
        survey: Survey?,
        locale: Locale = Locale.getDefault(),
    ) {
        val surveyCleared = ctaViewModel.onSurveyChanged(survey)
        if (surveyCleared) {
            ctaViewState.value = currentCtaViewState().copy(cta = null)
            return
        }
        viewModelScope.launch {
            if (survey != null) {
                refreshCta()
                surveyNotificationScheduler.scheduleSurveyAvailableNotification(survey)
            } else {
                surveyNotificationScheduler.removeScheduledSurveyAvailableNotification()
            }
        }
    }

    fun onCtaShown() {
        val cta = ctaViewState.value?.cta ?: return
        viewModelScope.launch(dispatchers.io()) {
            ctaViewModel.onCtaShown(cta)
        }
    }

    suspend fun refreshCta(): Cta? {
        if (currentGlobalLayoutState() is Browser) {
            val isBrowserShowing = currentBrowserViewState().browserShowing
            if (hasCtaBeenShownForCurrentPage.get() && isBrowserShowing) return null
            val cta = withContext(dispatchers.io()) {
                ctaViewModel.refreshCta(
                    dispatchers.io(),
                    isBrowserShowing,
                    siteLiveData.value,
                    showFavoritesOnboarding,
                )
            }
            if (isBrowserShowing && cta != null) hasCtaBeenShownForCurrentPage.set(true)
            ctaViewState.value = currentCtaViewState().copy(cta = cta)
            ctaChangedTicker.emit(System.currentTimeMillis().toString())
            return cta
        }
        return null
    }

    private fun showOrHideKeyboard(cta: Cta?) {
        command.value =
            if (cta is DialogCta || cta is HomePanelCta) HideKeyboard else ShowKeyboard
    }

    fun registerDaxBubbleCtaDismissed() {
        viewModelScope.launch {
            val cta = ctaViewState.value?.cta ?: return@launch
            ctaViewModel.registerDaxBubbleCtaDismissed(cta)
            ctaViewState.value = currentCtaViewState().copy(cta = null)
        }
    }

    fun onUserClickCtaOkButton() {
        val cta = currentCtaViewState().cta ?: return
        ctaViewModel.onUserClickCtaOkButton(cta)
        command.value = when (cta) {
            is HomePanelCta.Survey -> LaunchSurvey(cta.survey)
            is HomePanelCta.AddWidgetAuto, is HomePanelCta.AddWidgetInstructions -> LaunchAddWidget
            else -> return
        }
    }

    fun onUserClickCtaSecondaryButton() {
        viewModelScope.launch {
            val cta = currentCtaViewState().cta ?: return@launch
            ctaViewModel.onUserDismissedCta(cta)
        }
    }

    fun onMessageShown() {
        val message = currentCtaViewState().message ?: return
        viewModelScope.launch {
            remoteMessagingModel.get().onMessageShown(message)
        }
    }

    fun onMessageCloseButtonClicked() {
        val message = currentCtaViewState().message ?: return
        viewModelScope.launch {
            remoteMessagingModel.get().onMessageDismissed(message)
            refreshCta()
        }
    }

    fun onMessagePrimaryButtonClicked() {
        val message = currentCtaViewState().message ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.get().onPrimaryActionClicked(message) ?: return@launch
            command.value = action.asBrowserTabCommand() ?: return@launch
            refreshCta()
        }
    }

    fun onMessageSecondaryButtonClicked() {
        val message = currentCtaViewState().message ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.get().onSecondaryActionClicked(message) ?: return@launch
            command.value = action.asBrowserTabCommand() ?: return@launch
            refreshCta()
        }
    }

    fun onMessageActionButtonClicked() {
        val message = currentCtaViewState().message ?: return
        viewModelScope.launch {
            val action = remoteMessagingModel.get().onActionClicked(message) ?: return@launch
            command.value = action.asBrowserTabCommand() ?: return@launch
            refreshCta()
        }
    }

    fun onUserHideDaxDialog() {
        val cta = currentCtaViewState().cta ?: return
        command.value = DaxCommand.HideDaxDialog(cta)
    }

    fun onDaxDialogDismissed() {
        val cta = currentCtaViewState().cta ?: return
        if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
            command.value = DaxCommand.FinishPartialTrackerAnimation
        }
        onUserDismissedCta()
    }

    fun onUserDismissedCta() {
        val cta = currentCtaViewState().cta ?: return
        viewModelScope.launch {
            ctaViewModel.onUserDismissedCta(cta)
            when (cta) {
                is HomePanelCta -> refreshCta()
                else -> ctaViewState.value = currentCtaViewState().copy(cta = null)
            }
        }
    }

    fun updateTabPreview(
        tabId: String,
        fileName: String,
    ) {
        tabRepository.updateTabPreviewImage(tabId, fileName)
    }

    fun deleteTabPreview(tabId: String) {
        tabRepository.updateTabPreviewImage(tabId, null)
    }

    override fun handleAppLink(
        appLink: AppLink,
        isForMainFrame: Boolean,
    ): Boolean {
        return appLinksHandler.handleAppLink(
            isForMainFrame,
            appLink.uriString,
            appSettingsPreferencesStore.appLinksEnabled,
            !appSettingsPreferencesStore.showAppLinksPrompt,
        ) { appLinkClicked(appLink) }
    }

    fun openAppLink() {
        browserViewState.value?.previousAppLink?.let { appLink ->
            command.value = OpenAppLink(appLink)
        }
    }

    fun clearPreviousUrl() {
        appLinksHandler.updatePreviousUrl(null)
    }

    fun clearPreviousAppLink() {
        browserViewState.value = currentBrowserViewState().copy(
            previousAppLink = null,
        )
    }

    private fun updatePreviousAppLink(appLink: AppLink) {
        browserViewState.value = currentBrowserViewState().copy(
            previousAppLink = appLink,
        )
    }

    private fun appLinkClicked(appLink: AppLink) {
        if (appSettingsPreferencesStore.showAppLinksPrompt || appLinksHandler.isUserQuery()) {
            command.value = ShowAppLinkPrompt(appLink)
            appLinksHandler.setUserQueryState(false)
        } else {
            command.value = OpenAppLink(appLink)
        }
    }

    override fun handleNonHttpAppLink(nonHttpAppLink: NonHttpAppLink): Boolean {
        nonHttpAppLinkClicked(nonHttpAppLink)
        return true
    }

    fun nonHttpAppLinkClicked(appLink: NonHttpAppLink) {
        command.value = HandleNonHttpAppLink(appLink, getUrlHeaders(appLink.fallbackUrl))
    }

    fun onPrintSelected() {
        url?.let {
            pixel.fire(AppPixelName.MENU_ACTION_PRINT_PRESSED)
            command.value = PrintLink(removeAtbAndSourceParamsFromSearch(it), defaultMediaSize())
        }
    }

    fun printFromWebView() {
        viewModelScope.launch {
            onPrintSelected()
        }
    }

    override fun openMessageInNewTab(message: Message) {
        command.value = OpenMessageInNewTab(message, tabId)
    }

    override fun recoverFromRenderProcessGone() {
        webNavigationState?.let {
            navigationStateChanged(EmptyNavigationState(it))
        }
        invalidateBrowsingActions()
        showErrorWithAction()
    }

    override fun requiresAuthentication(request: BasicAuthenticationRequest) {
        if (request.host != site?.uri?.host) {
            omnibarViewState.value = currentOmnibarViewState().copy(
                omnibarText = request.site,
                showVoiceSearch = false,
                forceExpand = true,
            )
            command.value = HideWebContent
        }
        command.value = RequiresAuthentication(request)
    }

    fun handleAuthentication(
        request: BasicAuthenticationRequest,
        credentials: BasicAuthenticationCredentials,
    ) {
        request.handler.proceed(credentials.username, credentials.password)
        command.value = ShowWebContent
        command.value = SaveCredentials(request, credentials)
    }

    fun cancelAuthentication(request: BasicAuthenticationRequest) {
        request.handler.cancel()
        command.value = ShowWebContent
    }

    fun userLaunchingTabSwitcher() {
        command.value = LaunchTabSwitcher
    }

    private fun isFireproofWebsite(domain: String? = site?.domain): Boolean {
        if (domain == null) return false
        val fireproofWebsites = fireproofWebsiteState.value
        return fireproofWebsites?.any { it.domain == domain } ?: false
    }

    private fun invalidateBrowsingActions() {
        globalLayoutState.value = Invalidated
        loadingViewState.value = LoadingViewState()
        findInPageViewState.value = FindInPageViewState()
    }

    private fun disableUserNavigation() {
        browserViewState.value = currentBrowserViewState().copy(
            canGoBack = false,
            canGoForward = false,
            canReportSite = false,
            canChangeBrowsingMode = false,
            canFireproofSite = false,
        )
    }

    private fun showErrorWithAction(errorMessage: Int = R.string.crashedWebViewErrorMessage) {
        command.value = ShowErrorWithAction(errorMessage) { this.onUserSubmittedQuery(url.orEmpty()) }
    }

    private fun recoverTabWithQuery(query: String) {
        closeCurrentTab()
        command.value = OpenInNewTab(query)
    }

    override fun redirectTriggeredByGpc() {
        navigationAwareLoginDetector.onEvent(NavigationEvent.GpcRedirect)
    }

    override fun loginDetected() {
        val currentUrl = site?.url ?: return
        navigationAwareLoginDetector.onEvent(NavigationEvent.LoginAttempt(currentUrl))
    }

    fun requestFileDownload(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean,
    ) {
        if (url.startsWith("blob:")) {
            command.value = ConvertBlobToDataUri(url, mimeType)
        } else {
            sendRequestFileDownloadCommand(url, contentDisposition, mimeType, requestUserConfirmation)
        }
    }

    private fun sendRequestFileDownloadCommand(
        url: String,
        contentDisposition: String?,
        mimeType: String,
        requestUserConfirmation: Boolean,
    ) {
        command.postValue(RequestFileDownload(url, contentDisposition, mimeType, requestUserConfirmation))
    }

    fun showEmailProtectionChooseEmailPrompt() {
        emailManager.getEmailAddress()?.let {
            command.postValue(ShowEmailProtectionChooseEmailPrompt(it))
        }
    }

    fun consumeAliasAndCopyToClipboard() {
        emailManager.getAlias()?.let {
            command.value = CopyAliasToClipboard(it)
            pixel.enqueueFire(
                AppPixelName.EMAIL_COPIED_TO_CLIPBOARD,
                mapOf(
                    PixelParameter.COHORT to emailManager.getCohort(),
                    PixelParameter.LAST_USED_DAY to emailManager.getLastUsedDate(),
                ),
            )
            emailManager.setNewLastUsedDate()
        }
    }

    /**
     * API called after user selected to autofill a private alias into a form
     */
    fun usePrivateDuckAddress(originalUrl: String, duckAddress: String) {
        command.postValue(InjectEmailAddress(duckAddress = duckAddress, originalUrl = originalUrl, autoSaveLogin = true))
    }

    fun usePersonalDuckAddress(originalUrl: String, duckAddress: String) {
        command.postValue(InjectEmailAddress(duckAddress = duckAddress, originalUrl = originalUrl, autoSaveLogin = false))
    }

    fun download(pendingFileDownload: PendingFileDownload) {
        fileDownloader.enqueueDownload(pendingFileDownload)
    }

    fun deleteQuickAccessItem(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io() + NonCancellable) {
            faviconManager.deletePersistedFavicon(savedSite.url)
            savedSitesRepository.delete(savedSite)
        }
    }

    fun insertQuickAccessItem(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.insert(savedSite)
        }
    }

    fun onQuickAccessListChanged(newList: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateWithPosition(newList.map { it.favorite })
        }
    }

    fun onWebViewRefreshed() {
        accessibilityViewState.value = currentAccessibilityViewState().copy(refreshWebView = false)
        canAutofillSelectCredentialsDialogCanAutomaticallyShow = true
    }

    override fun handleCloakedAmpLink(initialUrl: String) {
        isProcessingTrackingLink = true
        command.value = ExtractUrlFromCloakedAmpLink(initialUrl)
    }

    override fun startProcessingTrackingLink() {
        isProcessingTrackingLink = true
    }

    fun updateLastAmpLink(url: String) {
        ampLinks.lastAmpLinkInfo = AmpLinkInfo(ampLink = url)
    }

    override fun onUrlExtractionError(initialUrl: String) {
        command.postValue(LoadExtractedUrl(extractedUrl = initialUrl))
    }

    override fun onUrlExtracted(
        initialUrl: String,
        extractedUrl: String?,
    ) {
        val destinationUrl = ampLinks.processDestinationUrl(initialUrl, extractedUrl)
        command.postValue(LoadExtractedUrl(extractedUrl = destinationUrl))
    }

    fun returnNoCredentialsWithPage(originalUrl: String) {
        command.postValue(CancelIncomingAutofillRequest(originalUrl))
    }

    fun onConfigurationChanged() {
        browserViewState.value = currentBrowserViewState().copy(
            forceRenderingTicker = System.currentTimeMillis(),
        )
    }

    fun onMessageReceived() {
        isLinkOpenedInNewTab = true
    }

    override fun linkOpenedInNewTab(): Boolean {
        return isLinkOpenedInNewTab
    }

    override fun isActiveTab(): Boolean {
        liveSelectedTab.value?.let {
            return it.tabId == tabId
        }

        return false
    }

    override fun onReceivedError(errorType: WebViewErrorResponse, url: String) {
        browserViewState.value =
            currentBrowserViewState().copy(browserError = errorType, showPrivacyShield = false, showDaxIcon = false, showSearchIcon = false)
        command.postValue(WebViewError(errorType, url))
    }

    override fun recordErrorCode(error: String, url: String) {
        // when navigating from one page to another it can happen that errors are recorded before pageChanged etc. are
        // called triggering a buildSite.
        if (url != site?.url) {
            site = siteFactory.buildSite(url)
        }
        Timber.d("recordErrorCode $error in ${site?.url}")
        site?.onErrorDetected(error)
    }

    override fun recordHttpErrorCode(statusCode: Int, url: String) {
        // when navigating from one page to another it can happen that errors are recorded before pageChanged etc. are
        // called triggering a buildSite.
        if (url != site?.url) {
            site = siteFactory.buildSite(url)
        }
        Timber.d("recordHttpErrorCode $statusCode in ${site?.url}")
        site?.onHttpErrorDetected(statusCode)
    }

    fun onAutofillMenuSelected() {
        command.value = LaunchAutofillSettings
    }

    @VisibleForTesting
    fun updateWebNavigation(webNavigationState: WebNavigationState) {
        this.webNavigationState = webNavigationState
    }

    fun cancelPendingAutofillRequestToChooseCredentials() {
        canAutofillSelectCredentialsDialogCanAutomaticallyShow = false
    }

    fun canAutofillSelectCredentialsDialogCanAutomaticallyShow(): Boolean {
        return canAutofillSelectCredentialsDialogCanAutomaticallyShow && !currentOmnibarViewState().isEditing
    }

    fun onShowUserCredentialsSaved(it: LoginCredentials) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = ShowUserCredentialSavedOrUpdatedConfirmation(
                credentials = it,
                includeShortcutToViewCredential = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                messageResourceId = R.string.autofillLoginSavedSnackbarMessage,
            )
        }
    }

    fun onShowUserCredentialsUpdated(it: LoginCredentials) {
        viewModelScope.launch(dispatchers.main()) {
            command.value = ShowUserCredentialSavedOrUpdatedConfirmation(
                credentials = it,
                includeShortcutToViewCredential = autofillCapabilityChecker.canAccessCredentialManagementScreen(),
                messageResourceId = R.string.autofillLoginUpdatedSnackbarMessage,
            )
        }
    }

    private fun defaultMediaSize(): PrintAttributes.MediaSize {
        val country = device.country.uppercase(Locale.getDefault())
        return if (PRINT_LETTER_FORMAT_COUNTRIES_ISO3166_2.contains(country)) {
            PrintAttributes.MediaSize.NA_LETTER
        } else {
            PrintAttributes.MediaSize.ISO_A4
        }
    }

    fun voiceSearchDisabled() {
        omnibarViewState.value = currentOmnibarViewState().copy(
            showVoiceSearch = voiceSearchAvailability.shouldShowVoiceSearch(urlLoaded = url ?: ""),
        )
    }

    companion object {
        private const val FIXED_PROGRESS = 50

        // Minimum progress to show web content again after decided to hide web content (possible spoofing attack).
        // We think that progress is enough to assume next site has already loaded new content.
        private const val SHOW_CONTENT_MIN_PROGRESS = 50
        private const val NEW_CONTENT_MAX_DELAY_MS = 1000L
        private const val ONE_HOUR_IN_MS = 3_600_000

        // https://www.iso.org/iso-3166-country-codes.html
        private val PRINT_LETTER_FORMAT_COUNTRIES_ISO3166_2 = setOf(
            Locale.US.country,
            Locale.CANADA.country,
            "MX", // Mexico
        )
    }
}
