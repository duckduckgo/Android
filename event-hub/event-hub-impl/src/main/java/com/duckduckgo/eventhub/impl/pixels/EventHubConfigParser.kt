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

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.WARN
import logcat.logcat

private data class EventHubSettingsJson(
    val telemetry: Map<String, Any?>? = null,
)

private data class TelemetryPixelJson(
    val state: String,
    val trigger: TelemetryTriggerJson,
    val parameters: Map<String, TelemetryParameterJson>,
)

private data class TelemetryTriggerJson(
    val type: String? = null,
    val period: TelemetryPeriodJson? = null,
    val source: String? = null,
)

private data class TelemetryPeriodJson(
    val seconds: Int = 0,
    val minutes: Int = 0,
    val hours: Int = 0,
    val days: Int = 0,
)

private data class TelemetryParameterJson(
    val template: String,
    val source: String? = null,
    val dataKey: String? = null,
    val buckets: Map<String, BucketJson>? = null,
)

private data class BucketJson(
    val gte: Int,
    val lt: Int? = null,
)

object EventHubConfigParser {

    private const val PERIOD = "period"

    // Plain "immediate" is deliberately not accepted: it fires on every raw occurrence, while "immediate_v2" fires once per
    // de-duplicated event. Configs carrying the de-duplicated meaning use the new name so that clients firing on every
    // occurrence ignore them.
    private const val IMMEDIATE = "immediate_v2"
    private const val COUNTER = "counter"
    private const val DATA = "data"

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val settingsAdapter = moshi.adapter(EventHubSettingsJson::class.java).lenient()
    private val pixelAdapter = moshi.adapter(TelemetryPixelJson::class.java).lenient()

    fun parseTelemetry(settingsJson: String): List<TelemetryPixelConfig> {
        val settings = try {
            settingsAdapter.fromJson(settingsJson)
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse eventHub settings: ${e.message}" }
            null
        }
        // Each entry is decoded on its own so that one entry this client can't parse (e.g. a newer trigger type)
        // doesn't discard every other pixel
        return settings?.telemetry.orEmpty().mapNotNull { (name, pixelValue) -> parseTelemetryEntry(name, pixelValue) }
    }

    private fun parseTelemetryEntry(name: String, pixelValue: Any?): TelemetryPixelConfig? {
        return try {
            val pixelJson = pixelAdapter.fromJsonValue(pixelValue) ?: return skipEntry(name, "null entry")
            toTelemetryPixelConfig(name, pixelJson)
        } catch (e: Exception) {
            logcat(WARN) { "Skipping eventHub telemetry entry $name: ${e.message}" }
            null
        }
    }

    fun parseSinglePixelConfig(name: String, json: String): TelemetryPixelConfig? {
        return try {
            val pixelJson = pixelAdapter.fromJson(json) ?: return null
            toTelemetryPixelConfig(name, pixelJson)
        } catch (e: Exception) {
            null
        }
    }

    fun serializePixelConfig(config: TelemetryPixelConfig): String? {
        val triggerJson = when (val trigger = config.trigger) {
            is TelemetryTriggerConfig.Period -> TelemetryTriggerJson(
                period = TelemetryPeriodJson(
                    seconds = trigger.period.seconds,
                    minutes = trigger.period.minutes,
                    hours = trigger.period.hours,
                    days = trigger.period.days,
                ),
            )
            is TelemetryTriggerConfig.Immediate -> TelemetryTriggerJson(type = IMMEDIATE, source = trigger.source)
        }
        val pixelJson = TelemetryPixelJson(
            state = config.state,
            trigger = triggerJson,
            parameters = config.parameters.mapValues { (_, paramConfig) ->
                when (paramConfig) {
                    is TelemetryParameterConfig.Counter -> TelemetryParameterJson(
                        template = COUNTER,
                        source = paramConfig.source,
                        buckets = paramConfig.buckets.mapValues { (_, bucket) -> BucketJson(gte = bucket.gte, lt = bucket.lt) },
                    )
                    is TelemetryParameterConfig.Data -> TelemetryParameterJson(
                        template = DATA,
                        source = paramConfig.source,
                        dataKey = paramConfig.dataKey,
                    )
                }
            },
        )
        return runCatching { pixelAdapter.toJson(pixelJson) }
            .onFailure { logcat(WARN) { "Failed to serialize pixel config ${config.name}: ${it.message}" } }
            .getOrNull()
    }

    private fun toTelemetryPixelConfig(name: String, json: TelemetryPixelJson): TelemetryPixelConfig? {
        val trigger = when (val triggerType = json.trigger.type ?: PERIOD) {
            PERIOD -> toPeriodTrigger(name, json.trigger) ?: return null
            IMMEDIATE -> {
                val source = json.trigger.source?.takeIf { it.isNotEmpty() } ?: return skipEntry(name, "immediate trigger without source")
                TelemetryTriggerConfig.Immediate(source)
            }
            else -> return skipEntry(name, "unsupported trigger type $triggerType")
        }

        val parameters = mutableMapOf<String, TelemetryParameterConfig>()
        for ((paramName, paramJson) in json.parameters) {
            toParameterConfig(paramJson)?.let { parameters[paramName] = it }
        }
        // A period pixel only fires when a counter lands in a bucket, so without one it would never fire. An immediate pixel
        // fires on the event alone and may have no parameters.
        if (trigger is TelemetryTriggerConfig.Period && parameters.values.none { it is TelemetryParameterConfig.Counter }) {
            return skipEntry(name, "no valid counter parameters")
        }

        return TelemetryPixelConfig(
            name = name,
            state = json.state,
            trigger = trigger,
            parameters = parameters,
        )
    }

    private fun toPeriodTrigger(name: String, json: TelemetryTriggerJson): TelemetryTriggerConfig.Period? {
        val periodJson = json.period ?: return skipEntry(name, "missing period")
        if (periodJson.seconds == 0 && periodJson.minutes == 0 && periodJson.hours == 0 && periodJson.days == 0) {
            return skipEntry(name, "zero period")
        }

        val period = TelemetryPeriodConfig(
            seconds = periodJson.seconds,
            minutes = periodJson.minutes,
            hours = periodJson.hours,
            days = periodJson.days,
        )
        if (period.periodSeconds <= 0) return skipEntry(name, "non-positive period")
        return TelemetryTriggerConfig.Period(period)
    }

    private fun <T> skipEntry(name: String, reason: String): T? {
        logcat(WARN) { "Skipping eventHub telemetry entry $name: $reason" }
        return null
    }

    private fun toParameterConfig(json: TelemetryParameterJson): TelemetryParameterConfig? {
        return when (json.template) {
            COUNTER -> {
                val source = json.source ?: return null
                val bucketsJson = json.buckets ?: return null

                val buckets = bucketsJson.mapValuesTo(linkedMapOf()) { (_, bucketJson) ->
                    BucketConfig(gte = bucketJson.gte, lt = bucketJson.lt)
                }
                if (buckets.isEmpty()) return null

                TelemetryParameterConfig.Counter(source = source, buckets = buckets)
            }
            DATA -> {
                val dataKey = json.dataKey?.takeIf { it.isNotEmpty() } ?: return null
                TelemetryParameterConfig.Data(dataKey = dataKey, source = json.source)
            }
            else -> null
        }
    }
}
