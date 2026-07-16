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

data class TelemetryPeriodConfig(
    val seconds: Int = 0,
    val minutes: Int = 0,
    val hours: Int = 0,
    val days: Int = 0,
) {
    val periodSeconds: Long
        get() = seconds.toLong() + minutes.toLong() * 60 + hours.toLong() * 3600 + days.toLong() * 86400
}

data class TelemetryParameterConfig(
    val template: String,
    val source: String? = null,
    val buckets: Map<String, BucketConfig> = emptyMap(),
    /**
     * Optional allow-list of experiment name prefixes for the `experiments` template.
     * When null the parameter reports every active experiment; otherwise only experiments
     * whose (sub)feature name starts with one of these prefixes are reported.
     * Parsed from the pipe-separated `matchExperiments` config string, e.g.
     * `"tdsNextExperiment|contentScopeExperiment1"` -> `["tdsNextExperiment", "contentScopeExperiment1"]`.
     */
    val matchExperiments: List<String>? = null,
) {
    val isCounter: Boolean get() = template == "counter"
    val isExperiments: Boolean get() = template == "experiments"
}

data class BucketConfig(
    val gte: Int,
    val lt: Int?,
)

data class ParamState(val value: Int, val stopCounting: Boolean = false)

/**
 * The result of building a pixel's parameters for firing.
 * @param parameters values that the pixel sender will URL-encode (e.g. counter buckets).
 * @param encodedParameters values that are already %-encoded and must be sent verbatim (e.g. the
 * `experiments` JSON object).
 */
data class BuiltPixel(
    val parameters: Map<String, String>,
    val encodedParameters: Map<String, String>,
)

data class PixelState(
    val pixelName: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val config: TelemetryPixelConfig,
    val params: Map<String, ParamState>,
)
