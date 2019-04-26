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

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import timber.log.Timber
import javax.inject.Inject

@WorkerThread
class TrackerDataLoader @Inject constructor(
    private val trackerDetector: TrackerDetector,
    private val binaryDataStore: BinaryDataStore,
    private val trackerDataDao: TrackerDataDao
) {

    fun loadData() {

        Timber.d("Loading Tracker data")

        // this is stored to disk, then fed to the C++ adblock module
        loadAdblockData(Client.ClientName.TRACKERSWHITELIST)

        // stored in DB, then read into memory
        loadDisconnectData()
    }

    fun loadAdblockData(name: Client.ClientName) {
        Timber.d("Looking for adblock tracker ${name.name} to load")

        if (binaryDataStore.hasData(name.name)) {
            Timber.d("Found adblock tracker ${name.name}")
            val client = AdBlockClient(name)
            client.loadProcessedData(binaryDataStore.loadData(name.name))
            trackerDetector.addClient(client)
        } else {
            Timber.d("No adblock tracker ${name.name} found")
        }
    }

    fun loadDisconnectData() {
        val trackers = trackerDataDao.getAll()
        Timber.d("Loaded ${trackers.size} disconnect trackers from DB")

        val client = DisconnectClient(Client.ClientName.DISCONNECT, trackers)
        trackerDetector.addClient(client)
    }
}
