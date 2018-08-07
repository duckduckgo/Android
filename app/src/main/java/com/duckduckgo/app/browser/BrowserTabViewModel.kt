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

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.graphics.Bitmap
import android.net.Uri
import android.support.annotation.AnyThread
import android.support.annotation.StringRes
import android.support.annotation.VisibleForTesting
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteResult
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.SaveBookmarkDialogFragment.SaveBookmarkListener
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.*
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.SpecialUrlDetector.UrlType.IntentType
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserNotification
import com.duckduckgo.app.browser.favicon.FaviconDownloader
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.session.WebViewSessionStorage
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.baseHost
import com.duckduckgo.app.global.db.AppConfigurationDao
import com.duckduckgo.app.global.db.AppConfigurationEntity
import com.duckduckgo.app.global.isMobileSite
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteFactory
import com.duckduckgo.app.global.toDesktopUri
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacy.db.SiteVisitedEntity
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.improvedGrade
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
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
    private val autoCompleteApi: AutoCompleteApi,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val defaultBrowserNotification: DefaultBrowserNotification,
    private val longPressHandler: LongPressHandler,
    private val webViewSessionStorage: WebViewSessionStorage,
    private val specialUrlDetector: SpecialUrlDetector,
    private val faviconDownloader: FaviconDownloader,
    appConfigurationDao: AppConfigurationDao
) : WebViewClientListener, SaveBookmarkListener, ViewModel() {

    data class GlobalLayoutViewState(
        val isNewTabState: Boolean = true
    )

    data class BrowserViewState(
        val browserShowing: Boolean = false,
        val isFullScreen: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val showPrivacyGrade: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val showFireButton: Boolean = true,
        val showMenuButton: Boolean = true,
        val canSharePage: Boolean = false,
        val canAddBookmarks: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val canAddToHome: Boolean = false
    )

    data class OmnibarViewState(
        val omnibarText: String = "",
        val isEditing: Boolean = false
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

    data class DefaultBrowserViewState(
        val showDefaultBrowserBanner: Boolean = false,
        val showHomeScreenCallToActionButton: Boolean = false
    )

    sealed class Command {
        object LandingPage : Command()
        object Refresh : Command()
        class Navigate(val url: String) : Command()
        class OpenInNewTab(val query: String) : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
        object ShowKeyboard : Command()
        object HideKeyboard : Command()
        class ShowFullScreen(val view: View) : Command()
        class DownloadImage(val url: String) : Command()
        class ShareLink(val url: String) : Command()
        class FindInPageCommand(val searchTerm: String) : Command()
        class BrokenSiteFeedback(val url: String?) : Command()
        class DisplayMessage(@StringRes val messageId: Int) : Command()
        object DismissFindInPage : Command()
        class ShowFileChooser(val filePathCallback: ValueCallback<Array<Uri>>, val fileChooserParams: WebChromeClient.FileChooserParams) : Command()
        class HandleExternalAppLink(val appLink: IntentType) : Command()
        class AddHomeShortcut(val title: String, val url: String, val icon: Bitmap?= null) : Command()
    }

    val autoCompleteViewState: MutableLiveData<AutoCompleteViewState> = MutableLiveData()
    val browserViewState: MutableLiveData<BrowserViewState> = MutableLiveData()
    val globalLayoutState: MutableLiveData<GlobalLayoutViewState> = MutableLiveData()
    val loadingViewState: MutableLiveData<LoadingViewState> = MutableLiveData()
    val omnibarViewState: MutableLiveData<OmnibarViewState> = MutableLiveData()
    val defaultBrowserViewState: MutableLiveData<DefaultBrowserViewState> = MutableLiveData()
    val findInPageViewState: MutableLiveData<FindInPageViewState> = MutableLiveData()

    val tabs: LiveData<List<TabEntity>> = tabRepository.liveTabs
    val privacyGrade: MutableLiveData<PrivacyGrade> = MutableLiveData()
    val url: SingleLiveEvent<String> = SingleLiveEvent()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    @VisibleForTesting
    val appConfigurationObserver: Observer<AppConfigurationEntity> = Observer { appConfiguration ->
        appConfiguration?.let {
            Timber.i("App configuration downloaded: ${it.appConfigurationDownloaded}")
            appConfigurationDownloaded = it.appConfigurationDownloaded
        }
    }

    private var appConfigurationDownloaded = false
    private val appConfigurationObservable = appConfigurationDao.appConfigurationStatus()
    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private var siteLiveData = MutableLiveData<Site>()
    private var site: Site? = null
    private lateinit var tabId: String


    init {
        initializeViewStates()

        appConfigurationObservable.observeForever(appConfigurationObserver)
        configureAutoComplete()
    }

    fun loadData(tabId: String, initialUrl: String?) {
        this.tabId = tabId
        siteLiveData = tabRepository.retrieveSiteData(tabId)
        site = siteLiveData.value

        initialUrl?.let {
            site = siteFactory.build(it)
        }
    }

    fun onViewReady() {
        site?.url?.let {
            onUserSubmittedQuery(it)
        }
    }

    private fun configureAutoComplete() {
        autoCompletePublishSubject
            .debounce(300, TimeUnit.MILLISECONDS)
            .distinctUntilChanged()
            .switchMap { autoCompleteApi.autoComplete(it) }
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
        super.onCleared()
        appConfigurationObservable.removeObserver(appConfigurationObserver)
    }

    fun registerWebViewListener(browserWebViewClient: BrowserWebViewClient, browserChromeClient: BrowserChromeClient) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onViewVisible() {
        command.value = if (url.value == null) ShowKeyboard else Command.HideKeyboard

        val showBanner = defaultBrowserNotification.shouldShowBannerNotification(currentBrowserViewState().browserShowing)
        val showCallToActionButton = defaultBrowserNotification.shouldShowHomeScreenCallToActionNotification()
        defaultBrowserViewState.value = DefaultBrowserViewState(showBanner, showCallToActionButton)
    }

    fun onUserSubmittedQuery(input: String) {
        if (input.isBlank()) {
            return
        }

        command.value = HideKeyboard
        val trimmedInput = input.trim()

        val type = specialUrlDetector.determineType(trimmedInput)
        when (type) {
            is IntentType -> {
                externalAppLinkClicked(type)
            }
            else -> {
                url.value = queryUrlConverter.convertQueryToUrl(trimmedInput)
            }
        }

        globalLayoutState.value = GlobalLayoutViewState(isNewTabState = false)
        findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = true)
        omnibarViewState.value = currentOmnibarViewState().copy(omnibarText = trimmedInput)
        browserViewState.value = currentBrowserViewState().copy(browserShowing = true, showClearButton = false)
        autoCompleteViewState.value = AutoCompleteViewState(false)
    }

    override fun progressChanged(newProgress: Int, canGoBack: Boolean, canGoForward: Boolean) {
        Timber.v("Loading in progress $newProgress")

        val progress = currentLoadingViewState()
        loadingViewState.value = progress.copy(progress = newProgress)
        browserViewState.value = currentBrowserViewState().copy(canGoBack = canGoBack, canGoForward = canGoForward)
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

    override fun loadingStarted() {
        Timber.v("Loading started")
        val progress = currentLoadingViewState()
        loadingViewState.value = progress.copy(isLoading = true)
        site = null
        onSiteChanged()
    }

    override fun loadingFinished(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        Timber.v("Loading finished")

        val currentOmnibarViewState = currentOmnibarViewState()
        val currentLoadingViewState = currentLoadingViewState()

        val omnibarText = if (url != null) omnibarTextForUrl(url) else currentOmnibarViewState.omnibarText

        loadingViewState.value = currentLoadingViewState.copy(isLoading = false)
        omnibarViewState.value = currentOmnibarViewState.copy(omnibarText = omnibarText)
        browserViewState.value = currentBrowserViewState().copy(canGoBack = canGoBack, canGoForward = canGoForward)
        registerSiteVisit()
    }

    private fun registerSiteVisit() {
        val domainVisited = url.value?.toUri()?.host ?: return
        Schedulers.io().scheduleDirect {
            networkLeaderboardDao.insert(SiteVisitedEntity(domainVisited))
        }
    }

    override fun titleReceived(title: String) {
        site?.title = title
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

    override fun urlChanged(url: String?) {
        Timber.v("Url changed: $url")

        if (url == null) {
            findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = false)

            val currentBrowserViewState = currentBrowserViewState()
            browserViewState.value = currentBrowserViewState.copy(canAddBookmarks = false, canAddToHome = false)

            return
        }


        val currentBrowserViewState = currentBrowserViewState()
        val currentOmnibarViewState = currentOmnibarViewState()

        omnibarViewState.value = currentOmnibarViewState.copy(omnibarText = omnibarTextForUrl(url))
        findInPageViewState.value = FindInPageViewState(visible = false, canFindInPage = true)
        browserViewState.value = currentBrowserViewState.copy(
            browserShowing = true,
            canAddBookmarks = true,
            canAddToHome = true,
            canSharePage = true,
            showPrivacyGrade = appConfigurationDownloaded
        )

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            val shouldShowBanner = defaultBrowserNotification.shouldShowBannerNotification(currentBrowserViewState.browserShowing)
            defaultBrowserViewState.value = currentDefaultBrowserViewState().copy(showDefaultBrowserBanner = shouldShowBanner)
            statisticsUpdater.refreshRetentionAtb()
        }

        site = siteFactory.build(url)
        onSiteChanged()
    }

    private fun omnibarTextForUrl(url: String): String {
        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            return duckDuckGoUrlDetector.extractQuery(url) ?: ""
        }
        return url
    }

    override fun trackerDetected(event: TrackingEvent) {
        if (event.documentUrl == site?.url) {
            site?.trackerDetected(event)
            onSiteChanged()
        }
        updateNetworkLeaderboard(event)
    }

    private fun updateNetworkLeaderboard(event: TrackingEvent) {
        val networkName = event.trackerNetwork?.name ?: return
        val domainVisited = Uri.parse(event.documentUrl).host ?: return
        networkLeaderboardDao.insert(NetworkLeaderboardEntry(networkName, domainVisited))
        networkLeaderboardDao.insert(SiteVisitedEntity(domainVisited))
    }

    override fun pageHasHttpResources(page: String?) {
        if (page == site?.url) {
            site?.hasHttpResources = true
            onSiteChanged()
        }
    }

    private fun onSiteChanged() {
        siteLiveData.postValue(site)
        privacyGrade.postValue(site?.improvedGrade)
        tabRepository.update(tabId, site)
    }

    override fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams) {
        command.value = Command.ShowFileChooser(filePathCallback, fileChooserParams)
    }

    private fun currentAutoCompleteViewState(): AutoCompleteViewState = autoCompleteViewState.value!!
    private fun currentBrowserViewState(): BrowserViewState = browserViewState.value!!
    private fun currentFindInPageViewState(): FindInPageViewState = findInPageViewState.value!!
    private fun currentOmnibarViewState(): OmnibarViewState = omnibarViewState.value!!
    private fun currentLoadingViewState(): LoadingViewState = loadingViewState.value!!
    private fun currentDefaultBrowserViewState(): DefaultBrowserViewState = defaultBrowserViewState.value!!

    fun onOmnibarInputStateChanged(query: String, hasFocus: Boolean) {

        // determine if empty list to be shown, or existing search results
        val autoCompleteSearchResults = if (query.isBlank()) {
            AutoCompleteResult(query, emptyList())
        } else {
            currentAutoCompleteViewState().searchResults
        }

        val currentOmnibarViewState = currentOmnibarViewState()
        val hasQueryChanged = (currentOmnibarViewState.omnibarText != query)
        val autoCompleteSuggestionsEnabled = appSettingsPreferencesStore.autoCompleteSuggestionsEnabled
        val showAutoCompleteSuggestions = hasFocus && query.isNotBlank() && hasQueryChanged && autoCompleteSuggestionsEnabled
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()

        omnibarViewState.value = currentOmnibarViewState.copy(isEditing = hasFocus)

        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(
            showPrivacyGrade = appConfigurationDownloaded && currentBrowserViewState.browserShowing,
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

    override fun onBookmarkSaved(id: Int?, title: String, url: String) {
        Schedulers.io().scheduleDirect {
            bookmarksDao.insert(BookmarkEntity(title = title, url = url))
        }
        command.value = DisplayMessage(R.string.bookmarkAddedFeedback)
    }

    fun onBrokenSiteSelected() {
        command.value = BrokenSiteFeedback(site?.url)
    }

    fun onUserSelectedToEditQuery(query: String) {
        omnibarViewState.value = currentOmnibarViewState().copy(isEditing = false, omnibarText = query)
        autoCompleteViewState.value = AutoCompleteViewState(showSuggestions = false)
    }

    fun userLongPressedInWebView(target: WebView.HitTestResult, menu: ContextMenu) {
        Timber.i("Long pressed on ${target.type}, (extra=${target.extra})")
        longPressHandler.handleLongPress(target.type, target.extra, menu)
    }

    fun userSelectedItemFromLongPressMenu(longPressTarget: String, item: MenuItem): Boolean {

        val requiredAction = longPressHandler.userSelectedMenuItem(longPressTarget, item)

        return when (requiredAction) {
            is RequiredAction.OpenInNewTab -> {
                command.value = OpenInNewTab(requiredAction.url)
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
            RequiredAction.None -> {
                false
            }
        }
    }

    fun userRequestingToFindInPage() {
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
        findInPageViewState.value = currentViewState.copy(showNumberMatches = true,
        activeMatchIndex = activeIndex,
        numberMatches = numberOfMatches)
    }

    fun onWebSessionRestored() {
        globalLayoutState.value = GlobalLayoutViewState(isNewTabState = false)
    }

    fun desktopSiteModeToggled(urlString: String?, desktopSiteRequested: Boolean) {
        val currentBrowserViewState = currentBrowserViewState()
        browserViewState.value = currentBrowserViewState.copy(isDesktopBrowsingMode = desktopSiteRequested)

        if (urlString == null) {
            return
        }
        val url = Uri.parse(urlString)
        if (desktopSiteRequested && url.isMobileSite) {
            val desktopUrl = url.toDesktopUri()
            Timber.i("Original URL $urlString - attempting $desktopUrl with desktop site UA string")
            command.value = Navigate(desktopUrl.toString())
        } else {
            command.value = Refresh
        }
    }

    fun resetView() {
        site = null
        url.value = null
        onSiteChanged()
        initializeViewStates()
    }

    private fun initializeViewStates() {
        globalLayoutState.value = GlobalLayoutViewState()
        defaultBrowserViewState.value = DefaultBrowserViewState(showHomeScreenCallToActionButton = defaultBrowserNotification.shouldShowHomeScreenCallToActionNotification())
        browserViewState.value = BrowserViewState()
        loadingViewState.value = LoadingViewState()
        autoCompleteViewState.value = AutoCompleteViewState()
        omnibarViewState.value = OmnibarViewState()
        findInPageViewState.value = FindInPageViewState()
    }

    fun userSharingLink(url: String?) {
        if (url != null) {
            command.value = ShareLink(url)
        }
    }

    fun userDeclinedBannerToSetAsDefaultBrowser() {
        defaultBrowserDetector.userDeclinedBannerToSetAsDefaultBrowser()
        val currentDefaultBrowserViewState = currentDefaultBrowserViewState()
        defaultBrowserViewState.value = currentDefaultBrowserViewState.copy(showDefaultBrowserBanner = false)
    }

    fun userDeclinedHomeScreenCallToActionToSetAsDefaultBrowser() {
        defaultBrowserDetector.userDeclinedHomeScreenCallToActionToSetAsDefaultBrowser()
        val currentDefaultBrowserViewState = currentDefaultBrowserViewState()
        defaultBrowserViewState.value = currentDefaultBrowserViewState.copy(showHomeScreenCallToActionButton = false)
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

    fun userRequestedToPinPageToHome(currentPage: String) {
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

    override fun externalAppLinkClicked(appLink: IntentType) {
        command.value = HandleExternalAppLink(appLink)
    }
}



