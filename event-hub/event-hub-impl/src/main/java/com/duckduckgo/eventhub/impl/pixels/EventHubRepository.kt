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

package com.duckduckgo.eventhub.impl.pixels

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateDao
import com.duckduckgo.eventhub.impl.pixels.store.EventHubPixelStateEntity
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.SingleInstanceIn
import javax.inject.Inject

interface EventHubRepository {
    fun getPixelState(name: String): PixelState?
    fun getAllPixelStates(): List<PixelState>
    fun savePixelState(state: PixelState)
    fun deletePixelState(name: String)
    fun deleteAllPixelStates()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealEventHubRepository @Inject constructor(
    private val pixelStateDao: EventHubPixelStateDao,
) : EventHubRepository {

    override fun getPixelState(name: String): PixelState? {
        val entity = pixelStateDao.getPixelState(name) ?: return null
        return entityToPixelState(entity)
    }

    override fun getAllPixelStates(): List<PixelState> {
        return pixelStateDao.getAllPixelStates().mapNotNull { entityToPixelState(it) }
    }

    override fun savePixelState(state: PixelState) {
        val configJson = EventHubConfigParser.serializePixelConfig(state.config) ?: return
        pixelStateDao.insertPixelState(
            EventHubPixelStateEntity(
                pixelName = state.pixelName,
                periodStartMillis = state.periodStartMillis,
                periodEndMillis = state.periodEndMillis,
                paramsJson = serializeParams(state.params),
                configJson = configJson,
            ),
        )
    }

    override fun deletePixelState(name: String) = pixelStateDao.deletePixelState(name)
    override fun deleteAllPixelStates() = pixelStateDao.deleteAllPixelStates()

    private fun entityToPixelState(entity: EventHubPixelStateEntity): PixelState? {
        val config = EventHubConfigParser.parseSinglePixelConfig(entity.pixelName, entity.configJson) ?: return null
        return PixelState(
            pixelName = entity.pixelName,
            periodStartMillis = entity.periodStartMillis,
            periodEndMillis = entity.periodEndMillis,
            config = config,
            params = parseParamsJson(entity.paramsJson),
        )
    }

    companion object {
        private val paramsAdapter by lazy {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val paramsType = Types.newParameterizedType(Map::class.java, String::class.java, ParamState::class.java)
            moshi.adapter<Map<String, ParamState>>(paramsType).lenient()
        }

        fun parseParamsJson(json: String): Map<String, ParamState> {
            return try {
                paramsAdapter.fromJson(json) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }

        fun serializeParams(params: Map<String, ParamState>): String {
            return paramsAdapter.toJson(params)
        }
    }
}
