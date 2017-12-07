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

package com.duckduckgo.app.trackerdetection

import com.duckduckgo.app.trackerdetection.api.TrackerListService
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.store.TrackerDataStore
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject


class TrackerDataLoader @Inject constructor(private val trackerDetector: TrackerDetector,
                                            private val trackerDataStore: TrackerDataStore,
                                            private val trackerListService: TrackerListService,
                                            private val networkTrackers: TrackerNetworks) {

    fun loadData() {
        loadAdblockData(Client.ClientName.EASYLIST)
        loadAdblockData(Client.ClientName.EASYPRIVACY)
        loadDisconnectData()
    }

    private fun loadAdblockData(name: Client.ClientName) {

        if (trackerDataStore.hasData(name)) {
            val client = AdBlockClient(name)
            client.loadProcessedData(trackerDataStore.loadData(name))
            trackerDetector.addClient(client)
            return
        }

        trackerListService.list(name.name.toLowerCase())
                .subscribeOn(Schedulers.io())
                .map { responseBody ->
                    val client = AdBlockClient(name)
                    client.loadBasicData(responseBody.bytes())
                    trackerDataStore.saveData(name, client.getProcessedData())
                    return@map client
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ client ->
                    trackerDetector.addClient(client)
                }, { error ->
                    Timber.e(error)
                })
    }

    private fun loadDisconnectData() {

        trackerListService.disconnect()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ response ->
                    val client = DisconnectClient(Client.ClientName.DISCONNECT, response.trackers)
                    trackerDetector.addClient(client)
                    networkTrackers.updateData(response.trackers)
                }, { error ->
                    Timber.e(error)
                })
    }
}
