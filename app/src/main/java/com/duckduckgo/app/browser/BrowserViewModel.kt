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
import android.arch.lifecycle.ViewModelProvider
import com.duckduckgo.app.browser.omnibar.OmnibarEntryConverter
import com.duckduckgo.app.browser.omnibar.QueryUrlConverter
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
import javax.inject.Inject

class BrowserViewModel(
        private val queryUrlConverter: OmnibarEntryConverter,
        private val trackerDataProvider: TrackerDataProvider,
        private val trackerDetector: TrackerDetector,
        private val trackerListService: TrackerListService) : ViewModel() {

    val query: MutableLiveData<String> = MutableLiveData()

    init {
        loadTrackerClients()
    }

    fun onQueryEntered(input: String) {

        if (input.isBlank()) {
            return
        }

        if (queryUrlConverter.isWebUrl(input)) {
            query.value = queryUrlConverter.convertUri(input)

        } else {
            query.value = queryUrlConverter.convertQueryToUri(input).toString()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class BrowserViewModelFactory @Inject constructor() : ViewModelProvider.Factory {

        @Inject
        lateinit var queryUrlConverter: QueryUrlConverter

        @Inject
        lateinit var trackerDataProvider: TrackerDataProvider

        @Inject
        lateinit var trackerDetector: TrackerDetector

        @Inject
        lateinit var trackerListService: TrackerListService

        override fun <T : ViewModel> create(aClass: Class<T>): T {
            if (aClass.isAssignableFrom(BrowserViewModel::class.java)) {
                return BrowserViewModel(queryUrlConverter, trackerDataProvider, trackerDetector, trackerListService) as T
            }
            throw IllegalArgumentException("Unknown view model")
        }
    }

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



