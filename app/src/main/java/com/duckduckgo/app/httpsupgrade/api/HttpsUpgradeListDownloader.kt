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

package com.duckduckgo.app.httpsupgrade.api

import com.duckduckgo.app.global.api.isCached
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDbWriteStatusStore
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import io.reactivex.Completable
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * For performance reasons, we can't insert all the HTTPS rows in a single transaction as it blocks other DB writes from happening for a while
 * To avoid the performance problem of a single transaction, we can chunk the data and perform many smaller transactions.
 * However, if we don't use a single transaction, there is the risk the process could die or exception be thrown while iterating through the HTTPS inserts, leaving the DB in a bad state.
 * The network cache could therefore deliver the cached value and we could skip writing to the DB thinking we'd already successfully written it all.
 *
 * To counter this, we need an external mechanism to track whether the write completed successfully or not.
 * Using this, we can detect if the write failed when we next download the data. We'll only skip the DB inserts if cache returns the list and the
 * the eTag received in the response headers matches the eTag we stored on last successful write, indicating that it finished writing successfully.
 * @see HttpsUpgradeDbWriteStatusStore for how this status flag is stored.
 */
class HttpsUpgradeListDownloader @Inject constructor(
    private val service: HttpsUpgradeListService,
    private val database: AppDatabase,
    private val httpsUpgradeDao: HttpsUpgradeDomainDao,
    private val dbWriteStatusStore: HttpsUpgradeDbWriteStatusStore
) {

    fun downloadList(chunkSize: Int = INSERTION_CHUNK_SIZE): Completable {

        Timber.d("Downloading HTTPS Upgrade data")

        return Completable.fromAction {

            val call = service.https()
            val response = call.execute()
            val eTag = response.headers().get("etag")

            if (response.isCached && dbWriteStatusStore.isMatchingETag(eTag)) {
                Timber.d("HTTPS data already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                Timber.d("Updating HTTPS upgrade list from server")

                val domains = response.body() ?: throw IllegalStateException("Failed to obtain HTTPS upgrade list")

                val startTime = System.currentTimeMillis()

                httpsUpgradeDao.deleteAll()
                Timber.v("Took ${System.currentTimeMillis() - startTime}ms to delete existing records")

                val chunks = domains.chunked(chunkSize)
                Timber.i("Received ${domains.size} HTTPS domains; chunking by $chunkSize into ${chunks.size} separate DB transactions")

                chunks.forEach {
                    database.runInTransaction {
                        httpsUpgradeDao.insertAll(it)
                    }
                }

                dbWriteStatusStore.saveETag(eTag)
                Timber.i("Successfully wrote HTTPS data; took ${System.currentTimeMillis() - startTime}ms")

            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }

    companion object {
        private const val INSERTION_CHUNK_SIZE = 1_000
    }
}