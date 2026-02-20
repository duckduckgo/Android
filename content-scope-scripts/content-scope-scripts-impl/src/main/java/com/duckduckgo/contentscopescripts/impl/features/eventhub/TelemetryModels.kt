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

package com.duckduckgo.contentscopescripts.impl.features.eventhub

/**
 * A single bucket definition for the counter parameter.
 */
data class BucketConfig(
    val minInclusive: Int,
    val maxExclusive: Int?,
    val name: String,
)

/**
 * State of a counter parameter.
 */
data class CounterParameter(
    val source: String,
    val buckets: List<BucketConfig>,
    var data: Int = 0,
    var stopCounting: Boolean = false,
)

/**
 * Persisted state for a telemetry entry.
 */
data class TelemetryPersistedState(
    val name: String,
    val periodStartMs: Long,
    val periodEndMs: Long,
    val periodSeconds: Int,
    val parameters: Map<String, CounterParameterState>,
)

/**
 * Persisted state for a counter parameter.
 */
data class CounterParameterState(
    val source: String,
    val buckets: List<BucketConfig>,
    val data: Int,
    val stopCounting: Boolean,
)

/**
 * Represents a single telemetry pixel definition with its current state.
 */
class TelemetryEntry(
    val name: String,
    var periodStartMs: Long,
    var periodEndMs: Long,
    val periodSeconds: Int,
    val parameters: MutableMap<String, CounterParameter>,
) {
    /**
     * Start collection with fresh timestamps.
     */
    fun start(nowMs: Long) {
        periodStartMs = nowMs
        periodEndMs = nowMs + periodSeconds * 1000L
    }

    /**
     * Process an event. If a counter parameter's source matches, increment it.
     */
    fun handleEvent(type: String, nowMs: Long) {
        if (nowMs > periodEndMs) return

        for ((_, param) in parameters) {
            if (param.source != type) continue
            if (param.stopCounting) continue

            param.data += 1

            // Check if any future bucket could be reached
            if (param.buckets.none { param.data < it.minInclusive }) {
                param.stopCounting = true
            }
        }
    }

    /**
     * Build pixel parameters from current state.
     */
    fun buildPixel(): Map<String, String> {
        val params = mutableMapOf<String, String>()
        for ((paramName, param) in parameters) {
            val bucket = bucketCount(param.data, param.buckets)
            if (bucket != null) {
                params[paramName] = bucket.name
            }
        }
        return params
    }

    fun toPersistedState(): TelemetryPersistedState {
        val paramStates = parameters.mapValues { (_, param) ->
            CounterParameterState(
                source = param.source,
                buckets = param.buckets,
                data = param.data,
                stopCounting = param.stopCounting,
            )
        }
        return TelemetryPersistedState(
            name = name,
            periodStartMs = periodStartMs,
            periodEndMs = periodEndMs,
            periodSeconds = periodSeconds,
            parameters = paramStates,
        )
    }

    companion object {
        /**
         * Parse a telemetry entry from remote config JSON.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromConfig(name: String, config: Map<String, Any?>, nowMs: Long): TelemetryEntry? {
            val trigger = config["trigger"] as? Map<String, Any?> ?: return null
            val period = trigger["period"] as? Map<String, Any?> ?: return null
            val periodSeconds = periodToSeconds(period)
            if (periodSeconds <= 0) return null

            val parameters = mutableMapOf<String, CounterParameter>()
            val parametersConfig = config["parameters"] as? Map<String, Map<String, Any?>> ?: emptyMap()

            for ((paramName, paramConfig) in parametersConfig) {
                val template = paramConfig["template"] as? String ?: continue
                if (template != "counter") continue
                val source = paramConfig["source"] as? String ?: continue

                val bucketsRaw = paramConfig["buckets"] as? List<Map<String, Any?>> ?: emptyList()
                val buckets = bucketsRaw.mapNotNull { bucketMap ->
                    val minInclusive = (bucketMap["minInclusive"] as? Number)?.toInt() ?: return@mapNotNull null
                    val bucketName = bucketMap["name"] as? String ?: return@mapNotNull null
                    val maxExclusive = (bucketMap["maxExclusive"] as? Number)?.toInt()
                    BucketConfig(minInclusive = minInclusive, maxExclusive = maxExclusive, name = bucketName)
                }

                parameters[paramName] = CounterParameter(source = source, buckets = buckets)
            }

            return TelemetryEntry(
                name = name,
                periodStartMs = nowMs,
                periodEndMs = nowMs + periodSeconds * 1000L,
                periodSeconds = periodSeconds,
                parameters = parameters,
            )
        }

        /**
         * Restore from persisted state.
         */
        fun fromPersistedState(state: TelemetryPersistedState): TelemetryEntry {
            val params = state.parameters.mapValues { (_, paramState) ->
                CounterParameter(
                    source = paramState.source,
                    buckets = paramState.buckets,
                    data = paramState.data,
                    stopCounting = paramState.stopCounting,
                )
            }.toMutableMap()

            return TelemetryEntry(
                name = state.name,
                periodStartMs = state.periodStartMs,
                periodEndMs = state.periodEndMs,
                periodSeconds = state.periodSeconds,
                parameters = params,
            )
        }

        fun periodToSeconds(period: Map<String, Any?>): Int {
            val seconds = (period["seconds"] as? Number)?.toInt() ?: 0
            val minutes = (period["minutes"] as? Number)?.toInt() ?: 0
            val hours = (period["hours"] as? Number)?.toInt() ?: 0
            val days = (period["days"] as? Number)?.toInt() ?: 0
            return seconds + minutes * 60 + hours * 3600 + days * 86400
        }

        /**
         * Attribution period: the start of the period in which the attribution window closes,
         * represented as a UTC Unix timestamp (seconds).
         */
        fun calculateAttributionPeriod(startTimeMs: Long, periodSeconds: Int): Long {
            val epochSecs = startTimeMs / 1000
            val snapped = (epochSecs / periodSeconds) * periodSeconds
            return snapped + periodSeconds
        }

        /**
         * Allocate a count to the first matching bucket.
         */
        fun bucketCount(count: Int, buckets: List<BucketConfig>): BucketConfig? {
            for (bucket in buckets) {
                if (count >= bucket.minInclusive) {
                    val maxExclusive = bucket.maxExclusive
                    if (maxExclusive == null || count < maxExclusive) {
                        return bucket
                    }
                }
            }
            return null
        }
    }
}
