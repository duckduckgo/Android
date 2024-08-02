/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.breakagereporting.impl

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface BreakageReportingRepository {
    fun updateAll(
        breakageReportingEntity: BreakageReportingEntity,
    )
    fun getBreakageReportingEntity(): BreakageReportingEntity
}

class RealBreakageReportingRepository constructor(
    database: BreakageReportingDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : BreakageReportingRepository {

    private val breakageReportingDao: BreakageReportingDao = database.breakageReportingDao()
    private var breakageReportingEntity = BreakageReportingEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(breakageReportingEntity: BreakageReportingEntity) {
        breakageReportingDao.updateAll(breakageReportingEntity)
        loadToMemory()
    }

    override fun getBreakageReportingEntity(): BreakageReportingEntity {
        return breakageReportingEntity
    }

    private fun loadToMemory() {
        breakageReportingEntity =
            breakageReportingDao.get() ?: BreakageReportingEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
