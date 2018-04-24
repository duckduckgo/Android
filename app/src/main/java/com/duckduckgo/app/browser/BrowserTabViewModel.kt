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
import androidx.net.toUri
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi.AutoCompleteResult
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.ui.SaveBookmarkDialogFragment.SaveBookmarkListener
import com.duckduckgo.app.browser.BrowserTabViewModel.Command.*
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.SingleLiveEvent
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
    private val longPressHandler: LongPressHandler,
    appConfigurationDao: AppConfigurationDao
) : WebViewClientListener, SaveBookmarkListener, ViewModel() {

    data class ViewState(
        val isLoading: Boolean = false,
        val progress: Int = 0,
        val omnibarText: String = "",
        val isEditing: Boolean = false,
        val browserShowing: Boolean = false,
        val showPrivacyGrade: Boolean = false,
        val showClearButton: Boolean = false,
        val showTabsButton: Boolean = true,
        val showFireButton: Boolean = true,
        val showMenuButton: Boolean = true,
        val canAddBookmarks: Boolean = false,
        val isFullScreen: Boolean = false,
        val autoComplete: AutoCompleteViewState = AutoCompleteViewState(),
        val findInPage: FindInPage = FindInPage(canFindInPage = false),
        val isDesktopBrowsingMode: Boolean = false,
        val canSharePage: Boolean = false
    )

    sealed class Command {
        object LandingPage : Command()
        object Refresh : Command()
        class NewTab(val query: String) : Command()
        class Navigate(val url: String) : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
        object ShowKeyboard : Command()
        object HideKeyboard : Command()
        class ShowFullScreen(val view: View) : Command()
        class DownloadImage(val url: String) : Command()
        class ShareLink(val url: String) : Command()
        class FindInPageCommand(val searchTerm: String) : Command()
        class DisplayMessage(@StringRes val messageId: Int) : Command()
        object DismissFindInPage : Command()
        class ShowFileChooser(val filePathCallback: ValueCallback<Array<Uri>>, val fileChooserParams: WebChromeClient.FileChooserParams) : Command()
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
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
        viewState.value = ViewState()
        appConfigurationObservable.observeForever(appConfigurationObserver)
        configureAutoComplete()
    }

    fun load(tabId: String) {
        this.tabId = tabId
        siteLiveData = tabRepository.retrieveSiteData(tabId)
        site = siteLiveData.value
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
        val currentViewState = currentViewState()
        val searchResultViewState = currentViewState.autoComplete
        viewState.value = currentViewState.copy(autoComplete = searchResultViewState.copy(searchResults = AutoCompleteResult(result.query, results)))
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
    }

    fun onUserSubmittedQuery(input: String) {
        if (input.isBlank()) {
            return
        }

        command.value = HideKeyboard
        val trimmedInput = input.trim()
        url.value = buildUrl(trimmedInput)

        viewState.value = currentViewState().copy(
            findInPage = FindInPage(visible = false, canFindInPage = true),
            showClearButton = false,
            omnibarText = trimmedInput,
            browserShowing = true,
            autoComplete = AutoCompleteViewState(false)
        )
    }

    private fun buildUrl(input: String): String {
        if (queryUrlConverter.isWebUrl(input)) {
            return queryUrlConverter.convertUri(input)
        }
        return queryUrlConverter.convertQueryToUri(input).toString()
    }

    override fun progressChanged(newProgress: Int) {
        Timber.v("Loading in progress $newProgress")
        viewState.value = currentViewState().copy(progress = newProgress)
    }

    override fun goFullScreen(view: View) {
        command.value = ShowFullScreen(view)
        viewState.value = currentViewState().copy(isFullScreen = true)
    }

    override fun exitFullScreen() {
        viewState.value = currentViewState().copy(isFullScreen = false)
    }

    override fun loadingStarted() {
        Timber.v("Loading started")
        viewState.value = currentViewState().copy(isLoading = true)
        site = null
        onSiteChanged()
    }

    override fun loadingFinished() {
        Timber.v("Loading finished")
        viewState.value = currentViewState().copy(isLoading = false)
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
            viewState.value = viewState.value?.copy(
                canAddBookmarks = false,
                findInPage = FindInPage(visible = false, canFindInPage = false)
            )
            return
        }

        var newViewState = currentViewState().copy(
            canAddBookmarks = true,
            omnibarText = url,
            browserShowing = true,
            canSharePage = true,
            showPrivacyGrade = appConfigurationDownloaded,
            findInPage = FindInPage(visible = false, canFindInPage = true)
        )

        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)) {
            newViewState = newViewState.copy(omnibarText = duckDuckGoUrlDetector.extractQuery(url) ?: "")
            statisticsUpdater.refreshRetentionAtb()
        }
        viewState.value = newViewState
        site = siteFactory.build(url)
        onSiteChanged()
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

    private fun currentViewState(): ViewState = viewState.value!!

    fun onOmnibarInputStateChanged(query: String, hasFocus: Boolean) {

        val currentViewState = currentViewState()

        // determine if empty list to be shown, or existing search results
        val autoCompleteSearchResults = if (query.isBlank()) {
            AutoCompleteResult(query, emptyList())
        } else {
            currentViewState.autoComplete.searchResults
        }

        val hasQueryChanged = (currentViewState.omnibarText != query)
        val autoCompleteSuggestionsEnabled = appSettingsPreferencesStore.autoCompleteSuggestionsEnabled
        val showAutoCompleteSuggestions = hasFocus && query.isNotBlank() && hasQueryChanged && autoCompleteSuggestionsEnabled
        val showClearButton = hasFocus && query.isNotBlank()
        val showControls = !hasFocus || query.isBlank()

        viewState.value = currentViewState().copy(
            isEditing = hasFocus,
            showPrivacyGrade = appConfigurationDownloaded && currentViewState.browserShowing,
            showTabsButton = showControls,
            showFireButton = showControls,
            showMenuButton = showControls,
            showClearButton = showClearButton,
            autoComplete = AutoCompleteViewState(showAutoCompleteSuggestions, autoCompleteSearchResults)
        )

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

    fun onUserSelectedToEditQuery(query: String) {
        viewState.value = currentViewState().copy(
            isEditing = false,
            autoComplete = AutoCompleteViewState(showSuggestions = false),
            omnibarText = query
        )
    }

    fun userLongPressedInWebView(target: WebView.HitTestResult, menu: ContextMenu) {
        Timber.i("Long pressed on ${target.type}, (extra=${target.extra})")
        longPressHandler.handleLongPress(target.type, target.extra, menu)
    }

    fun userSelectedItemFromLongPressMenu(longPressTarget: String, item: MenuItem): Boolean {

        val requiredAction = longPressHandler.userSelectedMenuItem(longPressTarget, item)

        return when (requiredAction) {
            is RequiredAction.OpenInNewTab -> {
                command.value = NewTab(requiredAction.url)
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
        viewState.value = currentViewState().copy(findInPage = FindInPage(visible = true))
    }

    fun userFindingInPage(searchTerm: String) {
        var findInPage = currentViewState().findInPage.copy(visible = true, searchTerm = searchTerm)
        if (searchTerm.isEmpty()) {
            findInPage = findInPage.copy(showNumberMatches = false)
        }
        viewState.value = currentViewState().copy(findInPage = findInPage)
        command.value = FindInPageCommand(searchTerm)
    }

    fun dismissFindInView() {
        viewState.value = currentViewState().copy(findInPage = FindInPage(visible = false))
        command.value = DismissFindInPage
    }

    fun onFindResultsReceived(activeMatchOrdinal: Int, numberOfMatches: Int) {
        val activeIndex = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1
        val findInPage = currentViewState().findInPage.copy(
            showNumberMatches = true,
            activeMatchIndex = activeIndex,
            numberMatches = numberOfMatches
        )
        viewState.value = currentViewState().copy(findInPage = findInPage)
    }

    fun desktopSiteModeToggled(urlString: String?, desktopSiteRequested: Boolean) {
        viewState.value = currentViewState().copy(isDesktopBrowsingMode = desktopSiteRequested)

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
        onSiteChanged()
        viewState.value = ViewState()
    }

    fun userSharingLink(url: String?) {
        if (url != null) {
            command.value = ShareLink(url)
        }
    }

    data class FindInPage(
        val visible: Boolean = false,
        val showNumberMatches: Boolean = false,
        val activeMatchIndex: Int = 0,
        val searchTerm: String = "",
        val numberMatches: Int = 0,
        val canFindInPage: Boolean = true
    )

    data class AutoCompleteViewState(
        val showSuggestions: Boolean = false,
        val searchResults: AutoCompleteResult = AutoCompleteResult("", emptyList())
    )
}



