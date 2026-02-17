/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.webdetection.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface WebDetectionRepository {
    fun updateAll(entity: WebDetectionEntity)
    fun getWebDetectionEntity(): WebDetectionEntity
}

class RealWebDetectionRepository constructor(
    database: WebDetectionDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : WebDetectionRepository {

    private val dao: WebDetectionDao = database.webDetectionDao()
    private var entity = WebDetectionEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(entity: WebDetectionEntity) {
        dao.updateAll(entity)
        loadToMemory()
    }

    override fun getWebDetectionEntity(): WebDetectionEntity {
        return entity
    }

    private fun loadToMemory() {
        entity = dao.get() ?: WebDetectionEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
