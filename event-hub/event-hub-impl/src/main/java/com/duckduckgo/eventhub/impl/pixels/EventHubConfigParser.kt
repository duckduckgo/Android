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
    val telemetry: Map<String, TelemetryPixelJson>? = null,
)

private data class TelemetryPixelJson(
    val state: String,
    val trigger: TelemetryTriggerJson,
    val parameters: Map<String, TelemetryParameterJson>,
)

private data class TelemetryTriggerJson(
    val period: TelemetryPeriodJson,
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
    val buckets: Map<String, BucketJson>? = null,
)

private data class BucketJson(
    val gte: Int,
    val lt: Int? = null,
)

object EventHubConfigParser {

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val settingsAdapter = moshi.adapter(EventHubSettingsJson::class.java).lenient()
    private val pixelAdapter = moshi.adapter(TelemetryPixelJson::class.java).lenient()

    fun parseTelemetry(settingsJson: String): List<TelemetryPixelConfig> {
        return try {
            val settings = settingsAdapter.fromJson(settingsJson) ?: return emptyList()
            val telemetryMap = settings.telemetry ?: return emptyList()
            telemetryMap.mapNotNull { (name, pixelJson) ->
                toTelemetryPixelConfig(name, pixelJson)
            }
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse eventHub settings: ${e.message}" }
            emptyList()
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
        val pixelJson = TelemetryPixelJson(
            state = config.state,
            trigger = TelemetryTriggerJson(
                period = TelemetryPeriodJson(
                    seconds = config.trigger.period.seconds,
                    minutes = config.trigger.period.minutes,
                    hours = config.trigger.period.hours,
                    days = config.trigger.period.days,
                ),
            ),
            parameters = config.parameters.mapValues { (_, paramConfig) ->
                TelemetryParameterJson(
                    template = paramConfig.template,
                    source = paramConfig.source,
                    buckets = paramConfig.buckets.mapValues { (_, bucket) ->
                        BucketJson(gte = bucket.gte, lt = bucket.lt)
                    },
                )
            },
        )
        return runCatching { pixelAdapter.toJson(pixelJson) }
            .onFailure { logcat(WARN) { "Failed to serialize pixel config ${config.name}: ${it.message}" } }
            .getOrNull()
    }

    private fun toTelemetryPixelConfig(name: String, json: TelemetryPixelJson): TelemetryPixelConfig? {
        val periodJson = json.trigger.period
        if (periodJson.seconds == 0 && periodJson.minutes == 0 && periodJson.hours == 0 && periodJson.days == 0) return null

        val period = TelemetryPeriodConfig(
            seconds = periodJson.seconds,
            minutes = periodJson.minutes,
            hours = periodJson.hours,
            days = periodJson.days,
        )
        if (period.periodSeconds <= 0) return null

        val parameters = mutableMapOf<String, TelemetryParameterConfig>()
        for ((paramName, paramJson) in json.parameters) {
            toParameterConfig(paramJson)?.let { parameters[paramName] = it }
        }
        if (parameters.isEmpty()) return null

        return TelemetryPixelConfig(
            name = name,
            state = json.state,
            trigger = TelemetryTriggerConfig(period = period),
            parameters = parameters,
        )
    }

    private fun toParameterConfig(json: TelemetryParameterJson): TelemetryParameterConfig? {
        if (json.template != "counter") return null
        val source = json.source ?: return null
        val bucketsJson = json.buckets ?: return null

        val buckets = bucketsJson.mapValuesTo(linkedMapOf()) { (_, bucketJson) ->
            BucketConfig(gte = bucketJson.gte, lt = bucketJson.lt)
        }
        if (buckets.isEmpty()) return null

        return TelemetryParameterConfig(template = json.template, source = source, buckets = buckets)
    }
}
