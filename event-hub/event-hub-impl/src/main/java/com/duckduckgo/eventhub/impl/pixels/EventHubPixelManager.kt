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
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.duckduckgo.eventhub.impl.di.EventHubDispatcher
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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

    /** Signal that a WebView has navigated to a new URL. */
    fun onNavigationStarted(webViewId: String, url: String)

    /**
     * Notify that the remote feature config has changed.
     */
    fun onConfigChanged()

    /** Whether the eventHub feature is enabled. */
    fun isEnabled(): Boolean

    /** Signal that the app has entered the foreground. */
    fun onAppForegrounded()

    /** Signal that the app has entered the background. */
    fun onAppBackgrounded()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealEventHubPixelManager @Inject constructor(
    private val repository: EventHubRepository,
    private val pixel: Pixel,
    private val timeProvider: CurrentTimeProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @EventHubDispatcher private val pixelDispatcher: CoroutineDispatcher,
    private val eventHubFeature: EventHubFeature,
) : EventHubPixelManager {

    private var cachedTelemetryConfigs: List<TelemetryPixelConfig>? = null

    private val dedupSeen = ConcurrentHashMap<String, MutableSet<String>>()
    private val webViewCurrentUrl = ConcurrentHashMap<String, String>()
    private val schedulerJob = ConflatedJob()

    @Volatile
    private var isInForeground: Boolean = false

    override fun isEnabled(): Boolean = eventHubFeature.self().isEnabled()

    override fun onAppForegrounded() {
        isInForeground = true
        checkPixels()
    }

    private fun checkPixels() {
        appCoroutineScope.launch(pixelDispatcher) {
            if (!isEnabled()) return@launch

            val nextDeadline = processPixelStates()
            val configDeadline = initMissingPixels()
            scheduleNextCheck(minOf(nextDeadline, configDeadline))
        }
    }

    override fun onAppBackgrounded() {
        isInForeground = false
    }

    private fun getTelemetryConfigs(): List<TelemetryPixelConfig> {
        cachedTelemetryConfigs?.let { return it }
        val settingsJson = eventHubFeature.self().getSettings() ?: return emptyList()
        return EventHubConfigParser.parseTelemetry(settingsJson).also { cachedTelemetryConfigs = it }
    }

    override fun onNavigationStarted(webViewId: String, url: String) {
        if (!isEnabled()) return
        if (webViewId.isEmpty() || url.isEmpty()) return
        val previousUrl = webViewCurrentUrl.put(webViewId, url)
        if (previousUrl != null && previousUrl != url) {
            logcat(VERBOSE) { "EventHub: navigation detected for tab $webViewId ($previousUrl -> $url), clearing dedup" }
            dedupSeen.remove(webViewId)
        }
    }

    override fun handleWebEvent(data: JSONObject, webViewId: String) {
        val eventType = data.optString("type", "")
        if (eventType.isEmpty()) return

        if (!isEnabled()) return

        appCoroutineScope.launch(pixelDispatcher) {
            val nowMillis = timeProvider.currentTimeMillis()

            for (pixelState in repository.getAllPixelStates()) {
                if (nowMillis >= pixelState.periodEndMillis) continue

                val updatedParams = pixelState.params.toMutableMap()
                var changed = false

                for ((paramName, paramConfig) in pixelState.config.parameters) {
                    if (paramConfig.isCounter && paramConfig.source == eventType) {
                        val paramState = updatedParams[paramName] ?: ParamState(0)
                        if (paramState.stopCounting) continue
                        if (isDuplicateEvent(pixelState.pixelName, paramName, eventType, webViewId)) continue

                        changed = true
                        if (BucketCounter.shouldStopCounting(paramState.value, paramConfig.buckets)) {
                            updatedParams[paramName] = paramState.copy(stopCounting = true)
                            logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName already at max bucket, stopCounting" }
                            continue
                        }

                        val newValue = paramState.value + 1
                        updatedParams[paramName] = paramState.copy(value = newValue)
                        logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName incremented to $newValue" }
                    }
                }

                if (changed) {
                    repository.savePixelState(pixelState.copy(params = updatedParams))
                }
            }
        }
    }

    private fun isDuplicateEvent(pixelName: String, paramName: String, source: String, webViewId: String): Boolean {
        if (webViewId.isEmpty()) return false
        val key = "$pixelName:$paramName:$source"
        val perTab = dedupSeen.computeIfAbsent(webViewId) { ConcurrentHashMap.newKeySet() }
        if (!perTab.add(key)) {
            logcat(VERBOSE) { "EventHub: dedup $key:$webViewId (already seen on current page)" }
            return true
        }
        return false
    }

    private fun processPixelStates(): Long {
        val nowMillis = timeProvider.currentTimeMillis()
        var nextDeadline = Long.MAX_VALUE

        for (pixelState in repository.getAllPixelStates()) {
            if (nowMillis >= pixelState.periodEndMillis) {
                val newDeadline = fireTelemetry(pixelState)
                if (newDeadline != null) {
                    nextDeadline = minOf(nextDeadline, newDeadline)
                }
            } else {
                nextDeadline = minOf(nextDeadline, pixelState.periodEndMillis)
            }
        }

        return nextDeadline
    }

    private fun initMissingPixels(): Long {
        var nextDeadline = Long.MAX_VALUE
        for (pixelConfig in getTelemetryConfigs()) {
            if (repository.getPixelState(pixelConfig.name) == null) {
                val deadline = startNewPeriod(pixelConfig)
                if (deadline != null) {
                    nextDeadline = minOf(nextDeadline, deadline)
                }
            }
        }
        return nextDeadline
    }

    override fun onConfigChanged() {
        appCoroutineScope.launch(pixelDispatcher) {
            cachedTelemetryConfigs = null
            if (!isEnabled()) {
                logcat(DEBUG) { "EventHub: feature disabled, clearing all pixel states" }
                schedulerJob.cancel()
                repository.deleteAllPixelStates()
                dedupSeen.clear()
                webViewCurrentUrl.clear()
                return@launch
            }

            val telemetry = getTelemetryConfigs()
            logcat(DEBUG) { "EventHub: onConfigChanged — feature enabled, ${telemetry.size} telemetry pixel(s) in config" }

            var nextDeadline = Long.MAX_VALUE
            for (pixelState in repository.getAllPixelStates()) {
                nextDeadline = minOf(nextDeadline, pixelState.periodEndMillis)
            }
            for (pixelConfig in telemetry) {
                if (repository.getPixelState(pixelConfig.name) == null) {
                    val deadline = startNewPeriod(pixelConfig)
                    if (deadline != null) {
                        nextDeadline = minOf(nextDeadline, deadline)
                    }
                }
            }

            scheduleNextCheck(nextDeadline)
        }
    }

    private fun scheduleNextCheck(deadlineMillis: Long) {
        schedulerJob.cancel()
        if (deadlineMillis == Long.MAX_VALUE) return

        val nowMillis = timeProvider.currentTimeMillis()
        val delayMillis = (deadlineMillis - nowMillis).coerceAtLeast(0)

        logcat(VERBOSE) { "EventHub: scheduling next check in ${delayMillis}ms" }
        schedulerJob += appCoroutineScope.launch(pixelDispatcher) {
            delay(delayMillis)
            ensureActive()

            if (!isEnabled()) return@launch

            val nextDeadline = processPixelStates()
            scheduleNextCheck(nextDeadline)
        }
    }

    private fun fireTelemetry(pixelState: PixelState): Long? {
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
        return if (latestPixelConfig != null) startNewPeriod(latestPixelConfig) else null
    }

    private fun startNewPeriod(pixelConfig: TelemetryPixelConfig): Long? {
        if (!isInForeground || !isEnabled() || !pixelConfig.isEnabled) {
            logcat(VERBOSE) { "EventHub: skipping startNewPeriod for ${pixelConfig.name}" }
            return null
        }
        val nowMillis = timeProvider.currentTimeMillis()
        val periodMillis = pixelConfig.trigger.period.periodSeconds * 1000
        val periodEndMillis = nowMillis + periodMillis

        logcat(VERBOSE) { "EventHub: startNewPeriod ${pixelConfig.name} start=$nowMillis end=$periodEndMillis" }
        repository.savePixelState(
            PixelState(
                pixelName = pixelConfig.name,
                periodStartMillis = nowMillis,
                periodEndMillis = periodEndMillis,
                config = pixelConfig,
                params = pixelConfig.parameters.keys.associateWith { ParamState(0) },
            ),
        )

        return periodEndMillis
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
