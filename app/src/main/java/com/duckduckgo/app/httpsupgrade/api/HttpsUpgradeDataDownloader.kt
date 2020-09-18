/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.httpsupgrade.db.HttpsBloomFilterSpecDao
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec.Companion.HTTPS_BINARY_FILE
import io.reactivex.Completable
import io.reactivex.Completable.fromAction
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class HttpsUpgradeDataDownloader @Inject constructor(
    private val service: HttpsUpgradeService,
    private val httpsUpgrader: HttpsUpgrader,
    private val httpsBloomSpecDao: HttpsBloomFilterSpecDao,
    private val bloomFalsePositivesDao: HttpsWhitelistDao,
    private val binaryDataStore: BinaryDataStore,
    private val appDatabase: AppDatabase
) {

    fun download(): Completable {

        val filter = service.httpsBloomFilterSpec()
            .flatMapCompletable {
                downloadBloomFilter(it)
            }
        val whitelist = downloadWhitelist()

        return Completable.mergeDelayError(listOf(filter, whitelist))
            .doOnComplete {
                Timber.i("Https download task completed successfully")
            }
    }

    private fun downloadBloomFilter(specification: HttpsBloomFilterSpec): Completable {
        return fromAction {

            Timber.d("Downloading https bloom filter binary")

            if (specification == httpsBloomSpecDao.get() && binaryDataStore.verifyCheckSum(HTTPS_BINARY_FILE, specification.sha256)) {
                Timber.d("Https bloom data already stored for this spec")
                return@fromAction
            }

            val call = service.httpsBloomFilter()
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val bytes = response.body()!!.bytes()
            if (!binaryDataStore.verifyCheckSum(bytes, specification.sha256)) {
                throw IOException("Https binary has incorrect sha, throwing away file")
            }

            Timber.d("Updating https bloom data store with new data")
            appDatabase.runInTransaction {
                httpsBloomSpecDao.insert(specification)
                binaryDataStore.saveData(HTTPS_BINARY_FILE, bytes)
            }
            httpsUpgrader.reloadData()
        }
    }

    private fun downloadWhitelist(): Completable {

        Timber.d("Downloading HTTPS whitelist")
        return fromAction {

            val call = service.whitelist()
            val response = call.execute()

            if (response.isCached && bloomFalsePositivesDao.count() > 0) {
                Timber.d("Https whitelist already cached and stored")
                return@fromAction
            }

            if (response.isSuccessful) {
                val whitelist = response.body()!!
                Timber.d("Updating https whitelist with new data")
                bloomFalsePositivesDao.updateAll(whitelist)
            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }

    }
}
