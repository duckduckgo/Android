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

import android.content.Context
import androidx.annotation.WorkerThread
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.store.BinaryDataStore
import com.duckduckgo.httpsupgrade.api.HttpsEmbeddedDataPersister
import com.duckduckgo.httpsupgrade.impl.BloomFilter.Config.PathConfig
import com.duckduckgo.httpsupgrade.impl.HttpsUpgradePixelName.CREATE_BLOOM_FILTER_ERROR
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpec.Companion.HTTPS_BINARY_FILE
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpecDao
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

interface HttpsBloomFilterFactory {
    fun create(): BloomFilter?
}

class HttpsBloomFilterFactoryImpl constructor(
    private val dao: HttpsBloomFilterSpecDao,
    private val binaryDataStore: BinaryDataStore,
    private val httpsEmbeddedDataPersister: HttpsEmbeddedDataPersister,
    private val httpsDataPersister: HttpsDataPersister,
    private val pixel: Pixel,
    private val context: Context,
) : HttpsBloomFilterFactory {

    @WorkerThread
    override fun create(): BloomFilter? {
        if (httpsEmbeddedDataPersister.shouldPersistEmbeddedData()) {
            logcat { "Https update data not found, loading embedded data" }
            httpsEmbeddedDataPersister.persistEmbeddedData()
        }

        val specification = dao.get()
        val dataPath = binaryDataStore.dataFilePath(HTTPS_BINARY_FILE)
        if (dataPath == null || specification == null || !httpsDataPersister.isPersisted(specification)) {
            logcat { "Https update data not available" }
            return null
        }

        val initialTimestamp = System.currentTimeMillis()
        logcat { "Found https data at $dataPath, building filter" }
        val bloomFilter = try {
            BloomFilter(context, PathConfig(path = dataPath, bits = specification.bitCount, maxItems = specification.totalEntries))
        } catch (t: Throwable) {
            logcat(ERROR) { "Error creating the bloom filter, ${t.asLog()}" }
            pixel.fire(CREATE_BLOOM_FILTER_ERROR)
            null
        }
        logcat { "Loading took ${System.currentTimeMillis() - initialTimestamp}ms" }

        return bloomFilter
    }
}
