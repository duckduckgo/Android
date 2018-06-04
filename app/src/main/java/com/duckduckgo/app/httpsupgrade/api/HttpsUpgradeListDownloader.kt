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
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import io.reactivex.Completable
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class HttpsUpgradeListDownloader @Inject constructor(
    private val service: HttpsUpgradeListService,
    private val database: AppDatabase,
    private val httpsUpgradeDao: HttpsUpgradeDomainDao
) {

    fun downloadList(): Completable {

        Timber.d("Downloading HTTPS Upgrade data")

        return Completable.fromAction {

            val call = service.https()
            val response = call.execute()

            if (response.isCached && httpsUpgradeDao.count() != 0) {
                Timber.d("HTTPS data already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                Timber.d("Updating HTTPS upgrade list from server")

                val domains = response.body()
                if (domains == null) {
                    Timber.w("Failed to obtain HTTPS upgrade list")
                    return@fromAction
                }

                val startTime = System.currentTimeMillis()
                httpsUpgradeDao.deleteAll()
                Timber.i("Took ${System.currentTimeMillis() - startTime}ms to delete existing records")

                val chunks = domains.chunked(INSERTION_CHUNK_SIZE)
                Timber.i("Received ${domains.size} HTTPS domains; chunking by $INSERTION_CHUNK_SIZE into ${chunks.size} separate DB transactions")

                chunks.forEach {
                    database.runInTransaction {
                        httpsUpgradeDao.insertAll(it)
                    }
                }

                Timber.i("Total insertion time is ${System.currentTimeMillis() - startTime}ms")

            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }

    companion object {
        private const val INSERTION_CHUNK_SIZE = 1_000
    }
}