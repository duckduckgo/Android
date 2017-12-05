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
import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import timber.log.Timber

class BrowserViewModel(
        private val queryUrlConverter: OmnibarEntryConverter,
        private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
        private val networkTrackers: NetworkTrackers) :
        WebViewClientListener, ViewModel() {

    data class ViewState(
            val isLoading: Boolean = false,
            val progress: Int = 0,
            val url: String? = null,
            val isEditing: Boolean = false,
            val browserShowing: Boolean = false,
            val showClearButton: Boolean = false
    )

    /* Observable data for Activity to subscribe to */
    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val query: SingleLiveEvent<String> = SingleLiveEvent()
    val navigation: SingleLiveEvent<NavigationCommand> = SingleLiveEvent()

    enum class NavigationCommand {
        LANDING_PAGE
    }

    private var lastQuery: String? = null
    var siteMonitor: SiteMonitor? = null

    init {
        viewState.value = ViewState()
    }

    fun registerWebViewListener(browserWebViewClient: BrowserWebViewClient, browserChromeClient: BrowserChromeClient) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    fun onUserSubmittedQuery(input: String) {

        if (input.isBlank()) {
            return
        }

        if (queryUrlConverter.isWebUrl(input)) {
            lastQuery = null
            query.value = queryUrlConverter.convertUri(input)
        } else {
            lastQuery = input
            query.value = queryUrlConverter.convertQueryToUri(input).toString()
        }

        viewState.value = currentViewState().copy(showClearButton = false)
    }

    override fun progressChanged(newProgress: Int) {
        Timber.v("Loading in progress $newProgress")
        viewState.value = currentViewState().copy(progress = newProgress)
    }

    override fun loadingStarted() {
        Timber.v("Loading started")
        viewState.value = currentViewState().copy(isLoading = true)
        siteMonitor = null
    }

    override fun loadingFinished() {
        Timber.v("Loading finished")
        viewState.value = currentViewState().copy(isLoading = false)
    }

    override fun urlChanged(url: String?) {
        Timber.v("Url changed: $url")
        var newViewState = currentViewState().copy(url = url, browserShowing = true)

        if (duckDuckGoUrlDetector.isDuckDuckGoUrl(url)) {
            newViewState = newViewState.copy(url = lastQuery)
        }
        viewState.value = newViewState
        if (url != null) {
            siteMonitor = SiteMonitor(url, networkTrackers)
        }
    }

    override fun trackerDetected(event: TrackingEvent) {
        siteMonitor?.trackerDetected(event)
    }

    override fun pageHasHttpResources() {
        siteMonitor?.hasHttpResources = true
    }

    private fun currentViewState(): ViewState = viewState.value!!


    fun urlFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            viewState.value = currentViewState().copy(isEditing = hasFocus, showClearButton = false)
        } else {
            viewState.value = currentViewState().copy(isEditing = hasFocus)
        }
    }

    fun onUrlInputValueChanged(query: String, hasFocus: Boolean) {
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



