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

package com.duckduckgo.webtelemetry.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.webtelemetry.store.WebTelemetryPixelStateEntity
import com.duckduckgo.webtelemetry.store.WebTelemetryRepository
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

interface EventHubPixelManager {
    /** Process a webEvent by passing it to all enabled telemetry pixels. */
    fun handleWebEvent(eventType: String)

    /** Check all pixels and fire any whose period has elapsed. */
    fun checkPixels()

    /** Called when feature config changes. Re-syncs telemetry state. */
    fun onConfigChanged()
}

class RealEventHubPixelManager @Inject constructor(
    private val repository: WebTelemetryRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
    private val staggerProvider: StaggerProvider,
) : EventHubPixelManager {

    override fun handleWebEvent(eventType: String) {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        for (pixelConfig in config.telemetry) {
            if (!pixelConfig.isEnabled) continue
            val state = repository.getPixelState(pixelConfig.name) ?: continue
            val params = parseParamsJson(state.paramsJson)
            var changed = false

            for ((paramName, paramConfig) in pixelConfig.parameters) {
                if (paramConfig.isCounter && paramConfig.source == eventType) {
                    params[paramName] = (params[paramName] ?: 0) + 1
                    changed = true
                }
            }

            if (changed) {
                repository.savePixelState(state.copy(paramsJson = serializeParams(params)))
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

            val periodMillis = pixelConfig.trigger.period.periodSeconds * 1000
            val fireAtMillis = state.periodStartMillis + periodMillis

            if (nowMillis >= fireAtMillis) {
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

        val configPixelNames = config.telemetry.map { it.name }.toSet()
        val existingStates = repository.getAllPixelStates()
        val existingNames = existingStates.map { it.pixelName }.toSet()

        // Deregister unknown
        for (name in existingNames - configPixelNames) {
            repository.deletePixelState(name)
        }

        // Register/update each configured pixel
        for (pixelConfig in config.telemetry) {
            registerTelemetry(pixelConfig)
        }
    }

    private fun registerTelemetry(pixelConfig: TelemetryPixelConfig) {
        val existing = repository.getPixelState(pixelConfig.name)

        if (!pixelConfig.isEnabled) {
            if (existing != null) {
                repository.deletePixelState(pixelConfig.name)
            }
            return
        }

        if (existing == null) {
            startNewPeriod(pixelConfig)
        }
        // If already exists, we keep the state (preserving counters mid-period)
    }

    private fun startNewPeriod(pixelConfig: TelemetryPixelConfig) {
        val initialParams = pixelConfig.parameters.keys.associateWith { 0 }.toMutableMap()
        repository.savePixelState(
            WebTelemetryPixelStateEntity(
                pixelName = pixelConfig.name,
                periodStartMillis = timeProvider.currentTimeMillis(),
                paramsJson = serializeParams(initialParams),
            ),
        )
    }

    private fun fireTelemetry(pixelConfig: TelemetryPixelConfig, state: WebTelemetryPixelStateEntity) {
        val pixelData = buildPixel(pixelConfig, state)

        // If the only parameter is "period" (no meaningful data), skip firing
        val dataParams = pixelData.filterKeys { it != PARAM_PERIOD }
        if (dataParams.isEmpty()) {
            startNewPeriod(pixelConfig)
            return
        }

        val staggerMs = staggerProvider.randomStaggerMs(pixelConfig.trigger.period.maxStaggerMins)

        // Reset counters and start new period immediately
        startNewPeriod(pixelConfig)

        // Fire the pixel (with stagger delay if applicable)
        // On Android, we use enqueueFire which persists the pixel for retry
        pixel.enqueueFire(
            pixelName = pixelConfig.name,
            parameters = pixelData,
        )
    }

    private fun buildPixel(
        pixelConfig: TelemetryPixelConfig,
        state: WebTelemetryPixelStateEntity,
    ): Map<String, String> {
        val params = parseParamsJson(state.paramsJson)
        val pixelData = mutableMapOf<String, String>()

        for ((paramName, paramConfig) in pixelConfig.parameters) {
            val value = params[paramName] ?: 0
            if (paramConfig.isCounter) {
                val bucket = BucketCounter.bucketCount(value, paramConfig.buckets)
                if (bucket != null) {
                    pixelData[paramName] = bucket
                }
            }
        }

        pixelData[PARAM_PERIOD] = toStartOfInterval(
            state.periodStartMillis,
            pixelConfig.trigger.period.periodSeconds,
        ).toString()

        return pixelData
    }

    private fun getParsedConfig(): EventHubConfigParser.ParsedConfig {
        return EventHubConfigParser.parse(repository.getConfigEntity().json)
    }

    companion object {
        const val PARAM_PERIOD = "period"

        /**
         * Normalise a timestamp to the start of its interval.
         * E.g., for a 1-day period, 2026-01-26T17:00 → 2026-01-26T00:00 (epoch seconds).
         */
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
    }
}

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class RealTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

interface StaggerProvider {
    /** Returns a random delay in milliseconds, up to [maxStaggerMins] minutes. */
    fun randomStaggerMs(maxStaggerMins: Int): Long
}

class RealStaggerProvider @Inject constructor() : StaggerProvider {
    override fun randomStaggerMs(maxStaggerMins: Int): Long {
        if (maxStaggerMins <= 0) return 0
        return (Math.random() * maxStaggerMins * 60 * 1000).toLong()
    }
}
