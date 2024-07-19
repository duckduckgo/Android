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

package com.duckduckgo.performancemetrics.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface PerformanceMetricsRepository {
    fun updateAll(
        performanceMetricsEntity: PerformanceMetricsEntity,
    )
    fun getPerformanceMetricsEntity(): PerformanceMetricsEntity
}

class RealPerformanceMetricsRepository constructor(
    database: PerformanceMetricsDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : PerformanceMetricsRepository {

    private val performanceMetricsDao: PerformanceMetricsDao = database.performanceMetricsDao()
    private var performanceMetricsEntity = PerformanceMetricsEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(performanceMetricsEntity: PerformanceMetricsEntity) {
        performanceMetricsDao.updateAll(performanceMetricsEntity)
        loadToMemory()
    }

    override fun getPerformanceMetricsEntity(): PerformanceMetricsEntity {
        return performanceMetricsEntity
    }

    private fun loadToMemory() {
        performanceMetricsEntity =
            performanceMetricsDao.get() ?: PerformanceMetricsEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
