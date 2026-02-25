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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.eventhub.api.EventHubPixelManager
import com.duckduckgo.eventhub.api.WebEventContext
import com.duckduckgo.eventhub.store.EventHubPixelStateEntity
import com.duckduckgo.eventhub.store.EventHubRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority.DEBUG
import logcat.LogPriority.VERBOSE
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class RealEventHubPixelManager @Inject constructor(
    private val repository: EventHubRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : EventHubPixelManager {

    private val dedupState = ConcurrentHashMap<String, String>()
    private val scheduledTimers = ConcurrentHashMap<String, Job>()

    override fun handleWebEvent(eventType: String, context: WebEventContext) {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        val tabId = context.tabId
        val documentUrl = context.documentUrl
        val nowMillis = timeProvider.currentTimeMillis()

        for (state in repository.getAllPixelStates()) {
            val storedConfig = EventHubConfigParser.parseSinglePixelConfig(state.pixelName, state.configJson) ?: continue

            if (nowMillis > state.periodEndMillis) continue

            val params = parseParamsJson(state.paramsJson)
            val stopCounting = parseStopCountingJson(state.stopCountingJson)
            var changed = false

            for ((paramName, paramConfig) in storedConfig.parameters) {
                if (paramConfig.isCounter && paramConfig.source == eventType) {
                    if (paramName in stopCounting) continue
                    if (isDuplicateEvent(storedConfig.name, paramName, eventType, tabId, documentUrl)) continue

                    changed = true
                    val currentValue = params[paramName] ?: 0
                    if (BucketCounter.shouldStopCounting(currentValue, paramConfig.buckets)) {
                        stopCounting.add(paramName)
                        logcat(VERBOSE) { "EventHub: ${storedConfig.name}.$paramName already at max bucket, stopCounting" }
                        continue
                    }

                    val newValue = currentValue + 1
                    params[paramName] = newValue
                    logcat(VERBOSE) { "EventHub: ${storedConfig.name}.$paramName incremented to $newValue" }

                    if (BucketCounter.shouldStopCounting(newValue, paramConfig.buckets)) {
                        stopCounting.add(paramName)
                        logcat(VERBOSE) { "EventHub: ${storedConfig.name}.$paramName reached max bucket, stopCounting" }
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

    private fun isDuplicateEvent(pixelName: String, paramName: String, source: String, tabId: String, documentUrl: String): Boolean {
        if (tabId.isEmpty() || documentUrl.isEmpty()) return false
        val key = "$pixelName:$paramName:$source:$tabId"
        val lastUrl = dedupState[key]
        if (lastUrl == documentUrl) {
            logcat(VERBOSE) { "EventHub: dedup $key (same page: $documentUrl)" }
            return true
        }
        dedupState[key] = documentUrl
        return false
    }

    /**
     * Check all pixel states and fire any whose period has elapsed.
     * After firing, starts a new period and schedules the next fire.
     * Called on app foreground to catch pixels that elapsed while backgrounded.
     */
    fun checkPixels() {
        val config = getParsedConfig()
        if (!config.featureEnabled) return

        val nowMillis = timeProvider.currentTimeMillis()

        for (state in repository.getAllPixelStates()) {
            val storedConfig = EventHubConfigParser.parseSinglePixelConfig(state.pixelName, state.configJson) ?: continue

            if (nowMillis >= state.periodEndMillis) {
                fireTelemetry(storedConfig, state)
            } else {
                scheduleFireTelemetry(state.pixelName, state.periodEndMillis - nowMillis)
            }
        }
    }

    fun onConfigChanged() {
        val config = getParsedConfig()
        if (!config.featureEnabled) {
            logcat(DEBUG) { "EventHub: feature disabled, clearing all pixel states" }
            cancelAllTimers()
            repository.deleteAllPixelStates()
            return
        }

        logcat(DEBUG) { "EventHub: onConfigChanged — feature enabled, ${config.telemetry.size} telemetry pixel(s) in config" }
        for (pixelConfig in config.telemetry) {
            if (repository.getPixelState(pixelConfig.name) == null && pixelConfig.isEnabled) {
                logcat(DEBUG) { "EventHub: registering pixel ${pixelConfig.name}" }
                startNewPeriod(pixelConfig)
            }
        }
    }

    fun scheduleFireTelemetry(pixelName: String, delayMillis: Long) {
        if (scheduledTimers.containsKey(pixelName)) {
            logcat(VERBOSE) { "EventHub: timer already scheduled for $pixelName, skipping" }
            return
        }

        logcat(VERBOSE) { "EventHub: scheduling fire for $pixelName in ${delayMillis}ms" }
        val job = appCoroutineScope.launch(dispatcherProvider.io()) {
            delay(delayMillis)
            scheduledTimers.remove(pixelName)

            val config = getParsedConfig()
            if (!config.featureEnabled) return@launch

            val state = repository.getPixelState(pixelName) ?: return@launch
            val storedConfig = EventHubConfigParser.parseSinglePixelConfig(state.pixelName, state.configJson) ?: return@launch

            fireTelemetry(storedConfig, state)
        }
        scheduledTimers[pixelName] = job
    }

    fun hasScheduledTimer(pixelName: String): Boolean = scheduledTimers.containsKey(pixelName)

    fun cancelScheduledFire(pixelName: String) {
        scheduledTimers.remove(pixelName)?.let { job ->
            job.cancel()
            logcat(VERBOSE) { "EventHub: cancelled scheduled fire for $pixelName" }
        }
    }

    private fun cancelAllTimers() {
        scheduledTimers.forEach { (name, job) ->
            job.cancel()
            logcat(VERBOSE) { "EventHub: cancelled timer for $name" }
        }
        scheduledTimers.clear()
    }

    private fun fireTelemetry(pixelConfig: TelemetryPixelConfig, state: EventHubPixelStateEntity) {

        cancelScheduledFire(pixelConfig.name)

        val pixelData = buildPixel(pixelConfig, state)

        if (pixelData.isNotEmpty()) {
            val additionalParams = mapOf(
                PARAM_ATTRIBUTION_PERIOD to calculateAttributionPeriod(
                    state.periodStartMillis,
                    pixelConfig.trigger.period,
                ).toString(),
            )
            val allParams = pixelData + additionalParams
            logcat(DEBUG) { "EventHub: firing pixel ${pixelConfig.name} params=$allParams" }
            pixel.enqueueFire(
                pixelName = pixelConfig.name,
                parameters = allParams,
            )
        } else {
            logcat(VERBOSE) { "EventHub: skipping pixel ${pixelConfig.name}, no params" }
        }

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

        logcat(VERBOSE) { "EventHub: startNewPeriod ${pixelConfig.name} start=$nowMillis end=${nowMillis + periodMillis}" }
        repository.savePixelState(
            EventHubPixelStateEntity(
                pixelName = pixelConfig.name,
                periodStartMillis = nowMillis,
                periodEndMillis = nowMillis + periodMillis,
                paramsJson = serializeParams(initialParams),
                stopCountingJson = "[]",
                configJson = EventHubConfigParser.serializePixelConfig(pixelConfig),
            ),
        )

        scheduleFireTelemetry(pixelConfig.name, periodMillis)
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
            return toStartOfInterval(periodStartMillis, period.periodSeconds)
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
            return JSONObject(params.mapValues { it.value }).toString()
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
