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

/**
 * Parses the webTelemetry feature JSON from remote config into structured config objects.
 */
object WebTelemetryConfigParser {

    /**
     * Parses the feature JSON and returns the list of active (enabled) telemetry type configs.
     * Returns empty list if the feature is not enabled or cannot be parsed.
     */
    fun parseActiveTelemetryTypes(featureJson: String): List<TelemetryTypeConfig> {
        return try {
            val json = JSONObject(featureJson)
            val featureState = json.optString("state", "")
            if (featureState != "enabled") return emptyList()

            val settings = json.optJSONObject("settings") ?: return emptyList()
            val telemetryTypes = settings.optJSONObject("telemetryTypes") ?: return emptyList()

            val result = mutableListOf<TelemetryTypeConfig>()
            val keys = telemetryTypes.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val typeJson = telemetryTypes.optJSONObject(name) ?: continue
                val config = parseTelemetryType(name, typeJson) ?: continue
                if (config.isEnabled) {
                    result.add(config)
                }
            }
            result
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse webTelemetry config: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Parses all telemetry type configs (including disabled ones) from the feature JSON.
     */
    fun parseAllTelemetryTypes(featureJson: String): List<TelemetryTypeConfig> {
        return try {
            val json = JSONObject(featureJson)
            val settings = json.optJSONObject("settings") ?: return emptyList()
            val telemetryTypes = settings.optJSONObject("telemetryTypes") ?: return emptyList()

            val result = mutableListOf<TelemetryTypeConfig>()
            val keys = telemetryTypes.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val typeJson = telemetryTypes.optJSONObject(name) ?: continue
                parseTelemetryType(name, typeJson)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse webTelemetry config: ${e.message}" }
            emptyList()
        }
    }

    private fun parseTelemetryType(name: String, json: JSONObject): TelemetryTypeConfig? {
        val state = json.optString("state", "disabled")
        val template = json.optString("template", "")
        val pixel = json.optString("pixel", "")
        val period = json.optString("period", "day")

        if (template.isEmpty() || pixel.isEmpty()) return null

        val bucketsArray = json.optJSONArray("buckets")
        val buckets = if (bucketsArray != null) {
            (0 until bucketsArray.length()).mapNotNull { bucketsArray.optString(it) }
        } else {
            emptyList()
        }

        return TelemetryTypeConfig(
            name = name,
            state = state,
            template = template,
            buckets = buckets,
            pixel = pixel,
            period = period,
        )
    }
}
