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

import com.duckduckgo.common.utils.store.BinaryDataStore
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpec
import com.duckduckgo.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.httpsupgrade.store.HttpsFalsePositiveDomain
import com.duckduckgo.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.httpsupgrade.store.HttpsUpgradeDatabase
import logcat.logcat
import java.io.IOException

class HttpsDataPersister constructor(
    private val binaryDataStore: BinaryDataStore,
    private val httpsBloomSpecDao: HttpsBloomFilterSpecDao,
    private val httpsFalsePositivesDao: HttpsFalsePositivesDao,
    private val httpsUpgradeDatabase: HttpsUpgradeDatabase,
) {

    fun persistBloomFilter(
        specification: HttpsBloomFilterSpec,
        bytes: ByteArray,
        falsePositives: List<HttpsFalsePositiveDomain>,
    ) {
        runCatching {
            httpsUpgradeDatabase.runInTransaction {
                persistBloomFilter(specification, bytes)
                persistFalsePositives(falsePositives)
            }
        }
    }

    fun persistBloomFilter(
        specification: HttpsBloomFilterSpec,
        bytes: ByteArray,
    ) {
        if (!binaryDataStore.verifyCheckSum(bytes, specification.sha256)) {
            throw IOException("Https binary has incorrect sha, throwing away file")
        }

        logcat { "Updating https bloom data store with new data" }
        httpsUpgradeDatabase.runInTransaction {
            httpsBloomSpecDao.insert(specification)
            binaryDataStore.saveData(HttpsBloomFilterSpec.HTTPS_BINARY_FILE, bytes)
        }
    }

    fun persistFalsePositives(falsePositives: List<HttpsFalsePositiveDomain>) {
        httpsFalsePositivesDao.updateAll(falsePositives)
    }

    fun isPersisted(specification: HttpsBloomFilterSpec): Boolean {
        return specification == httpsBloomSpecDao.get() && binaryDataStore.verifyCheckSum(
            HttpsBloomFilterSpec.HTTPS_BINARY_FILE,
            specification.sha256,
        )
    }
}
