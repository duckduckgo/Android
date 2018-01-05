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
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.privacymonitor.SiteMonitor
import com.duckduckgo.app.privacymonitor.model.PrivacyGrade
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.model.improvedGrade
import com.duckduckgo.app.privacymonitor.store.PrivacyMonitorRepository
import com.duckduckgo.app.privacymonitor.store.TermsOfServiceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import timber.log.Timber

class BrowserViewModel(
        private val queryUrlConverter: OmnibarEntryConverter,
        private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
        private val termsOfServiceStore: TermsOfServiceStore,
        private val trackerNetworks: TrackerNetworks,
        private val privacyMonitorRepository: PrivacyMonitorRepository) :
        WebViewClientListener, ViewModel() {

    data class ViewState(
            val isLoading: Boolean = false,
            val progress: Int = 0,
            val omnibarText: String? = null,
            val isEditing: Boolean = false,
            val browserShowing: Boolean = false,
            val showClearButton: Boolean = false,
            val showPrivacyGrade: Boolean = false
    )

    /* Observable data for Activity to subscribe to */
    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val privacyGrade: MutableLiveData<PrivacyGrade> = MutableLiveData()
    val query: SingleLiveEvent<String> = SingleLiveEvent()
    val navigation: SingleLiveEvent<NavigationCommand> = SingleLiveEvent()

    enum class NavigationCommand {
        LANDING_PAGE
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

        viewState.value = currentViewState().copy(showClearButton = false, omnibarText = input, browserShowing = true)

        if (queryUrlConverter.isWebUrl(input)) {
            query.value = queryUrlConverter.convertUri(input)
        } else {
            query.value = queryUrlConverter.convertQueryToUri(input).toString()
        }
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

    override fun urlChanged(url: String?) {
        Timber.v("Url changed: $url")
        if (url == null) return

        var newViewState = currentViewState().copy(omnibarText = url, browserShowing = true, showPrivacyGrade = true)

        if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url) && duckDuckGoUrlDetector.hasQuery(url)) {
            newViewState = newViewState.copy(omnibarText = duckDuckGoUrlDetector.extractQuery(url))
        }
        viewState.value = newViewState

        val terms = termsOfServiceStore.retrieveTerms(url) ?: TermsOfService()
        siteMonitor = SiteMonitor(url, terms, trackerNetworks)
        onSiteMonitorChanged()
    }

    override fun trackerDetected(event: TrackingEvent) {
        siteMonitor?.trackerDetected(event)
        onSiteMonitorChanged()
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
        viewState.value = currentViewState().copy(isEditing = hasFocus, showClearButton = showClearButton)
    }

    /**
     * Returns a boolean indicating if the back button pressed was consumed or not
     *
     * May also instruct the Activity to navigate to a different screen because of the back button press
     */
    fun userDismissedKeyboard(): Boolean {
        if (!currentViewState().browserShowing) {
            navigation.value = NavigationCommand.LANDING_PAGE
            return true
        }
        return false
    }
}



