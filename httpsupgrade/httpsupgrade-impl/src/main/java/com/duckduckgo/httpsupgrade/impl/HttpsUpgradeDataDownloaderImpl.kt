/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.httpsupgrade.impl

import com.duckduckgo.app.global.extensions.isCached
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.httpsupgrade.api.HttpsUpgradeDataDownloader
import com.duckduckgo.httpsupgrade.api.HttpsUpgrader
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.Completable
import io.reactivex.Completable.fromAction
import java.io.IOException
import javax.inject.Inject
import logcat.logcat

@ContributesBinding(AppScope::class)
class HttpsUpgradeDataDownloaderImpl @Inject constructor(
    private val service: HttpsUpgradeService,
    private val httpsUpgrader: HttpsUpgrader,
    private val dataPersister: HttpsDataPersister,
    private val bloomFalsePositivesDao: com.duckduckgo.httpsupgrade.store.HttpsFalsePositivesDao,
) : HttpsUpgradeDataDownloader {

    override fun download(): Completable {
        val filter = service.httpsBloomFilterSpec()
            .flatMapCompletable {
                downloadBloomFilter(it)
            }
        val falsePositives = downloadFalsePositives()

        return Completable.mergeDelayError(listOf(filter, falsePositives))
            .doOnComplete {
                logcat { "Https download task completed successfully" }
            }
    }

    private fun downloadBloomFilter(specification: com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpec): Completable {
        return fromAction {
            logcat { "Downloading https bloom filter binary" }

            if (dataPersister.isPersisted(specification)) {
                logcat { "Https bloom data already stored for this spec" }
                return@fromAction
            }

            val call = service.httpsBloomFilter()
            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }

            val bytes = response.body()!!.bytes()
            dataPersister.persistBloomFilter(specification, bytes)
            httpsUpgrader.reloadData()
        }
    }

    private fun downloadFalsePositives(): Completable {
        logcat { "Downloading HTTPS false positives" }
        return fromAction {
            val call = service.falsePositives()
            val response = call.execute()

            if (response.isCached && bloomFalsePositivesDao.count() > 0) {
                logcat { "Https false positives already cached and stored" }
                return@fromAction
            }

            if (response.isSuccessful) {
                val falsePositives = response.body()!!
                logcat { "Updating https false positives with new data" }
                dataPersister.persistFalsePositives(falsePositives)
            } else {
                throw IOException("Status: ${response.code()} - ${response.errorBody()?.string()}")
            }
        }
    }
}
