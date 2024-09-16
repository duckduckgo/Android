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

package com.duckduckgo.runtimechecks.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface RuntimeChecksRepository {
    fun updateAll(
        runtimeChecksEntity: RuntimeChecksEntity,
    )
    fun getRuntimeChecksEntity(): RuntimeChecksEntity
}

class RealRuntimeChecksRepository constructor(
    database: RuntimeChecksDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : RuntimeChecksRepository {

    private val runtimeChecksDao: RuntimeChecksDao = database.runtimeChecksDao()
    private var runtimeChecksEntity = RuntimeChecksEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(runtimeChecksEntity: RuntimeChecksEntity) {
        runtimeChecksDao.updateAll(runtimeChecksEntity)
        loadToMemory()
    }

    override fun getRuntimeChecksEntity(): RuntimeChecksEntity {
        return runtimeChecksEntity
    }

    private fun loadToMemory() {
        runtimeChecksEntity =
            runtimeChecksDao.get() ?: RuntimeChecksEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
