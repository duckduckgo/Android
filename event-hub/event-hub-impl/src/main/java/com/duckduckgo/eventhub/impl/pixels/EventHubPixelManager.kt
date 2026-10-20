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
import com.duckduckgo.eventhub.impl.metrics.ExperimentMetricsHandler
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
    private val metricsHandler: ExperimentMetricsHandler,
) : EventHubPixelManager {

    private var cachedTelemetryConfigs: List<TelemetryPixelConfig>? = null

    private val deduplicator = WebEventDeduplicator()
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
        // Recorded even while the hub is off, so a page that was already open when it's switched on still has its URL and the
        // tab's next navigation clears that page's keys
        if (deduplicator.onNavigation(webViewId, url)) {
            logcat(VERBOSE) { "EventHub: tab $webViewId navigated to a different URL, clearing dedup" }
        }
    }

    override fun handleWebEvent(data: JSONObject, webViewId: String) {
        val eventType = data.optString("type", "")
        if (eventType.isEmpty()) return

        if (!isEnabled()) return

        // Decided as the event arrives, so it is checked against the page its tab is on at that moment
        if (!deduplicator.isFirstOccurrence(eventType, data.opt("data"), webViewId)) {
            logcat(VERBOSE) { "EventHub: dropping duplicate $eventType on tab $webViewId" }
            return
        }

        // Sampled on arrival: an event that arrives just before a period ends belongs to that period, even if it's processed after
        val nowMillis = timeProvider.currentTimeMillis()
        // A payload that isn't an object has no keys, so every data parameter reading it has no value
        val eventData = data.optJSONObject("data")

        // Both checked again once the work runs: switching the hub off may be handled first, and an immediate pixel has no state
        // that switching off could clear
        appCoroutineScope.launch(pixelDispatcher) {
            if (!isEnabled()) return@launch
            metricsHandler.onEvent(eventType)
        }

        appCoroutineScope.launch(pixelDispatcher) {
            if (!isEnabled()) return@launch
            fireImmediatePixels(eventType, eventData)

            for (pixelState in repository.getAllPixelStates()) {
                if (nowMillis >= pixelState.periodEndMillis) continue

                val updatedParams = pixelState.params.toMutableMap()
                var changed = false

                for ((paramName, paramConfig) in pixelState.config.parameters) {
                    if (paramConfig is TelemetryParameterConfig.Counter && paramConfig.source == eventType) {
                        val paramState = updatedParams[paramName] ?: ParamState(0)
                        if (paramState.stopCounting) continue

                        changed = true
                        if (BucketCounter.shouldStopCounting(paramState.value, paramConfig.buckets)) {
                            updatedParams[paramName] = paramState.copy(stopCounting = true)
                            logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName already at max bucket, stopCounting" }
                            continue
                        }

                        val newValue = paramState.value + 1
                        updatedParams[paramName] = paramState.copy(value = newValue)
                        logcat(VERBOSE) { "EventHub: ${pixelState.pixelName}.$paramName incremented to $newValue" }
                    } else if (paramConfig is TelemetryParameterConfig.Data && paramConfig.source == eventType) {
                        // Every matching event assigns, so one without the key leaves no value rather than an earlier event's
                        val paramState = updatedParams[paramName] ?: ParamState(0)
                        val newValue = dataParameterValue(eventData, paramConfig.dataKey)
                        if (newValue != paramState.lastDataValue) {
                            changed = true
                            updatedParams[paramName] = paramState.copy(lastDataValue = newValue)
                        }
                    }
                }

                if (changed) {
                    repository.savePixelState(pixelState.copy(params = updatedParams))
                }
            }
        }
    }

    private fun fireImmediatePixels(eventType: String, eventData: JSONObject?) {
        for (config in getTelemetryConfigs()) {
            val trigger = config.trigger
            if (!config.isEnabled || trigger !is TelemetryTriggerConfig.Immediate || trigger.source != eventType) continue

            val encodedParams = config.parameters.mapNotNull { (paramName, paramConfig) ->
                if (paramConfig !is TelemetryParameterConfig.Data) return@mapNotNull null
                dataParameterValue(eventData, paramConfig.dataKey)?.let { paramName to percentEncode(it) }
            }.toMap()
            logcat(DEBUG) { "EventHub: firing immediate pixel ${config.name} encoded=$encodedParams" }
            pixel.enqueueFire(pixelName = config.name, parameters = emptyMap(), encodedParameters = encodedParams)
        }
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
                deduplicator.clear()
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
        val counterParams = buildCounterParams(pixelState)
        val trigger = pixelState.config.trigger

        // Only a counter landing in a bucket fires a period pixel: data parameters are carried along but never fire it
        if (counterParams.isNotEmpty() && trigger is TelemetryTriggerConfig.Period) {
            val additionalParams = mapOf(
                PARAM_ATTRIBUTION_PERIOD to calculateAttributionPeriod(pixelState.periodStartMillis, trigger.period).toString(),
            )
            val allParams = counterParams + additionalParams
            val encodedParams = buildDataParams(pixelState)
            logcat(DEBUG) { "EventHub: firing pixel ${pixelState.pixelName} params=$allParams encoded=$encodedParams" }
            pixel.enqueueFire(
                pixelName = pixelState.pixelName,
                parameters = allParams,
                encodedParameters = encodedParams,
            )
        } else {
            logcat(VERBOSE) { "EventHub: skipping pixel ${pixelState.pixelName}, no params" }
        }

        repository.deletePixelState(pixelState.pixelName)

        val latestPixelConfig = getTelemetryConfigs().find { it.name == pixelState.pixelName }
        return if (latestPixelConfig != null) startNewPeriod(latestPixelConfig) else null
    }

    private fun startNewPeriod(pixelConfig: TelemetryPixelConfig): Long? {
        val trigger = pixelConfig.trigger as? TelemetryTriggerConfig.Period ?: return null
        if (!isInForeground || !isEnabled() || !pixelConfig.isEnabled) {
            logcat(VERBOSE) { "EventHub: skipping startNewPeriod for ${pixelConfig.name}" }
            return null
        }
        val nowMillis = timeProvider.currentTimeMillis()
        val periodMillis = trigger.period.periodSeconds * 1000
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

    private fun buildCounterParams(pixelState: PixelState): Map<String, String> {
        val pixelData = mutableMapOf<String, String>()

        for ((paramName, paramConfig) in pixelState.config.parameters) {
            if (paramConfig is TelemetryParameterConfig.Counter) {
                val value = (pixelState.params[paramName] ?: ParamState(0)).value
                val bucketName = BucketCounter.bucketCount(value, paramConfig.buckets)
                if (bucketName != null) {
                    pixelData[paramName] = bucketName
                }
            }
        }

        return pixelData
    }

    private fun buildDataParams(pixelState: PixelState): Map<String, String> {
        return pixelState.config.parameters.mapNotNull { (paramName, paramConfig) ->
            if (paramConfig !is TelemetryParameterConfig.Data) return@mapNotNull null
            pixelState.params[paramName]?.lastDataValue?.let { paramName to percentEncode(it) }
        }.toMap()
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
