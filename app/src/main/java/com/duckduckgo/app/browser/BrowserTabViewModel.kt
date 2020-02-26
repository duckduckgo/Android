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
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.EditBookmarkDialogFragment.EditBookmarkListener
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.*
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Browser
import com.duckduckgo.app.browser.BrowserTabViewModel.GlobalLayoutViewState.Invalidated
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.IntentType
import com.duckduckgo.app.browser.WebNavigationStateChange.*
import com.duckduckgo.app.browser.addtohome.AddToHomeCapabilityDetector
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.model.LongPressTarget
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.browser.ui.HttpAuthenticationDialogFragment.HttpAuthenticationListener
import com.duckduckgo.app.cta.ui.Cta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.cta.ui.HomePanelCta
import com.duckduckgo.app.global.*
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.model.domainMatchesUrl
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.app.usage.search.SearchCountDao
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class BrowserTabViewModel(
    private val statisticsUpdater: StatisticsUpdater,
    private val queryUrlConverter: OmnibarEntryConverter,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val siteFactory: SiteFactory,
    private val tabRepository: TabRepository,
    private val networkLeaderboardDao: NetworkLeaderboardDao,
    private val bookmarksDao: BookmarksDao,
    private val autoComplete: AutoComplete,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val longPressHandler: LongPressHandler,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val specialUrlDetector: SpecialUrlDetector,
    private val faviconDownloader: FaviconDownloader,
    private val addToHomeCapabilityDetector: AddToHomeCapabilityDetector,
    private val ctaViewModel: CtaViewModel,
    private val searchCountDao: SearchCountDao,
    private val pixel: Pixel,
    private val variantManager: VariantManager,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider()
) : WebViewClientListener, EditBookmarkListener, HttpAuthenticationListener, ViewModel() {

    private var buildingSiteFactoryJob: Job? = null

    sealed class GlobalLayoutViewState {
        data class Browser(val isNewTabState: Boolean = true) : GlobalLayoutViewState()
        object Invalidated : GlobalLayoutViewState()
    }

    data class CtaViewState(
        val cta: Cta? = null
    )

    data class BrowserViewState(
        val browserShowing: Boolean = false,
        val isFullScreen: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val canChangeBrowsingMode: Boolean = true,
        val showPrivacyGrade: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val showFireButton: Boolean = true,
        val showMenuButton: Boolean = true,
        val canSharePage: Boolean = false,
        val canAddBookmarks: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val canReportSite: Boolean = true,
        val addToHomeEnabled: Boolean = false,
        val addToHomeVisible: Boolean = false
    )

    data class OmnibarViewState(
        val omnibarText: String = "",
        val isEditing: Boolean = false,
        val shouldMoveCaretToEnd: Boolean = false
    )

    data class LoadingViewState(
        val isLoading: Boolean = false,
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

    data class AutoCompleteViewState(
        val showSuggestions: Boolean = false,
        val searchResults: AutoCompleteResult = AutoCompleteResult("", emptyList())
    )

    sealed class Command {
        object Refresh : Command()
        class Navigate(val url: String) : Command()
        class NavigateBack(val steps: Int) : Command()
        object NavigateForward : Command()
        class OpenInNewTab(val query: String) : Command()
        class OpenMessageInNewTab(val message: Message) : Command()
        class OpenInNewBackgroundTab(val query: String) : Command()
        object LaunchNewTab : Command()
        object ResetHistory : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
        object ShowKeyboard : Command()
        object HideKeyboard : Command()
        class ShowFullScreen(val view: View) : Command()
        class DownloadImage(val url: String) : Command()
        class ShowBookmarkAddedConfirmation(val bookmarkId: Long, val title: String?, val url: String?) : Command()
        class ShareLink(val url: String) : Command()
        class CopyLink(val url: String) : Command()
        class FindInPageCommand(val searchTerm: String) : Command()
        class BrokenSiteFeedback(val url: String?) : Command()
        object DismissFindInPage : Command()
        class ShowFileChooser(val filePathCallback: ValueCallback<Array<Uri>>, val fileChooserParams: WebChromeClient.FileChooserParams) : Command()
        class HandleExternalAppLink(val appLink: IntentType) : Command()
        class AddHomeShortcut(val title: String, val url: String, val icon: Bitmap? = null) : Command()
        class LaunchSurvey(val survey: Survey) : Command()
        object LaunchAddWidget : Command()
        object LaunchLegacyAddWidget : Command()
        class RequiresAuthentication(val request: BasicAuthenticationRequest) : Command()
        class SaveCredentials(val request: BasicAuthenticationRequest, val credentials: BasicAuthenticationCredentials) : Command()
        object GenerateWebViewPreviewImage : Command()
        object LaunchTabSwitcher : Command()
        class ShowErrorWithAction(val action: () -> Unit) : Command()
    }

    val autoCompleteViewState: MutableLiveData<AutoCompleteViewState> = MutableLiveData()
    val browserViewState: MutableLiveData<BrowserViewState> = MutableLiveData()
    val globalLayoutState: MutableLiveData<GlobalLayoutViewState> = MutableLiveData()
    val loadingViewState: MutableLiveData<LoadingViewState> = MutableLiveData()
    val omnibarViewState: MutableLiveData<OmnibarViewState> = MutableLiveData()
    val findInPageViewState: MutableLiveData<FindInPageViewState> = MutableLiveData()
    val ctaViewState: MutableLiveData<CtaViewState> = MutableLiveData()
    var siteLiveData: MutableLiveData<Site> = MutableLiveData()

    var skipHome = false
    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val survey: LiveData<Survey> = ctaViewModel.surveyLiveData
    val privacyGrade: MutableLiveData<PrivacyGrade> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    val url: String?
        get() = site?.url

    val title: String?
        get() = site?.title

    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private var autoCompleteDisposable: Disposable? = null
    private var site: Site? = null
    private lateinit var tabId: String
    private var webNavigationState: WebNavigationState? = null

    init {
        initializeViewStates()
        configureAutoComplete()
    }

    fun loadData(tabId: String, initialUrl: String?, skipHome: Boolean) {
        this.tabId = tabId
        this.skipHome = skipHome
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

        site = siteFactory.buildSite(url, title)
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
            .subscribe({ result ->
                onAutoCompleteResultReceived(result)
            }, { t: Throwable? -> Timber.w(t, "Failed to get search results") })
    }

    private fun onAutoCompleteResultReceived(result: AutoCompleteResult) {
        val results = result.suggestions.take(6)
        val currentViewState = currentAutoCompleteViewState()
        autoCompleteViewState.value = currentViewState.copy(searchResults = AutoCompleteResult(result.query, results))
    }

    @VisibleForTesting
    public override fun onCleared() {
        buildingSiteFactoryJob?.cancel()
        autoCompleteDisposable?.dispose()
        autoCompleteDisposable = null
        super.onCleared()
    }

    fun registerWebViewListener(browserWebViewClient: BrowserWebViewClient, browserChromeClient: BrowserChromeClient) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onViewResumed() {
        command.value = if (!currentBrowserViewState().browserShowing) ShowKeyboard else HideKeyboard
        if (currentGlobalLayoutState() is Invalidated && currentBrowserViewState().browserShowing) {
            showErrorWithAction()
        }
    }

    fun onViewVisible() {
        //we expect refreshCta to be called when a site is fully loaded if browsingShowing -trackers data available-.
        if (!currentBrowserViewState().browserShowing) {
            refreshCta()
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
            is AutoCompleteBookmarkSuggestion -> PixelName.AUTOCOMPLETE_BOOKMARK_SELECTION
            is AutoCompleteSearchSuggestion -> PixelName.AUTOCOMPLETE_SEARCH_SELECTION
        }

        pixel.fire(pixelName, params)
    }

    fun onUserSubmittedQuery(query: String) {
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

        val type = specialUrlDetector.determineType(trimmedInput)
        if (type is IntentType) {
            externalAppLinkClicked(type)
        } else {
            if (shouldClearHistoryOnNewQuery()) {
                command.value = ResetHistory
            }
            command.value = Navigate(queryUrlConverter.convertQueryToUrl(trimmedInput))
        }

        globalLayoutState.value = Browser(isNewTabState = false)
        findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = true)
        omnibarViewState.value = currentOmnibarViewState().copy(omnibarText = trimmedInput, shouldMoveCaretToEnd = false)
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true, showClearButton = false)
        autoCompleteViewState.value = AutoCompleteViewState(false)
    }

    private fun shouldClearHistoryOnNewQuery(): Boolean {
        val navigation = webNavigationState ?: return false
        return !currentBrowserViewState().browserShowing && navigation.hasNavigationHistory
    }

    suspend fun closeCurrentTab() {
        val currentTab = tabRepository.liveSelectedTab.value
        currentTab?.let {
            tabRepository.delete(currentTab)
        }
    }

    fun onUserPressedForward() {
        if (!currentBrowserViewState().browserShowing) {
            browserViewState.value = currentBrowserViewState().copy(browserShowing = true)
            command.value = Refresh
        } else {
            command.value = NavigateForward
        }
    }

    fun onRefreshRequested() {
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
        val navigation = webNavigationState ?: return false

        if (!currentBrowserViewState().browserShowing) {
            return false
        }

        if (navigation.canGoBack) {
            command.value = NavigateBack(navigation.stepsToPreviousPage)
            return true
        } else if (!skipHome) {
            navigateHome()
            return true
        }

        Timber.d("User pressed back and tab is set to skip home; need to generate WebView preview now")
        command.value = GenerateWebViewPreviewImage
        return false
    }

    private fun navigateHome() {
        site = null
        onSiteChanged()

        browserViewState.value = currentBrowserViewState().copy(
            browserShowing = false,
            canGoBack = false,
            canGoForward = currentGlobalLayoutState() !is Invalidated
        )
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
    }

    private fun pageChanged(url: String, title: String?) {

        Timber.v("Page changed: $url")
        buildSiteFactory(url, title)

        val currentOmnibarViewState = currentOmnibarViewState()
        omnibarViewState.postValue(currentOmnibarViewState.copy(omnibarText = omnibarTextForUrl(url), shouldMoveCaretToEnd = false))

        val currentBrowserViewState = currentBrowserViewState()
        findInPageViewState.postValue(FindInPageViewState(visible = false, canFindInPage = true))
        browserViewState.postValue(
            currentBrowserViewState.copy(
                browserShowing = true,
                canAddBookmarks = true,
                addToHomeEnabled = true,
                addToHomeVisible = addToHomeCapabilityDetector.isAddToHomeSupported(),
                canSharePage = true,
                showPrivacyGrade = true
            )
        )

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            statisticsUpdater.refreshSearchRetentionAtb()
        }

        registerSiteVisit()
    }

    private fun urlUpdated(url: String) {
        Timber.v("Page url updated: $url")
        site?.url = url
        onSiteChanged()
        val currentOmnibarViewState = currentOmnibarViewState()
        omnibarViewState.postValue(currentOmnibarViewState.copy(omnibarText = omnibarTextForUrl(url), shouldMoveCaretToEnd = false))
    }

    private fun omnibarTextForUrl(url: String?): String {
        if (url == null) return ""
        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            return duckDuckGoUrlDetector.extractQuery(url) ?: ""
        }
        return url
    }

    private fun pageCleared() {
        Timber.v("Page cleared: $url")
        site = null
        onSiteChanged()

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            canAddBookmarks = false,
            addToHomeEnabled = false,
            addToHomeVisible = addToHomeCapabilityDetector.isAddToHomeSupported(),
            canSharePage = false,
            showPrivacyGrade = false
        )
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
        val visualProgress = if (newProgress < FIXED_PROGRESS) {
            FIXED_PROGRESS
        } else {
            newProgress
        }
        loadingViewState.value = progress.copy(isLoading = isLoading, progress = visualProgress)
    }

    private fun registerSiteVisit() {
        Schedulers.io().scheduleDirect {
            networkLeaderboardDao.incrementSitesVisited()
        }
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
        viewModelScope.launch {

            val improvedGrade = withContext(dispatchers.io()) {
                site?.calculateGrades()?.improvedGrade
            }

            withContext(dispatchers.main()) {
                siteLiveData.value = site
                privacyGrade.value = improvedGrade
            }

            withContext(dispatchers.io()) {
                tabRepository.update(tabId, site)
            }
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

    fun onOmnibarInputStateChanged(query: String, hasFocus: Boolean, hasQueryChanged: Boolean) {

        // determine if empty list to be shown, or existing search results
        val autoCompleteSearchResults = if (query.isBlank()) {
            AutoCompleteResult(query, emptyList())
        } else {
            currentAutoCompleteViewState().searchResults
        }

        val currentOmnibarViewState = currentOmnibarViewState()
        val autoCompleteSuggestionsEnabled = appSettingsPreferencesStore.autoCompleteSuggestionsEnabled
        val showAutoCompleteSuggestions = hasFocus && query.isNotBlank() && hasQueryChanged && autoCompleteSuggestionsEnabled
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()

        omnibarViewState.value = currentOmnibarViewState.copy(isEditing = hasFocus)

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            showPrivacyGrade = currentBrowserViewState.browserShowing,
            showTabsButton = showControls,
            showFireButton = showControls,
            showMenuButton = showControls,
            showClearButton = showClearButton
        )

        autoCompleteViewState.value = AutoCompleteViewState(showAutoCompleteSuggestions, autoCompleteSearchResults)

        if (hasQueryChanged && hasFocus && autoCompleteSuggestionsEnabled) {
            autoCompletePublishSubject.accept(query.trim())
        }
    }

    suspend fun onBookmarkAddRequested() {
        val url = url ?: ""
        val title = title ?: ""
        val id = withContext(dispatchers.io()) {
            bookmarksDao.insert(BookmarkEntity(title = title, url = url))
        }
        withContext(dispatchers.main()) {
            command.value = ShowBookmarkAddedConfirmation(id, title, url)
        }
    }

    override fun onBookmarkEdited(id: Long, title: String, url: String) {
        viewModelScope.launch(dispatchers.io()) {
            editBookmark(id, title, url)
        }
    }

    suspend fun editBookmark(id: Long, title: String, url: String) {
        withContext(dispatchers.io()) {
            bookmarksDao.update(BookmarkEntity(id, title, url))
        }
    }

    fun onBrokenSiteSelected() {
        command.value = BrokenSiteFeedback(url)
    }

    fun onUserSelectedToEditQuery(query: String) {
        omnibarViewState.value = currentOmnibarViewState().copy(isEditing = false, omnibarText = query, shouldMoveCaretToEnd = true)
        autoCompleteViewState.value = AutoCompleteViewState(showSuggestions = false)
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
                command.value = OpenInNewTab(requiredAction.url)
                true
            }
            is RequiredAction.OpenInNewBackgroundTab -> {
                command.value = GenerateWebViewPreviewImage
                viewModelScope.launch { openInNewBackgroundTab(requiredAction.url) }
                true
            }
            is RequiredAction.DownloadFile -> {
                command.value = DownloadImage(requiredAction.url)
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

        val uri = site?.uri ?: return

        if (desktopSiteRequested && uri.isMobileSite) {
            val desktopUrl = uri.toDesktopUri().toString()
            Timber.i("Original URL $url - attempting $desktopUrl with desktop site UA string")
            command.value = Navigate(desktopUrl)
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

        faviconDownloader.download(currentPage.toUri())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.i("Successfully got favicon")
                command.value = AddHomeShortcut(title, currentPage, it)
            }, { throwable ->
                Timber.w(throwable, "Failed to obtain favicon")
                command.value = AddHomeShortcut(title, currentPage)
            })
    }

    fun userRequestedOpeningNewTab() {
        command.value = GenerateWebViewPreviewImage
        command.value = LaunchNewTab
    }

    fun onSurveyChanged(survey: Survey?) {
        val activeSurvey = ctaViewModel.onSurveyChanged(survey)
        if (activeSurvey != null) {
            refreshCta()
        }
    }

    fun onCtaShown() {
        val cta = ctaViewState.value?.cta ?: return
        ctaViewModel.onCtaShown(cta)
    }

    fun onManualCtaShown(cta: Cta) {
        ctaViewModel.onCtaShown(cta)
    }

    fun refreshCta() {
        if (currentGlobalLayoutState() is Browser) {
            viewModelScope.launch {
                val cta = withContext(dispatchers.io()) {
                    ctaViewModel.refreshCta(dispatchers.io(), currentBrowserViewState().browserShowing, siteLiveData.value)
                }
                ctaViewState.value = currentCtaViewState().copy(cta = cta)
            }
        }
    }

    fun registerDaxBubbleCtaDismissed() {
        val cta = ctaViewState.value?.cta ?: return
        ctaViewModel.registerDaxBubbleCtaDismissed(cta)
    }

    fun onUserClickCtaOkButton() {
        val cta = ctaViewState.value?.cta ?: return
        ctaViewModel.onUserClickCtaOkButton(cta)
        command.value = when (cta) {
            is HomePanelCta.Survey -> LaunchSurvey(cta.survey)
            is HomePanelCta.AddWidgetAuto -> LaunchAddWidget
            is HomePanelCta.AddWidgetInstructions -> LaunchLegacyAddWidget
            else -> return
        }
    }

    fun onUserDismissedCta() {
        val cta = ctaViewState.value?.cta ?: return

        ctaViewModel.onUserDismissedCta(cta)
        if (cta is HomePanelCta) {
            refreshCta()
        } else {
            ctaViewState.value = currentCtaViewState().copy(cta = null)
        }
    }

    fun updateTabPreview(tabId: String, fileName: String) {
        tabRepository.updateTabPreviewImage(tabId, fileName)
    }

    fun deleteTabPreview(tabId: String) {
        tabRepository.updateTabPreviewImage(tabId, null)
    }

    override fun externalAppLinkClicked(appLink: IntentType) {
        command.value = HandleExternalAppLink(appLink)
    }

    override fun openInNewTab(url: String?) {
        command.value = OpenInNewTab(url.orEmpty())
    }

    override fun openMessageInNewTab(message: Message) {
        command.value = OpenMessageInNewTab(message)
    }

    override fun recoverFromRenderProcessGone() {
        webNavigationState?.let {
            navigationStateChanged(EmptyNavigationState(it))
        }
        invalidateBrowsingActions()
        showErrorWithAction()
    }

    override fun requiresAuthentication(request: BasicAuthenticationRequest) {
        command.value = RequiresAuthentication(request)
    }

    override fun handleAuthentication(request: BasicAuthenticationRequest, credentials: BasicAuthenticationCredentials) {
        request.handler.proceed(credentials.username, credentials.password)
        command.value = SaveCredentials(request, credentials)
    }

    override fun cancelAuthentication(request: BasicAuthenticationRequest) {
        request.handler.cancel()
    }

    fun userLaunchingTabSwitcher() {
        command.value = LaunchTabSwitcher
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
            canChangeBrowsingMode = false
        )
    }

    private fun showErrorWithAction() {
        command.value = ShowErrorWithAction { this.onUserSubmittedQuery(url.orEmpty()) }
    }

    private fun recoverTabWithQuery(query: String) {
        viewModelScope.launch { closeCurrentTab() }
        command.value = OpenInNewTab(query)
    }

    companion object {
        private const val FIXED_PROGRESS = 50
    }
}