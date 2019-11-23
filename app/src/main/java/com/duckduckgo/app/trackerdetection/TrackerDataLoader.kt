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
import com.duckduckgo.app.trackerdetection.db.TdsTrackerDao
import com.duckduckgo.app.trackerdetection.db.TemporaryTrackingWhitelistDao
import timber.log.Timber
import javax.inject.Inject

@WorkerThread
class TrackerDataLoader @Inject constructor(
    private val trackerDetector: TrackerDetector,
    private val tdsTrackerDao: TdsTrackerDao,
    private val tempWhitelistDao: TemporaryTrackingWhitelistDao
) {

    fun loadData() {
        Timber.d("Loading Tracker data")

        // stored in DB, then read into memory
        loadTdsTrackerData()
        loadTemporaryWhitelistData()
    }

    fun loadTdsTrackerData() {
        val trackers = tdsTrackerDao.getAll()
        Timber.d("Loaded ${trackers.size} tds trackers from DB")

        val client = TdsClient(Client.ClientName.TDS, trackers)
        trackerDetector.addClient(client)
    }

    fun loadTemporaryWhitelistData() {
        val whitelist = tempWhitelistDao.getAll()
        Timber.d("Loaded ${whitelist.size} temporarily whitelisted domains from DB")

        val client = DocumentDomainClient(Client.ClientName.TEMPORARY_WHITELIST, whitelist)
        trackerDetector.addClient(client)
    }
}
