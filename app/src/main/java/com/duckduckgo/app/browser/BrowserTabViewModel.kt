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
import android.os.Message
import android.util.Patterns
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.ui.EditSavedSiteDialogFragment.EditSavedSiteListener
import com.duckduckgo.app.brokensite.BrokenSiteData
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.*
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Browser
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Invalidated
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.AppLink
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.NonHttpAppLink
import com.duckduckgo.app.browser.WebNavigationStateChange.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.applinks.AppLinksHandler
import com.duckduckgo.app.browser.applinks.DuckDuckGoAppLinksHandler
import com.duckduckgo.app.browser.downloader.DownloadFailReason
import com.duckduckgo.app.browser.downloader.FileDownloader
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favicon.FaviconSource.ImageFavicon
import com.duckduckgo.app.browser.favicon.FaviconSource.UrlFavicon
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter
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
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.ui.HttpAuthenticationDialogFragment.HttpAuthenticationListener
import com.duckduckgo.app.cta.ui.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.events.db.UserEventKey
import com.duckduckgo.app.global.events.db.UserEventsStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.global.model.domainMatchesUrl
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.global.view.asLocationPermissionOrigin
import com.duckduckgo.app.globalprivacycontrol.GlobalPrivacyControl
import com.duckduckgo.app.location.GeoLocationPermissions
import com.duckduckgo.app.location.data.LocationPermissionType
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.location.ui.SiteLocationPermissionDialog
import com.duckduckgo.app.location.ui.SystemLocationPermissionDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.FAVORITE_MENU_ITEM_STATE
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.db.TemporaryTrackingWhitelistDao
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.di.scopes.AppObjectGraph
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

class BrowserTabViewModel(
    private val statisticsUpdater: StatisticsUpdater,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val siteFactory: SiteFactory,
    private val tabRepository: TabRepository,
    private val userWhitelistDao: UserWhitelistDao,
    private val temporaryTrackingWhitelistDao: TemporaryTrackingWhitelistDao,
    private val networkLeaderboardDao: NetworkLeaderboardDao,
    private val bookmarksDao: BookmarksDao,
    private val favoritesRepository: FavoritesRepository,
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
    private val ctaViewModel: CtaViewModel,
    private val searchCountDao: SearchCountDao,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val userEventsStore: UserEventsStore,
    private val fileDownloader: FileDownloader,
    private val globalPrivacyControl: GlobalPrivacyControl,
    private val fireproofDialogsEventHandler: FireproofDialogsEventHandler,
    private val emailManager: EmailManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appLinksHandler: AppLinksHandler
) : WebViewClientListener, EditSavedSiteListener, HttpAuthenticationListener, SiteLocationPermissionDialog.SiteLocationPermissionDialogListener,
    SystemLocationPermissionDialog.SystemLocationPermissionDialogListener, ViewModel() {

    private var buildingSiteFactoryJob: Job? = null

    sealed class GlobalLayoutViewState {
        data class Browser(val isNewTabState: Boolean = true) : GlobalLayoutViewState()
        object Invalidated : GlobalLayoutViewState()
    }

    data class CtaViewState(
        val cta: Cta? = null,
        val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite> = emptyList()
    )

    data class BrowserViewState(
        val browserShowing: Boolean = false,
        val isFullScreen: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val canChangeBrowsingMode: Boolean = true,
        val showPrivacyGrade: Boolean = false,
        val showSearchIcon: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val fireButton: HighlightableButton = HighlightableButton.Visible(),
        val showMenuButton: HighlightableButton = HighlightableButton.Visible(),
        val canSharePage: Boolean = false,
        val canAddBookmarks: Boolean = false,
        val addFavorite: HighlightableButton = HighlightableButton.Visible(enabled = false),
        val canFireproofSite: Boolean = false,
        val isFireproofWebsite: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val canWhitelist: Boolean = false,
        val isWhitelisted: Boolean = false,
        val canReportSite: Boolean = false,
        val addToHomeEnabled: Boolean = false,
        val addToHomeVisible: Boolean = false,
        val showDaxIcon: Boolean = false,
        val isEmailSignedIn: Boolean = false
    )

    sealed class HighlightableButton {
        data class Visible(val enabled: Boolean = true, val highlighted: Boolean = false) : HighlightableButton()
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
        val shouldMoveCaretToEnd: Boolean = false
    )

    data class LoadingViewState(
        val isLoading: Boolean = false,
        val privacyOn: Boolean = true,
        val progress: Int = 0
    )

    data class FindInPageViewState(
        val visible: Boolean = false,
        val showNumberMatches: Boolean = false,
        val activeMatchIndex: Int = 0,
        val searchTerm: String = "",
        val numberMatches: Int = 0,
        val canFindInPage: Boolean = false
    )

    data class PrivacyGradeViewState(
        val privacyGrade: PrivacyGrade? = null,
        val shouldAnimate: Boolean = false,
        val showEmptyGrade: Boolean = true
    ) {
        val isEnabled: Boolean = privacyGrade != PrivacyGrade.UNKNOWN
    }

    data class AutoCompleteViewState(
        val showSuggestions: Boolean = false,
        val showFavorites: Boolean = false,
        val searchResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
        val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite> = emptyList()
    )

    data class LocationPermission(val origin: String, val callback: GeolocationPermissions.Callback)

    sealed class Command {
        object Refresh : Command()
        class Navigate(val url: String, val headers: Map<String, String>) : Command()
        class NavigateBack(val steps: Int) : Command()
        object NavigateForward : Command()
        class OpenInNewTab(val query: String, val sourceTabId: String? = null) : Command()
        class OpenMessageInNewTab(val message: Message, val sourceTabId: String? = null) : Command()
        class OpenInNewBackgroundTab(val query: String) : Command()
        object LaunchNewTab : Command()
        object ResetHistory : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
        object ShowKeyboard : Command()
        object HideKeyboard : Command()
        class ShowFullScreen(val view: View) : Command()
        class DownloadImage(val url: String, val requestUserConfirmation: Boolean) : Command()
        class ShowSavedSiteAddedConfirmation(val savedSite: SavedSite) : Command()
        class ShowEditSavedSiteDialog(val savedSite: SavedSite) : Command()
        class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
        class ShowFireproofWebSiteConfirmation(val fireproofWebsiteEntity: FireproofWebsiteEntity) : Command()
        object AskToDisableLoginDetection : Command()
        class AskToFireproofWebsite(val fireproofWebsite: FireproofWebsiteEntity) : Command()
        class ShareLink(val url: String) : Command()
        class CopyLink(val url: String) : Command()
        class FindInPageCommand(val searchTerm: String) : Command()
        class BrokenSiteFeedback(val data: BrokenSiteData) : Command()
        object DismissFindInPage : Command()
        class ShowFileChooser(val filePathCallback: ValueCallback<Array<Uri>>, val fileChooserParams: WebChromeClient.FileChooserParams) : Command()
        class HandleNonHttpAppLink(val nonHttpAppLink: NonHttpAppLink, val headers: Map<String, String>) : Command()
        class HandleAppLink(val appLink: AppLink, val headers: Map<String, String>) : Command()
        class AddHomeShortcut(val title: String, val url: String, val icon: Bitmap? = null) : Command()
        class LaunchSurvey(val survey: Survey) : Command()
        object LaunchAddWidget : Command()
        object LaunchLegacyAddWidget : Command()
        class RequiresAuthentication(val request: BasicAuthenticationRequest) : Command()
        class SaveCredentials(val request: BasicAuthenticationRequest, val credentials: BasicAuthenticationCredentials) : Command()
        object GenerateWebViewPreviewImage : Command()
        object LaunchTabSwitcher : Command()
        object HideWebContent : Command()
        object ShowWebContent : Command()
        class CheckSystemLocationPermission(val domain: String, val deniedForever: Boolean) : Command()
        class AskDomainPermission(val domain: String) : Command()
        object RequestSystemLocationPermission : Command()
        class RefreshUserAgent(val url: String?, val isDesktop: Boolean) : Command()
        class ShowErrorWithAction(val textResId: Int, val action: () -> Unit) : Command()
        class ShowDomainHasPermissionMessage(val domain: String) : Command()
        class ConvertBlobToDataUri(val url: String, val mimeType: String) : Command()
        class RequestFileDownload(val url: String, val contentDisposition: String?, val mimeType: String, val requestUserConfirmation: Boolean) : Command()
        object ChildTabClosed : Command()
        class CopyAliasToClipboard(val alias: String) : Command()
        class InjectEmailAddress(val address: String) : Command()
        class ShowEmailTooltip(val address: String) : Command()
        sealed class DaxCommand : Command() {
            object FinishTrackerAnimation : DaxCommand()
            class HideDaxDialog(val cta: Cta) : DaxCommand()
        }

        sealed class DownloadCommand : Command() {
            class ScanMediaFiles(val file: File) : DownloadCommand()
            class ShowDownloadFailedNotification(val message: String, val reason: DownloadFailReason) : DownloadCommand()
            class ShowDownloadFinishedNotification(val file: File, val mimeType: String?) : DownloadCommand()
            object ShowDownloadInProgressNotification : DownloadCommand()
        }
        class EditWithSelectedQuery(val query: String) : Command()
    }

    val autoCompleteViewState: MutableLiveData<AutoCompleteViewState> = MutableLiveData()
    val browserViewState: MutableLiveData<BrowserViewState> = MutableLiveData()
    val globalLayoutState: MutableLiveData<GlobalLayoutViewState> = MutableLiveData()
    val loadingViewState: MutableLiveData<LoadingViewState> = MutableLiveData()
    val omnibarViewState: MutableLiveData<OmnibarViewState> = MutableLiveData()
    val findInPageViewState: MutableLiveData<FindInPageViewState> = MutableLiveData()
    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    var siteLiveData: MutableLiveData<Site> = MutableLiveData()
    val privacyGradeViewState: MutableLiveData<PrivacyGradeViewState> = MutableLiveData()

    var skipHome = false
    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val survey: LiveData<Survey> = ctaViewModel.surveyLiveData
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

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
        context = viewModelScope.coroutineContext
    )

    private var autoCompleteDisposable: Disposable? = null
    private var site: Site? = null
    private lateinit var tabId: String
    private var webNavigationState: WebNavigationState? = null
    private var httpsUpgraded = false
    private val browserStateModifier = BrowserStateModifier()
    private var faviconPrefetchJob: Job? = null
    private var deferredBlankSite: Job? = null

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
            if (!isFireproofWebsite(loginEvent.forwardedToDomain)) {
                withContext(dispatchers.main()) {
                    command.value = AskToFireproofWebsite(FireproofWebsiteEntity(loginEvent.forwardedToDomain))
                }
            }
        }
    }

    init {
        initializeViewStates()
        configureAutoComplete()
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
        }.launchIn(viewModelScope)

        favoritesRepository.favorites().onEach { favorite ->
            val favorites = favorite.map { FavoritesQuickAccessAdapter.QuickAccessFavorite(it) }
            ctaViewState.value = currentCtaViewState().copy(favorites = favorites)
            autoCompleteViewState.value = currentAutoCompleteViewState().copy(favorites = favorites)
        }.launchIn(viewModelScope)
    }

    fun loadData(tabId: String, initialUrl: String?, skipHome: Boolean, favoritesOnboarding: Boolean) {
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

    fun onMessageProcessed() {
        showBrowser()
    }

    private fun buildSiteFactory(url: String, title: String? = null) {

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
                { t: Throwable? -> Timber.w(t, "Failed to get search results") }
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

    fun registerWebViewListener(browserWebViewClient: BrowserWebViewClient, browserChromeClient: BrowserChromeClient) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onViewResumed() {
        if (currentGlobalLayoutState() is Invalidated && currentBrowserViewState().browserShowing) {
            showErrorWithAction()
        }
    }

    fun onViewVisible() {
        // we expect refreshCta to be called when a site is fully loaded if browsingShowing -trackers data available-.
        if (!currentBrowserViewState().browserShowing) {
            viewModelScope.launch {
                val cta = refreshCta()
                showOrHideKeyboard(cta) // we hide the keyboard when showing a DialogCta and HomeCta type in the home screen otherwise we show it
            }
        } else {
            command.value = HideKeyboard
        }
    }

    fun onViewHidden() {
        skipHome = false
    }

    suspend fun fireAutocompletePixel(suggestion: AutoCompleteSuggestion) {
        val currentViewState = currentAutoCompleteViewState()
        val hasBookmarks = withContext(dispatchers.io()) {
            bookmarksDao.hasBookmarks()
        }
        val hasBookmarkResults = currentViewState.searchResults.suggestions.any { it is AutoCompleteBookmarkSuggestion }
        val params = mapOf(
            PixelParameter.SHOWED_BOOKMARKS to hasBookmarkResults.toString(),
            PixelParameter.BOOKMARK_CAPABLE to hasBookmarks.toString()
        )
        val pixelName = when (suggestion) {
            is AutoCompleteBookmarkSuggestion -> AppPixelName.AUTOCOMPLETE_BOOKMARK_SELECTION
            is AutoCompleteSearchSuggestion -> AppPixelName.AUTOCOMPLETE_SEARCH_SELECTION
        }

        pixel.fire(pixelName, params)
    }

    fun onUserSubmittedQuery(query: String, queryOrigin: QueryOrigin = QueryOrigin.FromUser) {
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
        val urlToNavigate = queryUrlConverter.convertQueryToUrl(trimmedInput, verticalParameter, queryOrigin)

        val type = specialUrlDetector.determineType(trimmedInput)
        if (type is NonHttpAppLink) {
            nonHttpAppLinkClicked(type)
        } else {
            if (shouldClearHistoryOnNewQuery()) {
                command.value = ResetHistory
            }

            fireQueryChangedPixel(trimmedInput)

            appLinksHandler.userEnteredBrowserState()
            command.value = Navigate(urlToNavigate, getUrlHeaders())
        }

        globalLayoutState.value = Browser(isNewTabState = false)
        findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = true)
        omnibarViewState.value = currentOmnibarViewState().copy(omnibarText = trimmedInput, shouldMoveCaretToEnd = false)
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true, showClearButton = false)
        autoCompleteViewState.value = currentAutoCompleteViewState().copy(showSuggestions = false, showFavorites = false, searchResults = AutoCompleteResult("", emptyList()))
    }

    private fun getUrlHeaders(): Map<String, String> = globalPrivacyControl.getHeaders()

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

    override fun iconReceived(url: String, icon: Bitmap) {
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

    override fun iconReceived(visitedUrl: String, iconUrl: String) {
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
            findInPageViewState.value = currentFindInPageViewState().copy(canFindInPage = true)
            command.value = Refresh
        } else {
            command.value = NavigateForward
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
            command.value = Refresh
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
            command.value = NavigateBack(navigation.stepsToPreviousPage)
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
            canGoForward = currentGlobalLayoutState() !is Invalidated
        )
        browserViewState.value = browserState

        findInPageViewState.value = FindInPageViewState()
        omnibarViewState.value = currentOmnibarViewState().copy(omnibarText = "", shouldMoveCaretToEnd = false)
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

        browserViewState.value = currentBrowserViewState().copy(
            canGoBack = newWebNavigationState.canGoBack || !skipHome,
            canGoForward = newWebNavigationState.canGoForward
        )

        Timber.v("navigationStateChanged: $stateChange")
        when (stateChange) {
            is NewPage -> pageChanged(stateChange.url, stateChange.title)
            is PageCleared -> pageCleared()
            is UrlUpdated -> urlUpdated(stateChange.url)
            is PageNavigationCleared -> disableUserNavigation()
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

    private fun pageChanged(url: String, title: String?) {
        Timber.v("Page changed: $url")
        buildSiteFactory(url, title)

        val currentOmnibarViewState = currentOmnibarViewState()
        omnibarViewState.value = currentOmnibarViewState.copy(omnibarText = omnibarTextForUrl(url), shouldMoveCaretToEnd = false)
        val currentBrowserViewState = currentBrowserViewState()
        val domain = site?.domain
        val canWhitelist = domain != null
        val canFireproofSite = domain != null
        val addFavorite = if (!currentBrowserViewState.addFavorite.isEnabled()) {
            HighlightableButton.Visible(enabled = true)
        } else {
            currentBrowserViewState.addFavorite
        }
        findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = true)

        browserViewState.value = currentBrowserViewState.copy(
            browserShowing = true,
            canAddBookmarks = true,
            addFavorite = addFavorite,
            addToHomeEnabled = true,
            addToHomeVisible = addToHomeCapabilityDetector.isAddToHomeSupported(),
            canSharePage = true,
            showPrivacyGrade = true,
            canReportSite = true,
            canWhitelist = canWhitelist,
            isWhitelisted = false,
            showSearchIcon = false,
            showClearButton = false,
            canFireproofSite = canFireproofSite,
            isFireproofWebsite = isFireproofWebsite(),
            showDaxIcon = shouldShowDaxIcon(url, true)
        )

        Timber.d("showPrivacyGrade=true, showSearchIcon=false, showClearButton=false")

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            statisticsUpdater.refreshSearchRetentionAtb()
        }

        domain?.let { viewModelScope.launch { updateLoadingStatePrivacy(domain) } }
        domain?.let { viewModelScope.launch { updateWhitelistedState(domain) } }

        val permissionOrigin = site?.uri?.host?.asLocationPermissionOrigin()
        permissionOrigin?.let { viewModelScope.launch { notifyPermanentLocationPermission(permissionOrigin) } }

        registerSiteVisit()
    }

    private fun shouldShowDaxIcon(currentUrl: String?, showPrivacyGrade: Boolean): Boolean {
        val url = currentUrl ?: return false
        return showPrivacyGrade && duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
    }

    private suspend fun updateLoadingStatePrivacy(domain: String) {
        val isWhitelisted = isWhitelisted(domain)
        withContext(dispatchers.main()) {
            loadingViewState.value = currentLoadingViewState().copy(privacyOn = !isWhitelisted)
        }
    }

    private suspend fun updateWhitelistedState(domain: String) {
        val isWhitelisted = isWhitelisted(domain)
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isWhitelisted = isWhitelisted)
        }
    }

    private suspend fun isWhitelisted(domain: String): Boolean {
        return withContext(dispatchers.io()) {
            userWhitelistDao.contains(domain) || temporaryTrackingWhitelistDao.contains(domain)
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
        omnibarViewState.postValue(currentOmnibarViewState.copy(omnibarText = omnibarTextForUrl(url), shouldMoveCaretToEnd = false))
        browserViewState.postValue(currentBrowserViewState().copy(isFireproofWebsite = isFireproofWebsite()))
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
            canAddBookmarks = false,
            addFavorite = HighlightableButton.Visible(enabled = false),
            addToHomeEnabled = false,
            addToHomeVisible = addToHomeCapabilityDetector.isAddToHomeSupported(),
            canSharePage = false,
            showPrivacyGrade = false,
            canReportSite = false,
            showSearchIcon = true,
            showClearButton = true,
            canFireproofSite = false,
            showDaxIcon = false
        )
        Timber.d("showPrivacyGrade=false, showSearchIcon=true, showClearButton=true")
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

        val isLoading = newProgress < 100
        val progress = currentLoadingViewState()
        if (progress.progress == newProgress) return
        val visualProgress = if (newProgress < FIXED_PROGRESS) {
            FIXED_PROGRESS
        } else {
            newProgress
        }

        loadingViewState.value = progress.copy(isLoading = isLoading, progress = visualProgress)

        val showLoadingGrade = progress.privacyOn || isLoading
        privacyGradeViewState.value = currentPrivacyGradeState().copy(shouldAnimate = isLoading, showEmptyGrade = showLoadingGrade)

        if (newProgress == 100) {
            command.value = RefreshUserAgent(url, currentBrowserViewState().isDesktopBrowsingMode)
            navigationAwareLoginDetector.onEvent(NavigationEvent.PageFinished)
        }
    }

    override fun onSiteLocationPermissionRequested(origin: String, callback: GeolocationPermissions.Callback) {
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

    override fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType) {
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

    override fun onSystemLocationPermissionAllowed() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_ENABLE)
        command.postValue(RequestSystemLocationPermission)
    }

    override fun onSystemLocationPermissionNotAllowed() {
        pixel.fire(AppPixelName.PRECISE_LOCATION_SYSTEM_DIALOG_LATER)
        onSiteLocationPermissionAlwaysDenied()
    }

    override fun onSystemLocationPermissionNeverAllowed() {
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

    fun onSystemLocationPermissionDeniedForever() {
        appSettingsPreferencesStore.appLocationPermissionDeniedForever = true
        onSystemLocationPermissionDeniedOneTime()
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

    private fun onSiteChanged() {
        httpsUpgraded = false
        viewModelScope.launch {

            val improvedGrade = withContext(dispatchers.io()) {
                site?.calculateGrades()?.improvedGrade
            }

            withContext(dispatchers.main()) {
                siteLiveData.value = site
                val isWhiteListed: Boolean = site?.domain?.let { isWhitelisted(it) } ?: false
                if (!isWhiteListed) {
                    privacyGradeViewState.value = currentPrivacyGradeState().copy(privacyGrade = improvedGrade)
                }
            }

            withContext(dispatchers.io()) {
                tabRepository.update(tabId, site)
            }
        }
    }

    fun stopShowingEmptyGrade() {
        if (currentPrivacyGradeState().showEmptyGrade) {
            privacyGradeViewState.value = currentPrivacyGradeState().copy(showEmptyGrade = false)
        }
    }

    override fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams) {
        command.value = ShowFileChooser(filePathCallback, fileChooserParams)
    }

    private fun currentGlobalLayoutState(): GlobalLayoutViewState = globalLayoutState.value!!
    private fun currentAutoCompleteViewState(): AutoCompleteViewState = autoCompleteViewState.value!!
    private fun currentBrowserViewState(): BrowserViewState = browserViewState.value!!
    private fun currentFindInPageViewState(): FindInPageViewState = findInPageViewState.value!!
    private fun currentOmnibarViewState(): OmnibarViewState = omnibarViewState.value!!
    private fun currentLoadingViewState(): LoadingViewState = loadingViewState.value!!
    private fun currentCtaViewState(): CtaViewState = ctaViewState.value!!
    private fun currentPrivacyGradeState(): PrivacyGradeViewState = privacyGradeViewState.value!!

    fun onOmnibarInputStateChanged(query: String, hasFocus: Boolean, hasQueryChanged: Boolean) {

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
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()
        val showPrivacyGrade = !hasFocus
        val showSearchIcon = hasFocus

        // show the real grade in case the animation was canceled before changing the state, this avoids showing an empty grade when regaining focus.
        if (showPrivacyGrade) {
            privacyGradeViewState.value = currentPrivacyGradeState().copy(showEmptyGrade = false)
        }

        omnibarViewState.value = currentOmnibarViewState().copy(isEditing = hasFocus)

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            showPrivacyGrade = showPrivacyGrade,
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
            showDaxIcon = shouldShowDaxIcon(url, showPrivacyGrade)
        )

        Timber.d("showPrivacyGrade=$showPrivacyGrade, showSearchIcon=$showSearchIcon, showClearButton=$showClearButton")

        autoCompleteViewState.value = currentAutoCompleteViewState()
            .copy(showSuggestions = showAutoCompleteSuggestions, showFavorites = showFavoritesAsSuggestions, searchResults = autoCompleteSearchResults)

        if (hasQueryChanged && hasFocus && autoCompleteSuggestionsEnabled) {
            autoCompletePublishSubject.accept(query.trim())
        }
    }

    suspend fun onBookmarkAddRequested() {
        val url = url ?: return
        val title = title ?: ""
        val savedBookmark = withContext(dispatchers.io()) {
            if (url.isNotBlank()) {
                faviconManager.persistCachedFavicon(tabId, url)
            }
            val bookmarkEntity = BookmarkEntity(title = title, url = url, parentId = 0)
            val id = bookmarksDao.insert(bookmarkEntity)
            SavedSite.Bookmark(id, title, url, 0)
        }
        withContext(dispatchers.main()) {
            command.value = ShowSavedSiteAddedConfirmation(savedBookmark)
        }
    }

    fun onAddFavoriteMenuClicked() {
        val url = url ?: return
        val title = title ?: ""

        val buttonHighlighted = currentBrowserViewState().addFavorite.isHighlighted()
        pixel.fire(
            AppPixelName.MENU_ACTION_ADD_FAVORITE_PRESSED.pixelName,
            mapOf(FAVORITE_MENU_ITEM_STATE to buttonHighlighted.toString())
        )

        viewModelScope.launch {
            withContext(dispatchers.io()) {
                if (url.isNotBlank()) {
                    faviconManager.persistCachedFavicon(tabId, url)
                    favoritesRepository.insert(title = title, url = url)
                } else null
            }?.let {
                withContext(dispatchers.main()) {
                    command.value = ShowSavedSiteAddedConfirmation(it)
                }
            }
        }
    }

    fun onFireproofWebsiteMenuClicked() {
        val domain = site?.domain ?: return
        viewModelScope.launch {
            if (currentBrowserViewState().isFireproofWebsite) {
                fireproofWebsiteRepository.removeFireproofWebsite(FireproofWebsiteEntity(domain))
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

    override fun onSavedSiteEdited(savedSite: SavedSite) {
        when (savedSite) {
            is SavedSite.Bookmark -> {
                viewModelScope.launch(dispatchers.io()) {
                    editBookmark(savedSite)
                }
            }
            is SavedSite.Favorite -> {
                viewModelScope.launch(dispatchers.io()) {
                    editFavorite(savedSite)
                }
            }
        }
    }

    fun onEditSavedSiteRequested(savedSite: SavedSite) {
        command.value = ShowEditSavedSiteDialog(savedSite)
    }

    fun onDeleteQuickAccessItemRequested(savedSite: SavedSite) {
        command.value = DeleteSavedSiteConfirmation(savedSite)
    }

    private suspend fun editBookmark(bookmark: SavedSite.Bookmark) {
        withContext(dispatchers.io()) {
            bookmarksDao.update(BookmarkEntity(bookmark.id, bookmark.title, bookmark.url, bookmark.parentId))
        }
    }

    private suspend fun editFavorite(favorite: SavedSite.Favorite) {
        withContext(dispatchers.io()) {
            favoritesRepository.update(favorite)
        }
    }

    fun onBrokenSiteSelected() {
        command.value = BrokenSiteFeedback(BrokenSiteData.fromSite(site))
    }

    fun onWhitelistSelected() {
        val domain = site?.domain ?: return
        appCoroutineScope.launch(dispatchers.io()) {
            if (isWhitelisted(domain)) {
                removeFromWhitelist(domain)
            } else {
                addToWhitelist(domain)
            }
            command.postValue(Refresh)
        }
    }

    private suspend fun addToWhitelist(domain: String) {
        pixel.fire(AppPixelName.BROWSER_MENU_WHITELIST_ADD)
        withContext(dispatchers.io()) {
            userWhitelistDao.insert(domain)
        }
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isWhitelisted = true)
        }
    }

    private suspend fun removeFromWhitelist(domain: String) {
        pixel.fire(AppPixelName.BROWSER_MENU_WHITELIST_REMOVE)
        withContext(dispatchers.io()) {
            userWhitelistDao.delete(domain)
        }
        withContext(dispatchers.main()) {
            browserViewState.value = currentBrowserViewState().copy(isWhitelisted = false)
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = EditWithSelectedQuery(query)
    }

    fun userLongPressedInWebView(target: LongPressTarget, menu: ContextMenu) {
        Timber.i("Long pressed on ${target.type}, (url=${target.url}), (image url = ${target.imageUrl})")
        longPressHandler.handleLongPress(target.type, target.url, menu)
    }

    fun userSelectedItemFromLongPressMenu(longPressTarget: LongPressTarget, item: MenuItem): Boolean {

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
        findInPageViewState.value = FindInPageViewState(visible = true, canFindInPage = true)
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

    fun onFindResultsReceived(activeMatchOrdinal: Int, numberOfMatches: Int) {
        val activeIndex = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
        val currentViewState = currentFindInPageViewState()
        findInPageViewState.value = currentViewState.copy(
            showNumberMatches = true,
            activeMatchIndex = activeIndex,
            numberMatches = numberOfMatches
        )
    }

    fun onWebSessionRestored() {
        globalLayoutState.value = Browser(isNewTabState = false)
    }

    fun onDesktopSiteModeToggled(desktopSiteRequested: Boolean) {
        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(isDesktopBrowsingMode = desktopSiteRequested)
        command.value = RefreshUserAgent(site?.uri?.toString(), desktopSiteRequested)

        val uri = site?.uri ?: return

        pixel.fire(
            if (desktopSiteRequested) AppPixelName.MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED
            else AppPixelName.MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED
        )

        if (desktopSiteRequested && uri.isMobileSite) {
            val desktopUrl = uri.toDesktopUri().toString()
            Timber.i("Original URL $url - attempting $desktopUrl with desktop site UA string")
            command.value = Navigate(desktopUrl, getUrlHeaders())
        } else {
            command.value = Refresh
        }
    }

    private fun initializeViewStates() {
        globalLayoutState.value = Browser()
        browserViewState.value = BrowserViewState().copy(addToHomeVisible = addToHomeCapabilityDetector.isAddToHomeSupported())
        loadingViewState.value = LoadingViewState()
        autoCompleteViewState.value = AutoCompleteViewState()
        omnibarViewState.value = OmnibarViewState()
        findInPageViewState.value = FindInPageViewState()
        ctaViewState.value = CtaViewState()
        privacyGradeViewState.value = PrivacyGradeViewState()
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

    fun saveWebViewState(webView: WebView?, tabId: String) {
        webViewSessionStorage.saveSession(webView, tabId)
    }

    fun restoreWebViewState(webView: WebView?, lastUrl: String) {
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
            browserViewState.value = currentBrowserViewState().copy(showMenuButton = HighlightableButton.Visible(highlighted = false), addFavorite = HighlightableButton.Visible(highlighted = true))
        }
    }

    fun onBrowserMenuClosed() {
        viewModelScope.launch {
            Timber.i("favoritesOnboarding onBrowserMenuClosed")
            if (currentBrowserViewState().addFavorite.isHighlighted()) {
                browserViewState.value = currentBrowserViewState().copy(
                    addFavorite = HighlightableButton.Visible(highlighted = false)
                )
            }
        }
    }

    fun userRequestedOpeningNewTab() {
        command.value = GenerateWebViewPreviewImage
        command.value = LaunchNewTab
    }

    fun onSurveyChanged(survey: Survey?, locale: Locale = Locale.getDefault()) {
        val surveyCleared = ctaViewModel.onSurveyChanged(survey)
        if (surveyCleared) {
            ctaViewState.value = currentCtaViewState().copy(cta = null)
            return
        }
        if (survey != null) {
            viewModelScope.launch {
                refreshCta(locale)
            }
        }
    }

    fun onCtaShown() {
        val cta = ctaViewState.value?.cta ?: return
        ctaViewModel.onCtaShown(cta)
    }

    suspend fun refreshCta(locale: Locale = Locale.getDefault()): Cta? {
        Timber.i("favoritesOnboarding: - refreshCta $showFavoritesOnboarding")
        if (currentGlobalLayoutState() is Browser) {
            val cta = withContext(dispatchers.io()) {
                ctaViewModel.refreshCta(dispatchers.io(), currentBrowserViewState().browserShowing, siteLiveData.value, showFavoritesOnboarding, locale)
            }
            ctaViewState.value = currentCtaViewState().copy(cta = cta)
            return cta
        }
        return null
    }

    private fun showOrHideKeyboard(cta: Cta?) {
        command.value = if (cta is DialogCta || cta is HomePanelCta) HideKeyboard else ShowKeyboard
    }

    fun registerDaxBubbleCtaDismissed() {
        viewModelScope.launch {
            val cta = ctaViewState.value?.cta ?: return@launch
            ctaViewModel.registerDaxBubbleCtaDismissed(cta)
        }
    }

    fun onUserClickCtaOkButton() {
        val cta = currentCtaViewState().cta ?: return
        ctaViewModel.onUserClickCtaOkButton(cta)
        command.value = when (cta) {
            is HomePanelCta.Survey -> LaunchSurvey(cta.survey)
            is HomePanelCta.AddWidgetAuto -> LaunchAddWidget
            is HomePanelCta.AddWidgetInstructions -> LaunchLegacyAddWidget
            else -> return
        }
    }

    fun onUserClickCtaSecondaryButton() {
        viewModelScope.launch {
            val cta = currentCtaViewState().cta ?: return@launch
            ctaViewModel.onUserDismissedCta(cta)
        }
    }

    fun onUserHideDaxDialog() {
        val cta = currentCtaViewState().cta ?: return
        command.value = DaxCommand.HideDaxDialog(cta)
    }

    fun onDaxDialogDismissed() {
        val cta = currentCtaViewState().cta ?: return
        if (cta is DaxDialogCta.DaxTrackersBlockedCta) {
            command.value = DaxCommand.FinishTrackerAnimation
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

    fun updateTabPreview(tabId: String, fileName: String) {
        tabRepository.updateTabPreviewImage(tabId, fileName)
    }

    fun deleteTabPreview(tabId: String) {
        tabRepository.updateTabPreviewImage(tabId, null)
    }

    override fun handleAppLink(appLink: AppLink, isRedirect: Boolean, isForMainFrame: Boolean): Boolean {
        return appLinksHandler.handleAppLink(isRedirect, isForMainFrame) { appLinkClicked(appLink) }
    }

    override fun resetAppLinkState() {
        appLinksHandler.reset()
    }

    fun navigateToAppLinkInBrowser(url: String, headers: Map<String, String>) {
        appLinksHandler.enterBrowserState()
        command.value = Navigate(url, headers)
    }

    fun appLinkClicked(appLink: AppLink) {
        command.value = HandleAppLink(appLink, getUrlHeaders())
    }

    override fun handleNonHttpAppLink(nonHttpAppLink: NonHttpAppLink, isRedirect: Boolean): Boolean {
        return appLinksHandler.handleNonHttpAppLink(isRedirect) { nonHttpAppLinkClicked(nonHttpAppLink) }
    }

    fun nonHttpAppLinkClicked(appLink: NonHttpAppLink) {
        command.value = HandleNonHttpAppLink(appLink, getUrlHeaders())
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
            omnibarViewState.value = currentOmnibarViewState().copy(omnibarText = request.site)
            command.value = HideWebContent
        }
        command.value = RequiresAuthentication(request)
    }

    override fun handleAuthentication(request: BasicAuthenticationRequest, credentials: BasicAuthenticationCredentials) {
        request.handler.proceed(credentials.username, credentials.password)
        command.value = ShowWebContent
        command.value = SaveCredentials(request, credentials)
    }

    override fun cancelAuthentication(request: BasicAuthenticationRequest) {
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
            canFireproofSite = false
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

    fun requestFileDownload(url: String, contentDisposition: String?, mimeType: String, requestUserConfirmation: Boolean) {
        if (url.startsWith("blob:")) {
            command.value = ConvertBlobToDataUri(url, mimeType)
        } else {
            sendRequestFileDownloadCommand(url, contentDisposition, mimeType, requestUserConfirmation)
        }
    }

    private fun sendRequestFileDownloadCommand(url: String, contentDisposition: String?, mimeType: String, requestUserConfirmation: Boolean) {
        command.postValue(RequestFileDownload(url, contentDisposition, mimeType, requestUserConfirmation))
    }

    fun showEmailTooltip() {
        emailManager.getEmailAddress()?.let {
            command.postValue(ShowEmailTooltip(it))
        }
    }

    fun consumeAliasAndCopyToClipboard() {
        emailManager.getAlias()?.let {
            command.value = CopyAliasToClipboard(it)
        }
    }

    fun consumeAlias() {
        emailManager.getAlias()?.let {
            command.postValue(InjectEmailAddress(it))
            pixel.enqueueFire(AppPixelName.EMAIL_USE_ALIAS, mapOf(PixelParameter.COHORT to emailManager.getCohort()))
        }
    }

    fun useAddress() {
        emailManager.getEmailAddress()?.let {
            command.postValue(InjectEmailAddress(it))
            pixel.enqueueFire(AppPixelName.EMAIL_USE_ADDRESS, mapOf(PixelParameter.COHORT to emailManager.getCohort()))
        }
    }

    fun cancelAutofillTooltip() {
        pixel.enqueueFire(AppPixelName.EMAIL_TOOLTIP_DISMISSED, mapOf(PixelParameter.COHORT to emailManager.getCohort()))
    }

    fun download(pendingFileDownload: FileDownloader.PendingFileDownload) {
        viewModelScope.launch(dispatchers.io()) {
            fileDownloader.download(
                pendingFileDownload,
                object : FileDownloader.FileDownloadListener {

                    override fun downloadStartedNetworkFile() {
                        Timber.d("download started: network file")
                        closeAndReturnToSourceIfBlankTab()
                    }

                    override fun downloadFinishedNetworkFile(file: File, mimeType: String?) {
                        Timber.i("downloadFinished network file")
                    }

                    override fun downloadStartedDataUri() {
                        Timber.i("downloadStarted data uri")
                        command.postValue(DownloadCommand.ShowDownloadInProgressNotification)
                        closeAndReturnToSourceIfBlankTab()
                    }

                    override fun downloadFinishedDataUri(file: File, mimeType: String?) {
                        Timber.i("downloadFinished data uri")
                        command.postValue(DownloadCommand.ScanMediaFiles(file))
                        command.postValue(DownloadCommand.ShowDownloadFinishedNotification(file, mimeType))
                    }

                    override fun downloadFailed(message: String, downloadFailReason: DownloadFailReason) {
                        Timber.w("Failed to download file [$message]")
                        command.postValue(DownloadCommand.ShowDownloadFailedNotification(message, downloadFailReason))
                    }

                    override fun downloadCancelled() {
                        Timber.i("Download cancelled")
                        closeAndReturnToSourceIfBlankTab()
                    }

                    override fun downloadOpened() {
                        closeAndReturnToSourceIfBlankTab()
                    }
                }
            )
        }
    }

    fun deleteQuickAccessItem(savedSite: SavedSite) {
        val favorite = savedSite as? SavedSite.Favorite ?: return
        viewModelScope.launch(dispatchers.io() + NonCancellable) {
            favoritesRepository.delete(favorite)
        }
    }

    fun insertQuickAccessItem(savedSite: SavedSite) {
        val favorite = savedSite as? SavedSite.Favorite ?: return
        viewModelScope.launch(dispatchers.io()) {
            favoritesRepository.insert(favorite)
        }
    }

    fun onQuickAccessListChanged(newList: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
        viewModelScope.launch(dispatchers.io()) {
            favoritesRepository.updateWithPosition(newList.map { it.favorite })
        }
    }

    companion object {
        private const val FIXED_PROGRESS = 50

        // Minimum progress to show web content again after decided to hide web content (possible spoofing attack).
        // We think that progress is enough to assume next site has already loaded new content.
        private const val SHOW_CONTENT_MIN_PROGRESS = 50
        private const val NEW_CONTENT_MAX_DELAY_MS = 1000L
        private const val ONE_HOUR_IN_MS = 3_600_000
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BrowserTabViewModelFactory @Inject constructor(
    private val statisticsUpdater: Provider<StatisticsUpdater>,
    private val queryUrlConverter: Provider<QueryUrlConverter>,
    private val duckDuckGoUrlDetector: Provider<DuckDuckGoUrlDetector>,
    private val siteFactory: Provider<SiteFactory>,
    private val tabRepository: Provider<TabRepository>,
    private val userWhitelistDao: Provider<UserWhitelistDao>,
    private val temporaryTrackingWhitelistDao: Provider<TemporaryTrackingWhitelistDao>,
    private val networkLeaderboardDao: Provider<NetworkLeaderboardDao>,
    private val bookmarksDao: Provider<BookmarksDao>,
    private val favoritesRepository: Provider<FavoritesRepository>,
    private val fireproofWebsiteRepository: Provider<FireproofWebsiteRepository>,
    private val locationPermissionsRepository: Provider<LocationPermissionsRepository>,
    private val geoLocationPermissions: Provider<GeoLocationPermissions>,
    private val navigationAwareLoginDetector: Provider<NavigationAwareLoginDetector>,
    private val autoComplete: Provider<AutoCompleteApi>,
    private val appSettingsPreferencesStore: Provider<SettingsDataStore>,
    private val longPressHandler: Provider<LongPressHandler>,
    private val webViewSessionStorage: Provider<WebViewSessionStorage>,
    private val specialUrlDetector: Provider<SpecialUrlDetector>,
    private val faviconManager: Provider<FaviconManager>,
    private val addToHomeCapabilityDetector: Provider<AddToHomeCapabilityDetector>,
    private val ctaViewModel: Provider<CtaViewModel>,
    private val searchCountDao: Provider<SearchCountDao>,
    private val pixel: Provider<Pixel>,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    private val userEventsStore: Provider<UserEventsStore>,
    private val fileDownloader: Provider<FileDownloader>,
    private val globalPrivacyControl: Provider<GlobalPrivacyControl>,
    private val fireproofDialogsEventHandler: Provider<FireproofDialogsEventHandler>,
    private val emailManager: Provider<EmailManager>,
    private val appCoroutineScope: Provider<CoroutineScope>,
    private val appLinksHandler: Provider<DuckDuckGoAppLinksHandler>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BrowserTabViewModel::class.java) -> BrowserTabViewModel(statisticsUpdater.get(), queryUrlConverter.get(), duckDuckGoUrlDetector.get(), siteFactory.get(), tabRepository.get(), userWhitelistDao.get(), temporaryTrackingWhitelistDao.get(), networkLeaderboardDao.get(), bookmarksDao.get(), favoritesRepository.get(), fireproofWebsiteRepository.get(), locationPermissionsRepository.get(), geoLocationPermissions.get(), navigationAwareLoginDetector.get(), autoComplete.get(), appSettingsPreferencesStore.get(), longPressHandler.get(), webViewSessionStorage.get(), specialUrlDetector.get(), faviconManager.get(), addToHomeCapabilityDetector.get(), ctaViewModel.get(), searchCountDao.get(), pixel.get(), dispatchers, userEventsStore.get(), fileDownloader.get(), globalPrivacyControl.get(), fireproofDialogsEventHandler.get(), emailManager.get(), appCoroutineScope.get(), appLinksHandler.get()) as T
                else -> null
            }
        }
    }
}
