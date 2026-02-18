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
        val days = periodJson.optInt("days", 0)
        val hours = periodJson.optInt("hours", 0)
        val minutes = periodJson.optInt("minutes", 0)
        val maxStaggerMins = periodJson.optInt("maxStaggerMins", 0)

        if (days == 0 && hours == 0 && minutes == 0) return null

        return TelemetryTriggerConfig(
            period = TelemetryPeriodConfig(days = days, hours = hours, minutes = minutes, maxStaggerMins = maxStaggerMins),
        )
    }

    private fun parseParameter(json: JSONObject): TelemetryParameterConfig? {
        if (!json.has("template")) return null
        val template = json.getString("template")

        return when (template) {
            "counter" -> {
                if (!json.has("source")) return null
                val source = json.getString("source")
                val bucketsArray = json.optJSONArray("buckets")
                val buckets = if (bucketsArray != null) {
                    (0 until bucketsArray.length()).mapNotNull { bucketsArray.optString(it) }
                } else {
                    emptyList()
                }
                TelemetryParameterConfig(template = template, source = source, buckets = buckets)
            }
            else -> null
        }
    }
}
