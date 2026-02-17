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
 * A telemetry type is a stateless router. When triggered, it increments counter parameters
 * on one or more target pixels.
 */
data class TelemetryTypeConfig(
    val name: String,
    val state: String,
    val template: String,
    val targets: List<TelemetryTarget>,
) {
    val isEnabled: Boolean get() = state == "enabled"
    val isCounter: Boolean get() = template == "counter"
}

/**
 * Links a telemetry type to a specific parameter on a specific pixel.
 */
data class TelemetryTarget(
    val pixel: String,
    val param: String,
)

/**
 * A pixel definition from remote config. Owns the firing schedule, jitter, and parameter definitions.
 */
data class PixelConfig(
    val name: String,
    val period: String,
    val jitter: Double,
    val parameters: Map<String, PixelParameterConfig>,
)

/**
 * Defines a single parameter on a pixel (its type and bucketing).
 */
data class PixelParameterConfig(
    val type: String,
    val buckets: List<String>,
) {
    val isCounter: Boolean get() = type == "counter"
}
