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

package com.duckduckgo.webevents.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.webevents.store.EventHubPixelStateEntity
import com.duckduckgo.webevents.store.EventHubRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

interface EventHubPixelManager {
    fun handleWebEvent(eventType: String)
    fun checkPixels()
    fun onConfigChanged()
}

class RealEventHubPixelManager @Inject constructor(
    private val repository: EventHubRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
) : EventHubPixelManager {

    override fun handleWebEvent(eventType: String) {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        val nowMillis = timeProvider.currentTimeMillis()

        for (pixelConfig in config.telemetry) {
            if (!pixelConfig.isEnabled) continue
            val state = repository.getPixelState(pixelConfig.name) ?: continue

            if (state.periodStartMillis == 0L || state.periodEndMillis == 0L) continue
            if (nowMillis > state.periodEndMillis) continue

            val params = parseParamsJson(state.paramsJson)
            val stopCounting = parseStopCountingJson(state.stopCountingJson)
            var changed = false

            for ((paramName, paramConfig) in pixelConfig.parameters) {
                if (paramConfig.isCounter && paramConfig.source == eventType) {
                    if (paramName in stopCounting) continue

                    val newValue = (params[paramName] ?: 0) + 1
                    params[paramName] = newValue
                    changed = true

                    if (BucketCounter.shouldStopCounting(newValue, paramConfig.buckets)) {
                        stopCounting.add(paramName)
                    }
                }
            }

            if (changed) {
                repository.savePixelState(
                    state.copy(
                        paramsJson = serializeParams(params),
                        stopCountingJson = serializeStopCounting(stopCounting),
                    ),
                )
            }
        }
    }

    override fun checkPixels() {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        val nowMillis = timeProvider.currentTimeMillis()

        for (pixelConfig in config.telemetry) {
            if (!pixelConfig.isEnabled) continue
            val state = repository.getPixelState(pixelConfig.name) ?: continue

            if (nowMillis >= state.periodEndMillis && state.periodEndMillis > 0) {
                fireTelemetry(pixelConfig, state)
            }
        }
    }

    override fun onConfigChanged() {
        val config = getParsedConfig()
        if (!config.featureEnabled) {
            repository.deleteAllPixelStates()
            return
        }

        for (pixelConfig in config.telemetry) {
            if (repository.getPixelState(pixelConfig.name) == null && pixelConfig.isEnabled) {
                startNewPeriod(pixelConfig)
            }
        }
    }

    private fun fireTelemetry(pixelConfig: TelemetryPixelConfig, state: EventHubPixelStateEntity) {
        if (state.periodStartMillis == 0L || state.periodEndMillis == 0L) return

        val pixelData = buildPixel(pixelConfig, state)

        if (pixelData.isNotEmpty()) {
            val additionalParams = mapOf(
                PARAM_ATTRIBUTION_PERIOD to calculateAttributionPeriod(
                    state.periodStartMillis,
                    pixelConfig.trigger.period,
                ).toString(),
            )
            pixel.enqueueFire(
                pixelName = pixelConfig.name,
                parameters = pixelData + additionalParams,
            )
        }

        // Deregister and re-register: resets state and picks up any config changes
        repository.deletePixelState(pixelConfig.name)

        val latestConfig = getParsedConfig()
        val latestPixelConfig = latestConfig.telemetry.find { it.name == pixelConfig.name }
        if (latestConfig.featureEnabled && latestPixelConfig != null && latestPixelConfig.isEnabled) {
            startNewPeriod(latestPixelConfig)
        }
    }

    private fun startNewPeriod(pixelConfig: TelemetryPixelConfig) {
        val nowMillis = timeProvider.currentTimeMillis()
        val periodMillis = pixelConfig.trigger.period.periodSeconds * 1000
        val initialParams = pixelConfig.parameters.keys.associateWith { 0 }.toMutableMap()

        repository.savePixelState(
            EventHubPixelStateEntity(
                pixelName = pixelConfig.name,
                periodStartMillis = nowMillis,
                periodEndMillis = nowMillis + periodMillis,
                paramsJson = serializeParams(initialParams),
                stopCountingJson = "[]",
            ),
        )
    }

    private fun buildPixel(
        pixelConfig: TelemetryPixelConfig,
        state: EventHubPixelStateEntity,
    ): Map<String, String> {
        val params = parseParamsJson(state.paramsJson)
        val pixelData = mutableMapOf<String, String>()

        for ((paramName, paramConfig) in pixelConfig.parameters) {
            val value = params[paramName] ?: 0
            if (paramConfig.isCounter) {
                val bucketName = BucketCounter.bucketCount(value, paramConfig.buckets)
                if (bucketName != null) {
                    pixelData[paramName] = bucketName
                }
            }
        }

        return pixelData
    }

    private fun getParsedConfig(): EventHubConfigParser.ParsedConfig {
        return EventHubConfigParser.parse(repository.getEventHubConfigEntity().json)
    }

    companion object {
        const val PARAM_ATTRIBUTION_PERIOD = "attributionPeriod"

        fun calculateAttributionPeriod(periodStartMillis: Long, period: TelemetryPeriodConfig): Long {
            val periodSecs = period.periodSeconds
            return toStartOfInterval(periodStartMillis, periodSecs) + periodSecs
        }

        fun toStartOfInterval(timestampMillis: Long, periodSeconds: Long): Long {
            val epochSeconds = timestampMillis / 1000
            return (epochSeconds / periodSeconds) * periodSeconds
        }

        fun parseParamsJson(json: String): MutableMap<String, Int> {
            return try {
                val obj = JSONObject(json)
                val map = mutableMapOf<String, Int>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = obj.optInt(key, 0)
                }
                map
            } catch (e: Exception) {
                mutableMapOf()
            }
        }

        fun serializeParams(params: Map<String, Int>): String {
            return JSONObject(params.mapValues { it.value as Any }).toString()
        }

        fun parseStopCountingJson(json: String): MutableSet<String> {
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { arr.optString(it) }.toMutableSet()
            } catch (e: Exception) {
                mutableSetOf()
            }
        }

        fun serializeStopCounting(set: Set<String>): String {
            return JSONArray(set.toList()).toString()
        }
    }
}

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class RealTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
