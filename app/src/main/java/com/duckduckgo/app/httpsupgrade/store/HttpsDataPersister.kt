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

package com.duckduckgo.app.httpsupgrade.store

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.duckduckgo.app.httpsupgrade.model.HttpsFalsePositiveDomain
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class HttpsDataPersister @Inject constructor(
    private val binaryDataStore: BinaryDataStore,
    private val httpsBloomSpecDao: HttpsBloomFilterSpecDao,
    private val httpsFalsePositivesDao: HttpsFalsePositivesDao,
    private val appDatabase: AppDatabase,
    private val context: Context,
    private val moshi: Moshi
) {

    fun persistBloomFilter(specification: HttpsBloomFilterSpec, bytes: ByteArray, falsePositives: List<HttpsFalsePositiveDomain>) {
        appDatabase.runInTransaction {
            persistBloomFilter(specification, bytes)
            persistFalsePositives(falsePositives)
        }
    }

    fun persistBloomFilter(specification: HttpsBloomFilterSpec, bytes: ByteArray) {
        if (!binaryDataStore.verifyCheckSum(bytes, specification.sha256)) {
            throw IOException("Https binary has incorrect sha, throwing away file")
        }

        Timber.d("Updating https bloom data store with new data")
        appDatabase.runInTransaction {
            httpsBloomSpecDao.insert(specification)
            binaryDataStore.saveData(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, bytes)
        }
    }

    fun persistFalsePositives(falsePositives: List<HttpsFalsePositiveDomain>) {
        httpsFalsePositivesDao.updateAll(falsePositives)
    }

    fun persistEmbeddedData() {
        Timber.d("Updating https data from embedded files")
        val specJson = context.resources.openRawResource(R.raw.https_mobile_v2_bloom_spec).bufferedReader().use { it.readText() }
        val specAdapter = moshi.adapter(HttpsBloomFilterSpec::class.java)

        val falsePositivesJson = context.resources.openRawResource(R.raw.https_mobile_v2_false_positives).bufferedReader().use { it.readText() }
        val falsePositivesType = Types.newParameterizedType(List::class.java, HttpsFalsePositiveDomain::class.java)
        val falsePositivesAdapter: JsonAdapter<List<HttpsFalsePositiveDomain>> = moshi.adapter(falsePositivesType)

        val bytes = context.resources.openRawResource(R.raw.https_mobile_v2_bloom).use { it.readBytes() }
        persistBloomFilter(specAdapter.fromJson(specJson)!!, bytes, falsePositivesAdapter.fromJson(falsePositivesJson)!!)
    }

    fun isPersisted(specification: HttpsBloomFilterSpec): Boolean {
        return specification == httpsBloomSpecDao.get() && binaryDataStore.verifyCheckSum(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, specification.sha256)
    }

    fun isPersisted(): Boolean {
        val specification = httpsBloomSpecDao.get() ?: return false
        return binaryDataStore.verifyCheckSum(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, specification.sha256)
    }
}
