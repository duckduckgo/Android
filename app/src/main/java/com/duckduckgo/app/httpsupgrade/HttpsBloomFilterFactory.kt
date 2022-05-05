/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec.Companion.HTTPS_BINARY_FILE
import com.duckduckgo.app.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.app.httpsupgrade.store.HttpsEmbeddedDataPersister
import com.duckduckgo.app.httpsupgrade.store.HttpsDataPersister
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

interface HttpsBloomFilterFactory {
    fun create(): BloomFilter?
}

@ContributesBinding(AppScope::class)
class HttpsBloomFilterFactoryImpl @Inject constructor(
    private val dao: HttpsBloomFilterSpecDao,
    private val binaryDataStore: BinaryDataStore,
    private val httpsEmbeddedDataPersister: HttpsEmbeddedDataPersister,
    private val httpsDataPersister: HttpsDataPersister,
    private val pixel: Pixel,
) : HttpsBloomFilterFactory {

    @WorkerThread
    override fun create(): BloomFilter? {

        if (httpsEmbeddedDataPersister.shouldPersistEmbeddedData()) {
            Timber.d("Https update data not found, loading embedded data")
            httpsEmbeddedDataPersister.persistEmbeddedData()
        }

        val specification = dao.get()
        val dataPath = binaryDataStore.dataFilePath(HTTPS_BINARY_FILE)
        if (dataPath == null || specification == null || !httpsDataPersister.isPersisted(specification)) {
            Timber.d("Https update data not available")
            return null
        }

        val initialTimestamp = System.currentTimeMillis()
        Timber.d("Found https data at $dataPath, building filter")
        val bloomFilter = try {
            BloomFilter(dataPath, specification.bitCount, specification.totalEntries)
        } catch (t: Throwable) {
            Timber.e(t, "Error creating the bloom filter")
            pixel.fire(AppPixelName.CREATE_BLOOM_FILTER_ERROR)
            null
        }
        Timber.v("Loading took ${System.currentTimeMillis() - initialTimestamp}ms")

        return bloomFilter
    }
}
