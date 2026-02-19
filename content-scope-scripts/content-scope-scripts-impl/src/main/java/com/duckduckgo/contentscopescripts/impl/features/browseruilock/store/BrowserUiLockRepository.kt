/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface BrowserUiLockRepository {
    fun updateAll(browserUiLockEntity: BrowserUiLockEntity)
    fun getBrowserUiLockEntity(): BrowserUiLockEntity
}

class RealBrowserUiLockRepository constructor(
    database: BrowserUiLockDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : BrowserUiLockRepository {

    private val browserUiLockDao: BrowserUiLockDao = database.browserUiLockDao()
    private var browserUiLockEntity = BrowserUiLockEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(browserUiLockEntity: BrowserUiLockEntity) {
        browserUiLockDao.updateAll(browserUiLockEntity)
        loadToMemory()
    }

    override fun getBrowserUiLockEntity(): BrowserUiLockEntity {
        return browserUiLockEntity
    }

    private fun loadToMemory() {
        browserUiLockEntity =
            browserUiLockDao.get() ?: BrowserUiLockEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
