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

import org.json.JSONObject

/**
 * A telemetry type is a stateless router. The [template] determines how the
 * type's configuration is interpreted; template-specific fields live in [rawConfig].
 */
data class TelemetryTypeConfig(
    val name: String,
    val state: String,
    val template: String,
    val rawConfig: JSONObject,
) {
    val isEnabled: Boolean get() = state == "enabled"
}

/**
 * Counter-specific view of a telemetry type configuration.
 * Only valid when [TelemetryTypeConfig.template] == "counter".
 */
data class CounterTelemetryType(
    val name: String,
    val targets: List<TelemetryTarget>,
) {
    companion object {
        fun from(config: TelemetryTypeConfig): CounterTelemetryType? {
            if (config.template != "counter") return null
            val targetsArray = config.rawConfig.optJSONArray("targets") ?: return null
            val targets = (0 until targetsArray.length()).mapNotNull { i ->
                val targetJson = targetsArray.optJSONObject(i) ?: return@mapNotNull null
                val pixel = targetJson.optString("pixel", "")
                val param = targetJson.optString("param", "")
                if (pixel.isNotEmpty() && param.isNotEmpty()) TelemetryTarget(pixel, param) else null
            }
            return if (targets.isNotEmpty()) CounterTelemetryType(config.name, targets) else null
        }
    }
}

/**
 * Links a telemetry type to a specific parameter on a specific pixel.
 */
data class TelemetryTarget(
    val pixel: String,
    val param: String,
)

/**
 * A pixel definition from remote config. Owns the firing schedule and parameter definitions.
 */
data class PixelConfig(
    val name: String,
    val trigger: PixelTriggerConfig,
    val parameters: Map<String, PixelParameterConfig>,
)

/**
 * Defines when a pixel fires.
 */
data class PixelTriggerConfig(
    val period: PixelPeriodConfig,
)

/**
 * Period definition with jitter.
 * [days] is the nominal period length.
 * [jitterMaxPercent] is the max jitter as a percentage (e.g. 25 means ±12.5%).
 */
data class PixelPeriodConfig(
    val days: Int,
    val jitterMaxPercent: Double,
) {
    val periodSeconds: Long get() = days.toLong() * 86400
}

/**
 * Defines a single parameter on a pixel (its type and bucketing).
 */
data class PixelParameterConfig(
    val type: String,
    val buckets: List<String>,
)
