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
import com.duckduckgo.webtelemetry.store.WebTelemetryCounterEntity
import com.duckduckgo.webtelemetry.store.WebTelemetryRepository
import logcat.logcat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface WebTelemetryCounterManager {
    fun handleTelemetryEvent(type: String)
    fun checkAndFireCounters()
    fun syncTelemetryState()
}

class RealWebTelemetryCounterManager @Inject constructor(
    private val repository: WebTelemetryRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
) : WebTelemetryCounterManager {

    override fun handleTelemetryEvent(type: String) {
        val activeTypes = getActiveTelemetryTypes()
        val config = activeTypes.find { it.name == type } ?: return

        if (!config.isCounter) return

        val existingCounter = repository.getCounter(type)
        if (existingCounter != null) {
            repository.saveCounter(existingCounter.copy(counter = existingCounter.counter + 1))
        } else {
            repository.saveCounter(
                WebTelemetryCounterEntity(
                    name = type,
                    counter = 1,
                    timestampMillis = timeProvider.currentTimeMillis(),
                ),
            )
        }
    }

    override fun checkAndFireCounters() {
        val activeTypes = getActiveTelemetryTypes()
        val activeCounterConfigs = activeTypes.filter { it.isCounter }

        for (config in activeCounterConfigs) {
            val counterEntity = repository.getCounter(config.name) ?: continue
            val elapsed = timeProvider.currentTimeMillis() - counterEntity.timestampMillis

            val periodMillis = when (config.period) {
                "day" -> TimeUnit.DAYS.toMillis(1)
                "week" -> TimeUnit.DAYS.toMillis(7)
                else -> continue
            }

            if (elapsed >= periodMillis) {
                fireCounterPixel(config, counterEntity.counter)
                repository.saveCounter(
                    WebTelemetryCounterEntity(
                        name = config.name,
                        counter = 0,
                        timestampMillis = timeProvider.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun syncTelemetryState() {
        val activeTypes = getActiveTelemetryTypes()
        val activeNames = activeTypes.map { it.name }.toSet()
        val existingCounters = repository.getAllCounters()
        val existingNames = existingCounters.map { it.name }.toSet()

        val needsEnable = activeNames - existingNames
        val needsDisable = existingNames - activeNames

        for (name in needsEnable) {
            val config = activeTypes.find { it.name == name } ?: continue
            if (config.isCounter) {
                repository.saveCounter(
                    WebTelemetryCounterEntity(
                        name = name,
                        counter = 0,
                        timestampMillis = timeProvider.currentTimeMillis(),
                    ),
                )
            }
        }

        for (name in needsDisable) {
            repository.deleteCounter(name)
        }
    }

    private fun getActiveTelemetryTypes(): List<TelemetryTypeConfig> {
        val configJson = repository.getConfigEntity().json
        return WebTelemetryConfigParser.parseActiveTelemetryTypes(configJson)
    }

    private fun fireCounterPixel(config: TelemetryTypeConfig, count: Int) {
        val bucket = BucketCounter.bucketCount(count, config.buckets) ?: run {
            logcat { "No matching bucket for count=$count in type=${config.name}" }
            return
        }
        pixel.fire(
            pixelName = config.pixel,
            parameters = mapOf(PARAM_COUNT to bucket),
        )
    }

    companion object {
        const val PARAM_COUNT = "count"
    }
}

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class RealTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
