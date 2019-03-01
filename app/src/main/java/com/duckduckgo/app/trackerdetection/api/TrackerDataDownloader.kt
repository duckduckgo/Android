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

import com.duckduckgo.app.global.api.isCached
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.trackerdetection.AdBlockClient
import com.duckduckgo.app.trackerdetection.Client
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import io.reactivex.Completable
import okhttp3.ResponseBody
import retrofit2.Call
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject


class TrackerDataDownloader @Inject constructor(
    private val trackerListService: TrackerListService,
    private val binaryDataStore: BinaryDataStore,
    private val trackerDataLoader: TrackerDataLoader,
    private val trackerDataDao: TrackerDataDao,
    private val appDatabase: AppDatabase
) {

    fun downloadList(clientName: Client.ClientName): Completable {

        return when (clientName) {
            DISCONNECT -> downloadDisconnectList()
            TRACKERSWHITELIST -> downloadAdblockList(clientName) { trackerListService.trackersWhitelist() }
            EASYLIST, EASYPRIVACY -> removeLegacyList(clientName)
        }
    }

    private fun downloadDisconnectList(): Completable {

        return Completable.fromAction {

            Timber.d("Downloading disconnect data")

            val call = trackerListService.disconnect()
            val response = call.execute()

            if (response.isCached && trackerDataDao.count() != 0) {
                Timber.d("Disconnect data already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                Timber.d("Updating disconnect data from server")
                val body = response.body()!!

                appDatabase.runInTransaction {
                    trackerDataDao.updateAll(body.trackers)
                    trackerDataLoader.loadDisconnectData()
                }

            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }

    private fun downloadAdblockList(clientName: Client.ClientName, callFactory: (clientName: Client.ClientName) -> Call<ResponseBody>): Completable {
        return Completable.fromAction {

            Timber.d("Downloading ${clientName.name} data")
            val call = callFactory(clientName)
            val response = call.execute()

            if (response.isCached && binaryDataStore.hasData(clientName.name)) {
                Timber.d("${clientName.name} data already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                val bodyBytes = response.body()!!.bytes()
                Timber.d("Updating ${clientName.name} data store with new data")
                persistTrackerData(clientName, bodyBytes)
                trackerDataLoader.loadAdblockData(clientName)
            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }

    private fun persistTrackerData(clientName: Client.ClientName, bodyBytes: ByteArray) {
        val client = AdBlockClient(clientName)
        client.loadBasicData(bodyBytes)
        binaryDataStore.saveData(clientName.name, client.getProcessedData())
    }

    private fun removeLegacyList(clientName: Client.ClientName): Completable {
        return Completable.fromAction {
            if (binaryDataStore.hasData(clientName.name)) {
                binaryDataStore.clearData(clientName.name)
            }
            return@fromAction
        }
    }

}