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
import android.arch.lifecycle.ViewModel
import android.net.Uri
import android.support.annotation.AnyThread
import com.duckduckgo.app.about.AboutDuckDuckGoActivity.Companion.RESULT_CODE_LOAD_ABOUT_DDG_WEB_PAGE
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
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity.Companion.RESULT_RELOAD
import com.duckduckgo.app.privacymonitor.ui.PrivacyDashboardActivity.Companion.RESULT_TOSDR
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
        private val networkLeaderboardDao: NetworkLeaderboardDao) :
        WebViewClientListener, ViewModel() {

    data class ViewState(
            val isLoading: Boolean = false,
            val progress: Int = 0,
            val omnibarText: String? = null,
            val isEditing: Boolean = false,
            val browserShowing: Boolean = false,
            val showClearButton: Boolean = false,
            val showPrivacyGrade: Boolean = false,
            val showFireButton: Boolean = true
    )

    /* Observable data for Activity to subscribe to */
    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val privacyGrade: MutableLiveData<PrivacyGrade> = MutableLiveData()
    val url: SingleLiveEvent<String> = SingleLiveEvent()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    sealed class Command {
        class LandingPage : Command()
        class Refresh : Command()
        class Navigate(val url: String) : Command()
        class DialNumber(val telephoneNumber: String) : Command()
        class SendSms(val telephoneNumber: String) : Command()
        class SendEmail(val emailAddress: String) : Command()
    }

    private var siteMonitor: SiteMonitor? = null

    init {
        viewState.value = ViewState()
        privacyMonitorRepository.privacyMonitor = MutableLiveData()
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

    fun buildUrl(input: String): String {
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
        if (url == null) return

        viewState.value = currentViewState().copy(
                omnibarText = omnibarText(url),
                browserShowing = true,
                showPrivacyGrade = true,
                showFireButton = true
        )

        val terms = termsOfServiceStore.retrieveTerms(url) ?: TermsOfService()
        siteMonitor = SiteMonitor(url, terms, trackerNetworks)
        onSiteMonitorChanged()
    }

    private fun omnibarText(url: String): String {
        if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url) && duckDuckGoUrlDetector.hasQuery(url)) {
            return duckDuckGoUrlDetector.extractQuery(url) ?: url
        }
        return url
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
                showPrivacyGrade = !hasFocus,
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
            RESULT_RELOAD -> command.value = Refresh()
            RESULT_TOSDR -> {
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
}



