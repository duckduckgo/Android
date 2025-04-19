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

package com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintingScreenSizeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface FingerprintingScreenSizeRepository {
    fun updateAll(
        fingerprintingScreenSizeEntity: FingerprintingScreenSizeEntity,
    )
    var fingerprintingScreenSizeEntity: FingerprintingScreenSizeEntity
}

class RealFingerprintingScreenSizeRepository constructor(
    val database: FingerprintProtectionDatabase,
    val coroutineScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : FingerprintingScreenSizeRepository {

    private val fingerprintingScreenSizeDao: FingerprintingScreenSizeDao = database.fingerprintingScreenSizeDao()
    override var fingerprintingScreenSizeEntity = FingerprintingScreenSizeEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(fingerprintingScreenSizeEntity: FingerprintingScreenSizeEntity) {
        fingerprintingScreenSizeDao.updateAll(fingerprintingScreenSizeEntity)
        loadToMemory()
    }

    private fun loadToMemory() {
        fingerprintingScreenSizeEntity =
            fingerprintingScreenSizeDao.get() ?: FingerprintingScreenSizeEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
