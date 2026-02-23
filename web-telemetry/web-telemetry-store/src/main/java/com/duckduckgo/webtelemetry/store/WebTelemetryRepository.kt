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

    fun updateWebEventsConfig(entity: WebEventsConfigEntity)
    fun getWebEventsConfigEntity(): WebEventsConfigEntity

    fun getPixelState(name: String): WebTelemetryPixelStateEntity?
    fun getAllPixelStates(): List<WebTelemetryPixelStateEntity>
    fun savePixelState(entity: WebTelemetryPixelStateEntity)
    fun deletePixelState(name: String)
    fun deleteAllPixelStates()
}

class RealWebTelemetryRepository constructor(
    database: WebTelemetryDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : WebTelemetryRepository {

    private val dao: WebTelemetryDao = database.webTelemetryDao()
    private var configEntity = WebTelemetryConfigEntity(json = EMPTY_JSON)
    private var webEventsConfigEntity = WebEventsConfigEntity(json = EMPTY_JSON)

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

    override fun updateWebEventsConfig(entity: WebEventsConfigEntity) {
        dao.updateWebEventsConfig(entity)
        webEventsConfigEntity = entity
    }

    override fun getWebEventsConfigEntity(): WebEventsConfigEntity {
        return webEventsConfigEntity
    }

    override fun getPixelState(name: String): WebTelemetryPixelStateEntity? {
        return dao.getPixelState(name)
    }

    override fun getAllPixelStates(): List<WebTelemetryPixelStateEntity> {
        return dao.getAllPixelStates()
    }

    override fun savePixelState(entity: WebTelemetryPixelStateEntity) {
        dao.insertPixelState(entity)
    }

    override fun deletePixelState(name: String) {
        dao.deletePixelState(name)
    }

    override fun deleteAllPixelStates() {
        dao.deleteAllPixelStates()
    }

    private fun loadConfigToMemory() {
        configEntity = dao.getConfig() ?: WebTelemetryConfigEntity(json = EMPTY_JSON)
        webEventsConfigEntity = dao.getWebEventsConfig() ?: WebEventsConfigEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
