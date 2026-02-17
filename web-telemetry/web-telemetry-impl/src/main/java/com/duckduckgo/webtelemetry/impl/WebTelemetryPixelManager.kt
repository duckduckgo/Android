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
import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

interface WebTelemetryPixelManager {
    /** Route a fireTelemetry event to the appropriate pixel parameters. */
    fun handleTelemetryEvent(type: String)

    /** Check all active pixels and fire any whose period+jitter has elapsed. */
    fun checkPixels()

    /** Sync pixel state: initialise new pixels, deregister removed ones. */
    fun syncPixelState()
}

class RealWebTelemetryPixelManager @Inject constructor(
    private val repository: WebTelemetryRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
    private val jitterProvider: JitterProvider,
) : WebTelemetryPixelManager {

    override fun handleTelemetryEvent(type: String) {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        val telemetryType = config.telemetryTypes.find { it.name == type && it.isEnabled } ?: return

        when (telemetryType.template) {
            "counter" -> {
                val counter = CounterTelemetryType.from(telemetryType) ?: return
                handleCounterEvent(counter)
            }
        }
    }

    private fun handleCounterEvent(counter: CounterTelemetryType) {
        for (target in counter.targets) {
            val pixelState = repository.getPixelState(target.pixel) ?: continue
            val params = parseParamsJson(pixelState.paramsJson)
            params[target.param] = (params[target.param] ?: 0) + 1
            repository.savePixelState(pixelState.copy(paramsJson = serializeParams(params)))
        }
    }

    override fun checkPixels() {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        for (pixelConfig in config.pixels) {
            val state = repository.getPixelState(pixelConfig.name) ?: continue
            val elapsedSeconds = (timeProvider.currentTimeMillis() - state.timestampMillis) / 1000.0
            val thresholdSeconds = pixelConfig.trigger.period.periodSeconds + state.jitterSeconds

            if (elapsedSeconds >= thresholdSeconds) {
                buildAndFirePixel(pixelConfig, state)

                val resetParams = pixelConfig.parameters.keys.associateWith { 0 }.toMutableMap()
                repository.savePixelState(
                    WebTelemetryPixelStateEntity(
                        pixelName = pixelConfig.name,
                        timestampMillis = timeProvider.currentTimeMillis(),
                        jitterSeconds = jitterProvider.generateJitter(pixelConfig),
                        paramsJson = serializeParams(resetParams),
                    ),
                )
            }
        }
    }

    override fun syncPixelState() {
        val config = getParsedConfig()
        if (!config.featureEnabled) {
            repository.deleteAllPixelStates()
            return
        }

        val activePixelNames = config.pixels.map { it.name }.toSet()
        val existingStates = repository.getAllPixelStates()
        val existingNames = existingStates.map { it.pixelName }.toSet()

        val needsInit = activePixelNames - existingNames
        val needsRemove = existingNames - activePixelNames

        for (pixelName in needsInit) {
            val pixelConfig = config.pixels.find { it.name == pixelName } ?: continue
            val initialParams = pixelConfig.parameters.keys.associateWith { 0 }.toMutableMap()
            repository.savePixelState(
                WebTelemetryPixelStateEntity(
                    pixelName = pixelName,
                    timestampMillis = timeProvider.currentTimeMillis(),
                    jitterSeconds = jitterProvider.generateJitter(pixelConfig),
                    paramsJson = serializeParams(initialParams),
                ),
            )
        }

        for (pixelName in needsRemove) {
            repository.deletePixelState(pixelName)
        }

        validateTelemetryTypes(config)
    }

    private fun validateTelemetryTypes(config: WebTelemetryConfigParser.ParsedConfig) {
        val activeTypes = config.telemetryTypes.filter { it.isEnabled }
        val pixelsByName = config.pixels.associateBy { it.name }

        for (type in activeTypes) {
            val targets = when (type.template) {
                "counter" -> CounterTelemetryType.from(type)?.targets ?: emptyList()
                else -> {
                    logcat(WARN) { "telemetry type '${type.name}' has unknown template '${type.template}'" }
                    continue
                }
            }

            for (target in targets) {
                val pixelConfig = pixelsByName[target.pixel]
                if (pixelConfig == null) {
                    logcat(WARN) { "telemetry type '${type.name}' targets pixel '${target.pixel}' which is not active" }
                    continue
                }
                val paramConfig = pixelConfig.parameters[target.param]
                if (paramConfig == null) {
                    logcat(WARN) { "telemetry type '${type.name}' targets param '${target.param}' which does not exist on pixel '${target.pixel}'" }
                    continue
                }
                if (type.template != paramConfig.type) {
                    logcat(WARN) {
                        "telemetry type '${type.name}' has template '${type.template}' but param '${target.param}' has type '${paramConfig.type}'"
                    }
                }
            }
        }
    }

    private fun buildAndFirePixel(pixelConfig: PixelConfig, state: WebTelemetryPixelStateEntity) {
        val currentParams = parseParamsJson(state.paramsJson)
        val pixelParams = mutableMapOf<String, String>()

        for ((paramName, paramConfig) in pixelConfig.parameters) {
            val value = currentParams[paramName] ?: 0
            if (paramConfig.type == "counter") {
                val bucket = BucketCounter.bucketCount(value, paramConfig.buckets)
                if (bucket != null) {
                    pixelParams[paramName] = bucket
                }
            }
        }

        if (pixelParams.isNotEmpty()) {
            pixel.fire(pixelName = pixelConfig.name, parameters = pixelParams)
        }
    }

    private fun getParsedConfig(): WebTelemetryConfigParser.ParsedConfig {
        return WebTelemetryConfigParser.parse(repository.getConfigEntity().json)
    }

    companion object {
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

interface JitterProvider {
    fun generateJitter(config: PixelConfig): Double
}

class RealJitterProvider @Inject constructor() : JitterProvider {
    override fun generateJitter(config: PixelConfig): Double {
        val periodConfig = config.trigger.period
        val periodSeconds = periodConfig.periodSeconds.toDouble()
        val jitterFraction = periodConfig.jitterMaxPercent / 100.0
        val halfSpread = periodSeconds * jitterFraction * 0.5
        return if (halfSpread > 0) {
            -halfSpread + Math.random() * 2 * halfSpread
        } else {
            0.0
        }
    }
}
