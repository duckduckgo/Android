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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import logcat.LogPriority.DEBUG
import logcat.LogPriority.VERBOSE
import logcat.logcat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

interface EventHubPixelManager {
    /**
     * Process an incoming web event against active telemetry pixel configs.
     * [webViewId] is the id of the WebView that the event originated from.
     */
    fun handleWebEvent(data: JSONObject, webViewId: String)

    /**
     * Signal that a WebView has navigated to a new URL (for example used for
     * event deduplication)
     */
    fun onNavigationStarted(webViewId: String, url: String)

    /**
     * Reconcile pixel state: fire any whose collection period has elapsed
     * and ensure active configs have scheduled work.
     */
    fun checkPixels()

    /**
     * Notify that the remote feature config has changed.
     */
    fun onConfigChanged()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealEventHubPixelManager @Inject constructor(
    private val repository: EventHubRepository,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val foregroundStateProvider: AppForegroundStateProvider,
    private val eventHubFeature: EventHubFeature,
) : EventHubPixelManager {

    @Volatile
    private var cachedTelemetryConfigs: List<TelemetryPixelConfig>? = null

    private val dedupSeen: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val webViewCurrentUrl = ConcurrentHashMap<String, String>()
    private val scheduledTimers = ConcurrentHashMap<String, Job>()

    private fun isFeatureEnabled(): Boolean = eventHubFeature.self().isEnabled()

    private fun getTelemetryConfigs(): List<TelemetryPixelConfig> {
        cachedTelemetryConfigs?.let { return it }
        val settingsJson = eventHubFeature.self().getSettings() ?: return emptyList()
        return EventHubConfigParser.parseTelemetry(settingsJson).also { cachedTelemetryConfigs = it }
    }

    override fun onNavigationStarted(webViewId: String, url: String) {
        if (webViewId.isEmpty() || url.isEmpty()) return
        val previousUrl = webViewCurrentUrl.put(webViewId, url)
        if (previousUrl != null && previousUrl != url) {
            logcat(VERBOSE) { "EventHub: navigation detected for tab $webViewId ($previousUrl -> $url), clearing dedup" }
            dedupSeen.removeAll { it.endsWith(":$webViewId") }
        }
    }

    override fun handleWebEvent(data: JSONObject, webViewId: String) {
        val eventType = data.optString("type", "")
        if (eventType.isEmpty()) return

        if (!isFeatureEnabled()) return

        val nowMillis = timeProvider.currentTimeMillis()

        synchronized(this) {
            for (pixelState in repository.getAllPixelStates()) {
                if (nowMillis > pixelState.periodEndMillis) continue

                val params = pixelState.params
                var changed = false

                for ((paramName, paramConfig) in pixelState.config.parameters) {
                    if (paramConfig.isCounter && paramConfig.source == eventType) {
                        val paramState = params[paramName] ?: ParamState(0)
                        if (paramState.stopCounting) continue
                        if (isDuplicateEvent(pixelState.pixelName, paramName, eventType, webViewId)) continue

                        changed = true
                        if (BucketCounter.shouldStopCounting(paramState.value, paramConfig.buckets)) {
                            params[paramName] = paramState.copy(stopCounting = true)
                            logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName already at max bucket, stopCounting" }
                            continue
                        }

                        val newValue = paramState.value + 1
                        params[paramName] = paramState.copy(value = newValue)
                        logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName incremented to $newValue" }
                    }
                }

                if (changed) {
                    repository.savePixelState(pixelState)
                }
            }
        }
    }

    private fun isDuplicateEvent(pixelName: String, paramName: String, source: String, webViewId: String): Boolean {
        if (webViewId.isEmpty()) return false
        val key = "$pixelName:$paramName:$source:$webViewId"
        if (!dedupSeen.add(key)) {
            logcat(VERBOSE) { "EventHub: dedup $key (already seen on current page)" }
            return true
        }
        return false
    }

    override fun checkPixels() {
        if (!isFeatureEnabled()) return

        val nowMillis = timeProvider.currentTimeMillis()

        synchronized(this) {
            for (pixelState in repository.getAllPixelStates()) {
                if (nowMillis >= pixelState.periodEndMillis) {
                    fireTelemetry(pixelState)
                } else {
                    scheduleFireTelemetry(pixelState.pixelName, pixelState.periodEndMillis - nowMillis)
                }
            }

            for (pixelConfig in getTelemetryConfigs()) {
                if (repository.getPixelState(pixelConfig.name) == null) {
                    startNewPeriod(pixelConfig)
                }
            }
        }
    }

    override fun onConfigChanged() {
        synchronized(this) {
            cachedTelemetryConfigs = null
            if (!isFeatureEnabled()) {
                logcat(DEBUG) { "EventHub: feature disabled, clearing all pixel states" }
                cancelAllTimers()
                repository.deleteAllPixelStates()
                return
            }

            val telemetry = getTelemetryConfigs()
            logcat(DEBUG) { "EventHub: onConfigChanged — feature enabled, ${telemetry.size} telemetry pixel(s) in config" }
            for (pixelConfig in telemetry) {
                if (repository.getPixelState(pixelConfig.name) == null) {
                    startNewPeriod(pixelConfig)
                }
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
            ensureActive()

            if (!isFeatureEnabled()) {
                scheduledTimers.remove(pixelName)
                return@launch
            }

            synchronized(this@RealEventHubPixelManager) {
                // Guard against stale timer: if checkPixels or fireTelemetry already replaced
                // this job with a new one, this coroutine is stale and must not fire.
                if (scheduledTimers[pixelName] !== coroutineContext[Job]) {
                    return@launch
                }
                val pixelState = repository.getPixelState(pixelName)
                if (pixelState == null) {
                    scheduledTimers.remove(pixelName)
                    return@launch
                }
                fireTelemetry(pixelState)
            }
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

    private fun fireTelemetry(pixelState: PixelState) {
        cancelScheduledFire(pixelState.pixelName)

        val pixelData = buildPixel(pixelState)

        if (pixelData.isNotEmpty()) {
            val additionalParams = mapOf(
                PARAM_ATTRIBUTION_PERIOD to calculateAttributionPeriod(
                    pixelState.periodStartMillis,
                    pixelState.config.trigger.period,
                ).toString(),
            )
            val allParams = pixelData + additionalParams
            logcat(DEBUG) { "EventHub: firing pixel ${pixelState.pixelName} params=$allParams" }
            pixel.enqueueFire(
                pixelName = pixelState.pixelName,
                parameters = allParams,
            )
        } else {
            logcat(VERBOSE) { "EventHub: skipping pixel ${pixelState.pixelName}, no params" }
        }

        repository.deletePixelState(pixelState.pixelName)

        val latestPixelConfig = getTelemetryConfigs().find { it.name == pixelState.pixelName }
        if (latestPixelConfig != null) {
            startNewPeriod(latestPixelConfig)
        }
    }

    private fun startNewPeriod(pixelConfig: TelemetryPixelConfig) {
        if (!foregroundStateProvider.isInForeground || !isFeatureEnabled() || !pixelConfig.isEnabled) {
            logcat(VERBOSE) { "EventHub: skipping startNewPeriod for ${pixelConfig.name}" }
            return
        }
        val nowMillis = timeProvider.currentTimeMillis()
        val periodMillis = pixelConfig.trigger.period.periodSeconds * 1000

        logcat(VERBOSE) { "EventHub: startNewPeriod ${pixelConfig.name} start=$nowMillis end=${nowMillis + periodMillis}" }
        repository.savePixelState(
            PixelState(
                pixelName = pixelConfig.name,
                periodStartMillis = nowMillis,
                periodEndMillis = nowMillis + periodMillis,
                config = pixelConfig,
                params = pixelConfig.parameters.keys.associateWith { ParamState(0) }.toMutableMap(),
            ),
        )

        scheduleFireTelemetry(pixelConfig.name, periodMillis)
    }

    private fun buildPixel(pixelState: PixelState): Map<String, String> {
        val pixelData = mutableMapOf<String, String>()

        for ((paramName, paramConfig) in pixelState.config.parameters) {
            if (paramConfig.isCounter) {
                val value = (pixelState.params[paramName] ?: ParamState(0)).value
                val bucketName = BucketCounter.bucketCount(value, paramConfig.buckets)
                if (bucketName != null) {
                    pixelData[paramName] = bucketName
                }
            }
        }

        return pixelData
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
    }
}

interface TimeProvider {
    fun currentTimeMillis(): Long
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
