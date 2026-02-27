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

package com.duckduckgo.eventhub.impl

import logcat.LogPriority.WARN
import logcat.logcat
import org.json.JSONObject

object EventHubConfigParser {

    data class ParsedConfig(
        val featureEnabled: Boolean,
        val telemetry: List<TelemetryPixelConfig>,
    ) {
        companion object {
            val EMPTY = ParsedConfig(featureEnabled = false, telemetry = emptyList())
        }
    }

    fun parse(featureJson: String): ParsedConfig {
        return try {
            val json = JSONObject(featureJson)
            val featureEnabled = json.optString("state", "") == "enabled"
            val settings = json.optJSONObject("settings") ?: return ParsedConfig(featureEnabled, emptyList())
            val telemetryJson = settings.optJSONObject("telemetry") ?: return ParsedConfig(featureEnabled, emptyList())

            val telemetry = mutableListOf<TelemetryPixelConfig>()
            val keys = telemetryJson.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val pixelJson = telemetryJson.optJSONObject(name) ?: continue
                parseTelemetryPixel(name, pixelJson)?.let { telemetry.add(it) }
            }

            ParsedConfig(featureEnabled, telemetry)
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse eventHub config: ${e.message}" }
            ParsedConfig.EMPTY
        }
    }

    fun parseSinglePixelConfig(name: String, json: String): TelemetryPixelConfig? {
        return try {
            parseTelemetryPixel(name, JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    fun serializePixelConfig(config: TelemetryPixelConfig): String {
        val json = JSONObject()
        json.put("state", config.state)

        val triggerJson = JSONObject()
        val periodJson = JSONObject()
        config.trigger.period.let { p ->
            if (p.seconds != 0) periodJson.put("seconds", p.seconds)
            if (p.minutes != 0) periodJson.put("minutes", p.minutes)
            if (p.hours != 0) periodJson.put("hours", p.hours)
            if (p.days != 0) periodJson.put("days", p.days)
        }
        triggerJson.put("period", periodJson)
        json.put("trigger", triggerJson)

        val paramsJson = JSONObject()
        for ((paramName, paramConfig) in config.parameters) {
            val paramObj = JSONObject()
            paramObj.put("template", paramConfig.template)
            paramObj.put("source", paramConfig.source)
            val bucketsObj = JSONObject()
            for ((bucketName, bucket) in paramConfig.buckets) {
                val bucketObj = JSONObject()
                bucketObj.put("gte", bucket.gte)
                if (bucket.lt != null) bucketObj.put("lt", bucket.lt)
                bucketsObj.put(bucketName, bucketObj)
            }
            paramObj.put("buckets", bucketsObj)
            paramsJson.put(paramName, paramObj)
        }
        json.put("parameters", paramsJson)

        return json.toString()
    }

    private fun parseTelemetryPixel(name: String, json: JSONObject): TelemetryPixelConfig? {
        if (!json.has("state")) return null
        val state = json.getString("state")

        val trigger = parseTrigger(json.optJSONObject("trigger") ?: return null) ?: return null

        val paramsJson = json.optJSONObject("parameters") ?: return null
        val parameters = mutableMapOf<String, TelemetryParameterConfig>()
        val paramKeys = paramsJson.keys()
        while (paramKeys.hasNext()) {
            val paramName = paramKeys.next()
            val paramJson = paramsJson.optJSONObject(paramName) ?: continue
            parseParameter(paramJson)?.let { parameters[paramName] = it }
        }
        if (parameters.isEmpty()) return null

        return TelemetryPixelConfig(name = name, state = state, trigger = trigger, parameters = parameters)
    }

    private fun parseTrigger(json: JSONObject): TelemetryTriggerConfig? {
        val periodJson = json.optJSONObject("period") ?: return null
        val seconds = periodJson.optInt("seconds", 0)
        val minutes = periodJson.optInt("minutes", 0)
        val hours = periodJson.optInt("hours", 0)
        val days = periodJson.optInt("days", 0)

        if (seconds == 0 && minutes == 0 && hours == 0 && days == 0) return null

        return TelemetryTriggerConfig(
            period = TelemetryPeriodConfig(seconds = seconds, minutes = minutes, hours = hours, days = days),
        )
    }

    private fun parseParameter(json: JSONObject): TelemetryParameterConfig? {
        if (!json.has("template")) return null
        val template = json.getString("template")

        return when (template) {
            "counter" -> {
                if (!json.has("source")) return null
                val source = json.getString("source")
                val bucketsJson = json.optJSONObject("buckets") ?: return null
                val buckets = linkedMapOf<String, BucketConfig>()
                val keys = bucketsJson.keys()
                while (keys.hasNext()) {
                    val bucketName = keys.next()
                    val bucketJson = bucketsJson.optJSONObject(bucketName) ?: continue
                    if (!bucketJson.has("gte")) continue
                    buckets[bucketName] = BucketConfig(
                        gte = bucketJson.getInt("gte"),
                        lt = if (bucketJson.has("lt")) bucketJson.getInt("lt") else null,
                    )
                }
                if (buckets.isEmpty()) return null
                TelemetryParameterConfig(template = template, source = source, buckets = buckets)
            }
            else -> null
        }
    }
}
