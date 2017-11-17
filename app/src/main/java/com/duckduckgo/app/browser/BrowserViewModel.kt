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
import com.duckduckgo.app.trackerdetection.AdBlockPlus
import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName
import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName.EASYLIST
import com.duckduckgo.app.trackerdetection.TrackerDetectionClient.ClientName.EASYPRIVACY
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.store.TrackerDataProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class BrowserViewModel(
        private val queryUrlConverter: OmnibarEntryConverter,
        private val trackerDataProvider: TrackerDataProvider,
        private val trackerDetector: TrackerDetector,
        private val trackerListService: TrackerListService) :
        WebViewClientListener, ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val query: SingleLiveEvent<String> = SingleLiveEvent()

    init {
        viewState.value = ViewState()
    }

    fun registerWebViewListener(browserWebViewClient: BrowserWebViewClient, browserChromeClient: BrowserChromeClient) {
        browserWebViewClient.webViewClientListener = this
        browserChromeClient.webViewClientListener = this
    }

    init {
        loadTrackerClients()
    }

    fun onQueryEntered(input: String) {

        if (input.isBlank()) {
            return
        }

        val convertedQuery: String = if (queryUrlConverter.isWebUrl(input)) {
            queryUrlConverter.convertUri(input)
        } else {
            queryUrlConverter.convertQueryToUri(input).toString()
        }
        query.value = convertedQuery
    }

    override fun progressChanged(newProgress: Int) {
        Timber.v("Loading in progress $newProgress")
        viewState.value = currentViewState().copy(progress = newProgress)
    }

    override fun loadingStateChange(isLoading: Boolean) {
        Timber.v("Loading state changed. isLoading=$isLoading")
        viewState.value = currentViewState().copy(isLoading = isLoading)
    }

    override fun urlChanged(url: String?) {
        Timber.v("Url changed: $url")
        viewState.value = currentViewState().copy(url = url)
    }

    private fun currentViewState(): ViewState = viewState.value!!

    fun urlFocusChanged(hasFocus: Boolean) {
        viewState.value = currentViewState().copy(isEditing = hasFocus)
    }

    data class ViewState(
            val isLoading: Boolean = false,
            val progress: Int = 0,
            val url: String? = null,
            val isEditing: Boolean = false
    )

    private fun loadTrackerClients() {

        if (!trackerDetector.hasClient(EASYLIST)) {
            addTrackerClient(EASYLIST)
        }

        if (!trackerDetector.hasClient(EASYPRIVACY)) {
            addTrackerClient(EASYPRIVACY)
        }
    }

    private fun addTrackerClient(name: ClientName) {

        if (trackerDataProvider.hasData(name)) {
            val client = AdBlockPlus(name)
            client.loadProcessedData(trackerDataProvider.loadData(name))
            trackerDetector.addClient(client)
            return
        }

        trackerListService.list(name.name.toLowerCase())
                .subscribeOn(Schedulers.io())
                .map { responseBody ->
                    val client = AdBlockPlus(name)
                    client.loadBasicData(responseBody.bytes())
                    trackerDataProvider.saveData(name, client.getProcessedData())
                    return@map client
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ client ->
                    trackerDetector.addClient(client)
                }, { error ->
                    Timber.e(error)
                })
    }

}



