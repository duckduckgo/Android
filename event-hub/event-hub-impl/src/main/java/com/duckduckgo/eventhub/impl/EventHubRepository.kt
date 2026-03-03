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
import org.json.JSONObject

interface EventHubRepository {
    fun getPixelState(name: String): PixelState?
    fun getAllPixelStates(): List<PixelState>
    fun savePixelState(state: PixelState)
    fun deletePixelState(name: String)
    fun deleteAllPixelStates()
}

class RealEventHubRepository(
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
        pixelStateDao.insertPixelState(
            EventHubPixelStateEntity(
                pixelName = state.pixelName,
                periodStartMillis = state.periodStartMillis,
                periodEndMillis = state.periodEndMillis,
                paramsJson = serializeParams(state.params),
                configJson = EventHubConfigParser.serializePixelConfig(state.config),
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
        fun parseParamsJson(json: String): MutableMap<String, ParamState> {
            return try {
                val obj = JSONObject(json)
                val map = mutableMapOf<String, ParamState>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val paramObj = obj.optJSONObject(key)
                    if (paramObj != null) {
                        map[key] = ParamState(
                            value = paramObj.optInt("value", 0),
                            stopCounting = paramObj.optBoolean("stopCounting", false),
                        )
                    } else {
                        map[key] = ParamState(value = obj.optInt(key, 0))
                    }
                }
                map
            } catch (e: Exception) {
                mutableMapOf()
            }
        }

        fun serializeParams(params: Map<String, ParamState>): String {
            val obj = JSONObject()
            for ((key, state) in params) {
                val paramObj = JSONObject()
                paramObj.put("value", state.value)
                if (state.stopCounting) {
                    paramObj.put("stopCounting", true)
                }
                obj.put(key, paramObj)
            }
            return obj.toString()
        }
    }
}
