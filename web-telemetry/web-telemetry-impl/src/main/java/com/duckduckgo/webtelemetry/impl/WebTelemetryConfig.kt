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

/**
 * A telemetry pixel definition from remote config.
 * Each pixel owns its firing schedule, parameters, and state.
 */
data class TelemetryPixelConfig(
    val name: String,
    val state: String,
    val trigger: TelemetryTriggerConfig,
    val parameters: Map<String, TelemetryParameterConfig>,
) {
    val isEnabled: Boolean get() = state == "enabled"
}

data class TelemetryTriggerConfig(
    val period: TelemetryPeriodConfig,
)

/**
 * Period definition.
 * [days], [hours], [minutes] define the cadence. At least one must be > 0.
 * [maxStaggerMins] is the max random delay (in minutes) added after the period elapses.
 */
data class TelemetryPeriodConfig(
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    val maxStaggerMins: Int = 0,
) {
    val periodSeconds: Long get() = days.toLong() * 86400 + hours.toLong() * 3600 + minutes.toLong() * 60
}

/**
 * A pixel parameter definition. The [template] determines how the parameter is handled.
 * Template-specific fields are accessed directly.
 */
data class TelemetryParameterConfig(
    val template: String,
    val source: String,
    val buckets: List<String>,
) {
    val isCounter: Boolean get() = template == "counter"
}
