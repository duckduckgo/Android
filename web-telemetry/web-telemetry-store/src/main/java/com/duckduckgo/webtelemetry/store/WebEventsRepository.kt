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

interface WebEventsRepository {
    fun updateEventHubConfig(entity: EventHubConfigEntity)
    fun getEventHubConfigEntity(): EventHubConfigEntity

    fun updateWebEventsFeatureConfig(entity: WebEventsFeatureConfigEntity)
    fun getWebEventsFeatureConfigEntity(): WebEventsFeatureConfigEntity

    fun getPixelState(name: String): WebEventsPixelStateEntity?
    fun getAllPixelStates(): List<WebEventsPixelStateEntity>
    fun savePixelState(entity: WebEventsPixelStateEntity)
    fun deletePixelState(name: String)
    fun deleteAllPixelStates()
}

class RealWebEventsRepository constructor(
    database: WebEventsDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : WebEventsRepository {

    private val dao: WebEventsDao = database.webEventsDao()
    private var eventHubConfigEntity = EventHubConfigEntity(json = EMPTY_JSON)
    private var webEventsFeatureConfigEntity = WebEventsFeatureConfigEntity(json = EMPTY_JSON)

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadConfigToMemory()
            }
        }
    }

    override fun updateEventHubConfig(entity: EventHubConfigEntity) {
        dao.updateEventHubConfig(entity)
        eventHubConfigEntity = entity
    }

    override fun getEventHubConfigEntity(): EventHubConfigEntity {
        return eventHubConfigEntity
    }

    override fun updateWebEventsFeatureConfig(entity: WebEventsFeatureConfigEntity) {
        dao.updateWebEventsFeatureConfig(entity)
        webEventsFeatureConfigEntity = entity
    }

    override fun getWebEventsFeatureConfigEntity(): WebEventsFeatureConfigEntity {
        return webEventsFeatureConfigEntity
    }

    override fun getPixelState(name: String): WebEventsPixelStateEntity? {
        return dao.getPixelState(name)
    }

    override fun getAllPixelStates(): List<WebEventsPixelStateEntity> {
        return dao.getAllPixelStates()
    }

    override fun savePixelState(entity: WebEventsPixelStateEntity) {
        dao.insertPixelState(entity)
    }

    override fun deletePixelState(name: String) {
        dao.deletePixelState(name)
    }

    override fun deleteAllPixelStates() {
        dao.deleteAllPixelStates()
    }

    private fun loadConfigToMemory() {
        eventHubConfigEntity = dao.getEventHubConfig() ?: EventHubConfigEntity(json = EMPTY_JSON)
        webEventsFeatureConfigEntity = dao.getWebEventsFeatureConfig() ?: WebEventsFeatureConfigEntity(json = EMPTY_JSON)
    }

    companion object {
        const val EMPTY_JSON = "{}"
    }
}
