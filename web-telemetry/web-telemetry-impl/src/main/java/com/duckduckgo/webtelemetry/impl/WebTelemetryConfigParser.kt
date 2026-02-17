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

    data class ParsedConfig(
        val featureEnabled: Boolean,
        val telemetryTypes: List<TelemetryTypeConfig>,
        val pixels: List<PixelConfig>,
    ) {
        companion object {
            val EMPTY = ParsedConfig(featureEnabled = false, telemetryTypes = emptyList(), pixels = emptyList())
        }
    }

    fun parse(featureJson: String): ParsedConfig {
        return try {
            val json = JSONObject(featureJson)
            val featureState = json.optString("state", "")
            val featureEnabled = featureState == "enabled"

            val settings = json.optJSONObject("settings") ?: return ParsedConfig(featureEnabled, emptyList(), emptyList())

            val telemetryTypes = parseTelemetryTypes(settings)
            val pixels = parsePixels(settings)

            ParsedConfig(featureEnabled, telemetryTypes, pixels)
        } catch (e: Exception) {
            logcat(WARN) { "Failed to parse webTelemetry config: ${e.message}" }
            ParsedConfig.EMPTY
        }
    }

    /**
     * Returns only active (feature enabled + type enabled) telemetry types.
     */
    fun parseActiveTelemetryTypes(featureJson: String): List<TelemetryTypeConfig> {
        val config = parse(featureJson)
        if (!config.featureEnabled) return emptyList()
        return config.telemetryTypes.filter { it.isEnabled }
    }

    /**
     * Returns active pixel configs (feature must be enabled; all defined pixels are considered active).
     */
    fun parseActivePixels(featureJson: String): List<PixelConfig> {
        val config = parse(featureJson)
        if (!config.featureEnabled) return emptyList()
        return config.pixels
    }

    private fun parseTelemetryTypes(settings: JSONObject): List<TelemetryTypeConfig> {
        val typesJson = settings.optJSONObject("telemetryTypes") ?: return emptyList()
        val result = mutableListOf<TelemetryTypeConfig>()
        val keys = typesJson.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val typeJson = typesJson.optJSONObject(name) ?: continue
            parseTelemetryType(name, typeJson)?.let { result.add(it) }
        }
        return result
    }

    private fun parseTelemetryType(name: String, json: JSONObject): TelemetryTypeConfig? {
        val state = json.optString("state", "disabled")
        val template = json.optString("template", "")
        if (template.isEmpty()) return null

        return TelemetryTypeConfig(name = name, state = state, template = template, rawConfig = json)
    }

    private fun parsePixels(settings: JSONObject): List<PixelConfig> {
        val pixelsJson = settings.optJSONObject("pixels") ?: return emptyList()
        val result = mutableListOf<PixelConfig>()
        val keys = pixelsJson.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val pixelJson = pixelsJson.optJSONObject(name) ?: continue
            parsePixel(name, pixelJson)?.let { result.add(it) }
        }
        return result
    }

    private fun parsePixel(name: String, json: JSONObject): PixelConfig? {
        val period = json.optString("period", "")
        if (period.isEmpty()) return null

        val jitter = json.optDouble("jitter", 0.25)
        val paramsJson = json.optJSONObject("parameters") ?: return null

        val parameters = mutableMapOf<String, PixelParameterConfig>()
        val paramKeys = paramsJson.keys()
        while (paramKeys.hasNext()) {
            val paramName = paramKeys.next()
            val paramJson = paramsJson.optJSONObject(paramName) ?: continue
            parsePixelParameter(paramJson)?.let { parameters[paramName] = it }
        }
        if (parameters.isEmpty()) return null

        return PixelConfig(name = name, period = period, jitter = jitter, parameters = parameters)
    }

    private fun parsePixelParameter(json: JSONObject): PixelParameterConfig? {
        val type = json.optString("type", "")
        if (type.isEmpty()) return null

        val bucketsArray = json.optJSONArray("buckets")
        val buckets = if (bucketsArray != null) {
            (0 until bucketsArray.length()).mapNotNull { bucketsArray.optString(it) }
        } else {
            emptyList()
        }

        return PixelParameterConfig(type = type, buckets = buckets)
    }
}
