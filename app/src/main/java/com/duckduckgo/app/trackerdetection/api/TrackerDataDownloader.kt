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

import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.extensions.extractETag
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.trackerdetection.Client.ClientName.*
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import io.reactivex.Completable
import java.io.IOException
import javax.inject.Inject
import okhttp3.Headers
import timber.log.Timber

class TrackerDataDownloader
@Inject
constructor(
    private val trackerListService: TrackerListService,
    private val binaryDataStore: BinaryDataStore,
    private val trackerDataLoader: TrackerDataLoader,
    private val appDatabase: AppDatabase,
    private val metadataDao: TdsMetadataDao
) {

    fun downloadTds(): Completable {

        return Completable.fromAction {
            Timber.d("Downloading tds.json")

            val call = trackerListService.tds()
            val response = call.execute()

            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val body = response.body()!!
            val eTag = response.headers().extractETag()
            val oldEtag = metadataDao.eTag()
            if (eTag != oldEtag) {
                Timber.d("Updating tds data from server")
                appDatabase.runInTransaction {
                    trackerDataLoader.persistTds(eTag, body)
                    trackerDataLoader.loadTrackers()
                }
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

fun Headers.extractETag(): String {
    return this["eTag"]
        ?.removePrefix("W/")
        ?.removeSurrounding("\"", "\"")
        .orEmpty() // removes weak eTag validator
}
