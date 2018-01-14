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

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.net.Uri
import android.support.annotation.AnyThread
import android.support.annotation.VisibleForTesting
import com.duckduckgo.app.about.AboutDuckDuckGoActivity.Companion.RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.BrowserViewModel.Command.Navigate
import com.duckduckgo.app.browser.BrowserViewModel.Command.Refresh
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.StringResolver
import com.duckduckgo.app.privacymonitor.SiteMonitor
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardEntry
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.model.improvedGrade
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.store.TermsOfServiceStore
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity.Companion.RELOAD_RESULT_CODE
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity.Companion.TOSDR_RESULT_CODE
import com.duckduckgo.app.settings.db.AppConfigurationDao
import com.duckduckgo.app.settings.db.AppConfigurationEntity
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import timber.log.Timber

class BrowserViewModel(
        private val queryUrlConverter: OmnibarEntryConverter,
        private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
        private val termsOfServiceStore: TermsOfServiceStore,
        private val trackerNetworks: TrackerNetworks,
        private val privacyMonitorRepository: PrivacyMonitorRepository,
        private val stringResolver: StringResolver,
        private val networkLeaderboardDao: NetworkLeaderboardDao,
        private val bookmarksDao: BookmarksDao,
        appConfigurationDao: AppConfigurationDao) : WebViewClientListener, ViewModel() {

    data class ViewState(
            val isLoading: Boolean = false,
            val progress: Int = 0,
            val omnibarText: String? = null,
            val isEditing: Boolean = false,
            val browserShowing: Boolean = false,
            val showClearButton: Boolean = false,
            val showPrivacyGrade: Boolean = false,
            val showFireButton: Boolean = true,
            val canAddBookmarks: Boolean = false
    )

    sealed class Command {
        class LandingPage : Command()
        class Refresh : Command()
        class Navigate(val url: String) : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
    }

    /* Observable data for Activity to subscribe to */
    val viewState: MutableLiveData<ViewState> = MutableLiveData()
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

    private val appConfigurationObservable = appConfigurationDao.appConfigurationStatus()
    private var siteMonitor: SiteMonitor? = null
    private var appConfigurationDownloaded = false

    init {
        viewState.value = ViewState(canAddBookmarks = false)
        privacyMonitorRepository.privacyMonitor = MutableLiveData()
        appConfigurationObservable.observeForever(appConfigurationObserver)
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

    fun onUserSubmittedQuery(input: String) {
        if (input.isBlank()) {
            return
        }
        url.value = buildUrl(input)
        viewState.value = currentViewState().copy(showClearButton = false, omnibarText = input)
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

    override fun loadingStarted() {
        Timber.v("Loading started")
        viewState.value = currentViewState().copy(isLoading = true)
        siteMonitor = null
        onSiteMonitorChanged()
    }

    override fun loadingFinished() {
        Timber.v("Loading finished")
        viewState.value = currentViewState().copy(isLoading = false)
    }

    @AnyThread
    override fun sendEmailRequested(emailAddress: String) {
        command.postValue(Command.SendEmail(emailAddress))
    }

    @AnyThread
    override fun dialTelephoneNumberRequested(telephoneNumber: String) {
        command.postValue(Command.DialNumber(telephoneNumber))
    }

    @AnyThread
    override fun sendSmsRequested(telephoneNumber: String) {
        command.postValue(Command.SendSms(telephoneNumber))
    }

    override fun urlChanged(url: String?) {
        Timber.v("Url changed: $url")
        if (url == null) {
            viewState.value = viewState.value?.copy(canAddBookmarks = false)
            return
        }

        var newViewState = currentViewState().copy(
                canAddBookmarks = true,
                omnibarText = url,
                browserShowing = true,
                showFireButton = true,
                showPrivacyGrade = appConfigurationDownloaded)

        if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url) && duckDuckGoUrlDetector.hasQuery(url)) {
            newViewState = newViewState.copy(omnibarText = duckDuckGoUrlDetector.extractQuery(url))
        }
        viewState.value = newViewState

        val terms = termsOfServiceStore.retrieveTerms(url) ?: TermsOfService()
        siteMonitor = SiteMonitor(url, terms, trackerNetworks)
        onSiteMonitorChanged()
    }

    override fun trackerDetected(event: TrackingEvent) {
        updateSiteMonitor(event)
        updateNetworkLeaderboard(event)
    }

    private fun updateSiteMonitor(event: TrackingEvent) {
        siteMonitor?.trackerDetected(event)
        onSiteMonitorChanged()
    }

    private fun updateNetworkLeaderboard(event: TrackingEvent) {
        val networkName = event.trackerNetwork?.name ?: return
        val domainVisited = Uri.parse(event.documentUrl).host ?: return
        networkLeaderboardDao.insert(NetworkLeaderboardEntry(networkName, domainVisited))
    }

    override fun pageHasHttpResources() {
        siteMonitor?.hasHttpResources = true
        onSiteMonitorChanged()
    }

    private fun onSiteMonitorChanged() {
        privacyGrade.postValue(siteMonitor?.improvedGrade)
        privacyMonitorRepository.privacyMonitor.postValue(siteMonitor)
    }

    private fun currentViewState(): ViewState = viewState.value!!

    fun onOmnibarInputStateChanged(query: String, hasFocus: Boolean) {
        val showClearButton = hasFocus && query.isNotEmpty()
        viewState.value = currentViewState().copy(
                isEditing = hasFocus,
                showClearButton = showClearButton,
                showPrivacyGrade = appConfigurationDownloaded && !hasFocus,
                showFireButton = !hasFocus
        )
    }

    fun onSharedTextReceived(input: String) {
        command.value = Navigate(buildUrl(input))
    }

    /**
     * Returns a boolean indicating if the back button pressed was consumed or not
     *
     * May also instruct the Activity to navigate to a different screen because of the back button press
     */
    fun userDismissedKeyboard(): Boolean {
        if (!currentViewState().browserShowing) {
            command.value = Command.LandingPage()
            return true
        }
        return false
    }

    fun receivedDashboardResult(resultCode: Int) {
        when (resultCode) {
            RELOAD_RESULT_CODE -> command.value = Refresh()
            TOSDR_RESULT_CODE -> {
                val url = stringResolver.getString(R.string.tosdrUrl)
                command.value = Navigate(url)
            }
        }
    }

    fun receivedSettingsResult(resultCode: Int) {
        when (resultCode) {
            RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE -> {
                val url = stringResolver.getString(R.string.aboutUrl)
                command.value = Navigate(url)
            }
        }
    }

    fun addBookmark(title: String?, url: String?) {
        bookmarksDao.insert(BookmarkEntity(title = title, url = url!!))
    }

}



