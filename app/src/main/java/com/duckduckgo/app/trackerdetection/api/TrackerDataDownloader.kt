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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.AdBlockClient
import com.duckduckgo.app.trackerdetection.Client
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import com.duckduckgo.app.trackerdetection.store.TrackerDataStore
import io.reactivex.Completable
import timber.log.Timber
import javax.inject.Inject


class TrackerDataDownloader @Inject constructor(
        private val trackerListService: TrackerListService,
        private val trackerDataStore: TrackerDataStore,
        private val trackerDataLoader: TrackerDataLoader,
        private val trackerDataDao: TrackerDataDao) {

    // this should run on every sync
    fun downloadList(clientName: Client.ClientName): Completable {

        return when (clientName) {
            DISCONNECT -> disconnectDownload()
            EASYLIST, EASYPRIVACY -> easyDownload(clientName)
        }
    }
    private fun disconnectDownload(): Completable {

        return Completable.fromAction({
            val call = trackerListService.disconnect()
            val response = call.execute()
            if (response.isSuccessful) {
                Timber.d("Got disconnect response from server")
                val body = response.body()!!

                trackerDataDao.insertAll(body.trackers)
                trackerDataLoader.loadDisconnectData()
            }
        })
    }

    private fun easyDownload(clientName: Client.ClientName): Completable {
        return Completable.fromAction({
            val call = trackerListService.list(clientName.name.toLowerCase())
            val response = call.execute()

            if (response.isSuccessful) {
                val bodyBytes = response.body()!!.bytes()

                val cachedResponse = response.raw().cacheResponse()
                if (cachedResponse != null) {
                    Timber.i("Re-using tracker data from cache")
                } else {
                    Timber.i("Response not from cache; updating data store with new data")
                    persistTrackerData(clientName, bodyBytes)
                    trackerDataLoader.loadAdblockData(clientName)
                }
            }
        })
    }

    private fun persistTrackerData(clientName: Client.ClientName, bodyBytes: ByteArray) {
        val client = AdBlockClient(clientName)
        client.loadBasicData(bodyBytes)
        trackerDataStore.saveData(clientName, client.getProcessedData())
    }

}