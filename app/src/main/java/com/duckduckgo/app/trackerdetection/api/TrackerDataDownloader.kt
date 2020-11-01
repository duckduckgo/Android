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
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.db.TdsTrackerDao
import com.duckduckgo.app.trackerdetection.db.TemporaryTrackingWhitelistDao
import com.duckduckgo.app.trackerdetection.model.TemporaryTrackingWhitelistedDomain
import io.reactivex.Completable
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class TrackerDataDownloader @Inject constructor(
    private val trackerListService: TrackerListService,
    private val binaryDataStore: BinaryDataStore,
    private val trackerDataLoader: TrackerDataLoader,
    private val tdsTrackerDao: TdsTrackerDao,
    private val temporaryTrackingWhitelistDao: TemporaryTrackingWhitelistDao,
    private val appDatabase: AppDatabase
) {

    fun downloadTds(): Completable {

        return Completable.fromAction {

            Timber.d("Downloading tds.json")

            val call = trackerListService.tds()
            val response = call.execute()

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
            if (response.isCached && tdsTrackerDao.count() != 0) {
                Timber.d("Tds data already cached and stored")
                return@fromAction
            }

            Timber.d("Updating tds data from server")
            val body = response.body()!!
            val eTag = response.headers()["eTag"]?.removeSurrounding("W/\"", "\"").orEmpty() // removes weak eTag validator
            appDatabase.runInTransaction {
                trackerDataLoader.persistTds(eTag, body)
                trackerDataLoader.loadTrackers()
            }
        }
    }

    fun downloadTemporaryWhitelist(): Completable {

        return Completable.fromAction {

            Timber.d("Downloading temporary tracking whitelist")

            val call = trackerListService.temporaryWhitelist()
            val response = call.execute()

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
            if (response.isCached && temporaryTrackingWhitelistDao.count() != 0) {
                Timber.d("Temporary whitelist data already cached and stored")
                return@fromAction
            }

            Timber.d("Updating temporary tracking whitelist data from server")
            val body = response.body()!!

            appDatabase.runInTransaction {
                temporaryTrackingWhitelistDao.updateAll(body.lines().filter { it.isNotBlank() }
                    .map { TemporaryTrackingWhitelistedDomain(it) })
                trackerDataLoader.loadTemporaryWhitelist()
            }
        }
    }

    fun clearLegacyLists(): Completable {
        return Completable.fromAction {
            listOf(EASYLIST, EASYPRIVACY, TRACKERSWHITELIST).forEach {
                if (binaryDataStore.hasData(it.name)) {
                    binaryDataStore.clearData(it.name)
                }
            }
            return@fromAction
        }
    }
}
