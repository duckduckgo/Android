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

package com.duckduckgo.fingerprintprotection.store.features.fingerprintinghardware

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintingHardwareEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface FingerprintingHardwareRepository {
    fun updateAll(
        fingerprintingHardwareEntity: FingerprintingHardwareEntity,
    )
    var fingerprintingHardwareEntity: FingerprintingHardwareEntity
}

class RealFingerprintingHardwareRepository constructor(
    val database: FingerprintProtectionDatabase,
    val coroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : FingerprintingHardwareRepository {

    private val fingerprintingHardwareDao: FingerprintingHardwareDao = database.fingerprintingHardwareDao()
    override var fingerprintingHardwareEntity = FingerprintingHardwareEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(fingerprintingHardwareEntity: FingerprintingHardwareEntity) {
        fingerprintingHardwareDao.updateAll(fingerprintingHardwareEntity)
        loadToMemory()
    }

    private fun loadToMemory() {
        fingerprintingHardwareEntity =
            fingerprintingHardwareDao.get() ?: FingerprintingHardwareEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
