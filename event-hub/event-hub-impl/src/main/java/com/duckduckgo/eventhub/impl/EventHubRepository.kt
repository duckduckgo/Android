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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.eventhub.impl.store.EventHubPixelStateDao
import com.duckduckgo.eventhub.impl.store.EventHubPixelStateEntity

interface EventHubRepository {
    fun getEventHubConfigJson(): String
    fun setEventHubConfigJson(value: String)

    fun getWebEventsConfigJson(): String
    fun setWebEventsConfigJson(value: String)

    fun getPixelState(name: String): EventHubPixelStateEntity?
    fun getAllPixelStates(): List<EventHubPixelStateEntity>
    fun savePixelState(entity: EventHubPixelStateEntity)
    fun deletePixelState(name: String)
    fun deleteAllPixelStates()
}

class RealEventHubRepository(
    private val dataStore: EventHubDataStore,
    private val pixelStateDao: EventHubPixelStateDao,
) : EventHubRepository {

    override fun getEventHubConfigJson(): String = dataStore.getEventHubConfigJson()
    override fun setEventHubConfigJson(value: String) = dataStore.setEventHubConfigJson(value)

    override fun getWebEventsConfigJson(): String = dataStore.getWebEventsConfigJson()
    override fun setWebEventsConfigJson(value: String) = dataStore.setWebEventsConfigJson(value)

    override fun getPixelState(name: String): EventHubPixelStateEntity? = pixelStateDao.getPixelState(name)
    override fun getAllPixelStates(): List<EventHubPixelStateEntity> = pixelStateDao.getAllPixelStates()
    override fun savePixelState(entity: EventHubPixelStateEntity) = pixelStateDao.insertPixelState(entity)
    override fun deletePixelState(name: String) = pixelStateDao.deletePixelState(name)
    override fun deleteAllPixelStates() = pixelStateDao.deleteAllPixelStates()
}
