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
import com.duckduckgo.app.httpsupgrade.store.HttpsDataPersister
import timber.log.Timber
import javax.inject.Inject

interface HttpsBloomFilterFactory {
    fun create(): BloomFilter?
}

class HttpsBloomFilterFactoryImpl @Inject constructor(
    private val dao: HttpsBloomFilterSpecDao,
    private val binaryDataStore: BinaryDataStore,
    private val persister: HttpsDataPersister
) : HttpsBloomFilterFactory {

    @WorkerThread
    override fun create(): BloomFilter? {

        if (!persister.isPersisted()) {
            Timber.d("Https update data not found, loading embedded data")
            persister.persistEmbeddedData()
        }

        val specification = dao.get()
        val dataPath = binaryDataStore.dataFilePath(HTTPS_BINARY_FILE)
        if (dataPath == null || specification == null || !persister.isPersisted(specification)) {
            Timber.d("Embedded https update data failed to load")
            return null
        }

        val initialTimestamp = System.currentTimeMillis()
        Timber.d("Found https data at $dataPath, building filter")
        val bloomFilter = BloomFilter(dataPath, specification.bitCount, specification.totalEntries)
        Timber.v("Loading took ${System.currentTimeMillis() - initialTimestamp}ms")

        return bloomFilter
    }
}
