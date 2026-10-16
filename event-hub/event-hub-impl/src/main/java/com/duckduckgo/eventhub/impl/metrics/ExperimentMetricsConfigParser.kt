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

package com.duckduckgo.eventhub.impl.metrics

import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import logcat.LogPriority.WARN
import logcat.logcat

/**
 * A metric an experiment declares in its `settings.metrics`: every delivered [event] asks the experiments framework to
 * convert [name] at each threshold, in that threshold's conversion windows.
 */
internal data class MetricDefinition(
    val name: String,
    val event: String,
    val windowsByThreshold: Map<Int, List<ConversionWindow>>,
)

private data class ExperimentSettingsJson(
    val metrics: Map<String, Any?>? = null,
)

private data class MetricJson(
    val event: String? = null,
    val conversions: List<ConversionGroupJson>? = null,
)

// Read as Double so a fractional value can be rejected; reading as Int would silently truncate it
private data class ConversionGroupJson(
    val windows: List<List<Double>>? = null,
    val thresholds: List<Double>? = null,
)

internal object ExperimentMetricsConfigParser {

    private val NAME_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    // search and app_use are reserved by the spec. The others are built-in metrics Android attaches to the same CSS and TDS
    // experiments, so a config metric with one of these names would share, and corrupt, the framework's counter for it.
    private val RESERVED_NAMES = setOf("search", "app_use", "duck_ai_prompt_sent", "duck_ai_new_chat", "privacyToggleUsed")

    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val settingsAdapter = moshi.adapter(ExperimentSettingsJson::class.java).lenient()
    private val metricAdapter = moshi.adapter(MetricJson::class.java).lenient()

    /** Never throws: an invalid metric is skipped and logged without affecting the others. */
    fun parse(settingsJson: String): List<MetricDefinition> {
        val settings = try {
            settingsAdapter.fromJson(settingsJson)
        } catch (e: Exception) {
            logcat(WARN) { "EventHub: failed to parse experiment settings: ${e.message}" }
            null
        }
        return settings?.metrics.orEmpty().mapNotNull { (name, metricValue) -> parseMetric(name, metricValue) }
    }

    private fun parseMetric(name: String, metricValue: Any?): MetricDefinition? {
        if (!NAME_PATTERN.matches(name)) return skip(name, "invalid name")
        if (name in RESERVED_NAMES) return skip(name, "reserved name")

        val metric = try {
            metricAdapter.fromJsonValue(metricValue)
        } catch (e: Exception) {
            return skip(name, e.message.orEmpty())
        } ?: return skip(name, "null entry")

        val event = metric.event?.takeIf { it.isNotEmpty() } ?: return skip(name, "missing event")
        val conversions = metric.conversions?.takeIf { it.isNotEmpty() } ?: return skip(name, "missing conversions")

        // A (threshold, window) pair repeated across groups would increment the same framework counter twice per event
        val pairs = linkedSetOf<Pair<Int, ConversionWindow>>()
        for (group in conversions) {
            val windows = group.windows?.takeIf { it.isNotEmpty() } ?: return skip(name, "missing windows")
            val rawThresholds = group.thresholds?.takeIf { it.isNotEmpty() } ?: return skip(name, "missing thresholds")
            val thresholds = rawThresholds.mapNotNull { it.toWholeNumberOrNull()?.takeIf { threshold -> threshold >= 1 } }
            if (thresholds.size != rawThresholds.size) return skip(name, "threshold not a whole number of at least 1")
            for (window in windows) {
                val bounds = window.map { it.toWholeNumberOrNull() }
                val low = bounds.getOrNull(0)
                val high = bounds.getOrNull(1)
                if (bounds.size != 2 || low == null || high == null || low < 0 || low > high) return skip(name, "invalid window $window")
                thresholds.forEach { threshold -> pairs += threshold to ConversionWindow(low, high) }
            }
        }

        return MetricDefinition(
            name = name,
            event = event,
            windowsByThreshold = pairs.groupBy(keySelector = { it.first }, valueTransform = { it.second }),
        )
    }

    private fun Double.toWholeNumberOrNull(): Int? = toInt().takeIf { it.toDouble() == this }

    private fun skip(name: String, reason: String): MetricDefinition? {
        logcat(WARN) { "EventHub: skipping experiment metric $name: $reason" }
        return null
    }
}
