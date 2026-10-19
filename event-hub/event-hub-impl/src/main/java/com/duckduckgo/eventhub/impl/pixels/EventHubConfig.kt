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

sealed class TelemetryTriggerConfig {
    /** Counts delivered events over [period] and fires once at its end. */
    data class Period(val period: TelemetryPeriodConfig) : TelemetryTriggerConfig()

    /** Fires one pixel for each delivered [source] event. It has no period and no persisted state, and isn't gated on foreground. */
    data class Immediate(val source: String) : TelemetryTriggerConfig()
}

data class TelemetryPeriodConfig(
    val seconds: Int = 0,
    val minutes: Int = 0,
    val hours: Int = 0,
    val days: Int = 0,
) {
    val periodSeconds: Long
        get() = seconds.toLong() + minutes.toLong() * 60 + hours.toLong() * 3600 + days.toLong() * 86400
}

sealed class TelemetryParameterConfig {
    /** Counts delivered [source] events and reports the bucket the total falls in. */
    data class Counter(
        val source: String,
        val buckets: Map<String, BucketConfig>,
    ) : TelemetryParameterConfig()

    /**
     * Reports `data[dataKey]` from an event's payload. On a period pixel the value comes from the latest delivered [source]
     * event; on an immediate pixel it comes from the event that fired it, and [source] is unused.
     */
    data class Data(
        val dataKey: String,
        val source: String? = null,
    ) : TelemetryParameterConfig()
}

data class BucketConfig(
    val gte: Int,
    val lt: Int?,
)

/** [lastDataValue] is a data parameter's value as compact JSON, or null when it has none. */
data class ParamState(
    val value: Int,
    val stopCounting: Boolean = false,
    val lastDataValue: String? = null,
)

data class PixelState(
    val pixelName: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val config: TelemetryPixelConfig,
    val params: Map<String, ParamState>,
)
