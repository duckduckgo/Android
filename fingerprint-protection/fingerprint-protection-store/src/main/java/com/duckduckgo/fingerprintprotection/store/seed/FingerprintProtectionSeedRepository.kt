/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.fingerprintprotection.store.seed

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionSeedEntity
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface FingerprintProtectionSeedRepository {
    var fingerprintProtectionSeedEntity: FingerprintProtectionSeedEntity
    fun storeNewSeed()
}

class RealFingerprintProtectionSeedRepository constructor(
    val database: FingerprintProtectionDatabase,
    val coroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
) : FingerprintProtectionSeedRepository {

    private val fingerprintProtectionSeedDao: FingerprintProtectionSeedDao = database.fingerprintProtectionSeedDao()
    override var fingerprintProtectionSeedEntity = FingerprintProtectionSeedEntity(seed = getRandomSeed())

    init {
        storeNewSeed()
    }

    override fun storeNewSeed() {
        coroutineScope.launch(dispatcherProvider.io()) {
            updateAll(FingerprintProtectionSeedEntity(seed = getRandomSeed()))
        }
    }

    private fun updateAll(fingerprintProtectionSeedEntity: FingerprintProtectionSeedEntity) {
        fingerprintProtectionSeedDao.updateAll(fingerprintProtectionSeedEntity)
        loadToMemory()
    }

    private fun loadToMemory() {
        fingerprintProtectionSeedEntity =
            fingerprintProtectionSeedDao.get() ?: FingerprintProtectionSeedEntity(seed = getRandomSeed())
    }

    private fun getRandomSeed(): String {
        return UUID.randomUUID().toString()
    }
}
