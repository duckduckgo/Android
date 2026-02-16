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

package com.duckduckgo.webtelemetry.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface WebTelemetryRepository {
    fun updateConfig(entity: WebTelemetryConfigEntity)
    fun getConfigEntity(): WebTelemetryConfigEntity

    fun getCounter(name: String): WebTelemetryCounterEntity?
    fun getAllCounters(): List<WebTelemetryCounterEntity>
    fun saveCounter(entity: WebTelemetryCounterEntity)
    fun deleteCounter(name: String)
    fun deleteAllCounters()
}

class RealWebTelemetryRepository constructor(
    database: WebTelemetryDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : WebTelemetryRepository {

    private val dao: WebTelemetryDao = database.webTelemetryDao()
    private var configEntity = WebTelemetryConfigEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadConfigToMemory()
            }
        }
    }

    override fun updateConfig(entity: WebTelemetryConfigEntity) {
        dao.updateConfig(entity)
        loadConfigToMemory()
    }

    override fun getConfigEntity(): WebTelemetryConfigEntity {
        return configEntity
    }

    override fun getCounter(name: String): WebTelemetryCounterEntity? {
        return dao.getCounter(name)
    }

    override fun getAllCounters(): List<WebTelemetryCounterEntity> {
        return dao.getAllCounters()
    }

    override fun saveCounter(entity: WebTelemetryCounterEntity) {
        dao.insertCounter(entity)
    }

    override fun deleteCounter(name: String) {
        dao.deleteCounter(name)
    }

    override fun deleteAllCounters() {
        dao.deleteAllCounters()
    }

    private fun loadConfigToMemory() {
        configEntity = dao.getConfig() ?: WebTelemetryConfigEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
