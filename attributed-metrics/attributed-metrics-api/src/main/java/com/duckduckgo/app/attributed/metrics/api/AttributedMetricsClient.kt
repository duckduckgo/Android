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

package com.duckduckgo.app.attributed.metrics.api

/**
 * Client for collecting and emitting attributed metrics.
 */
interface AttributedMetricClient {
    /**
     * Stores an event occurrence for later analysis.
     * Does nothing if the client is not active.
     *
     * @param eventName Name of the event to collect
     */
    fun collectEvent(eventName: String)

    /**
     * Calculates statistics for a specific event over a time period.
     * Returns zero stats if the client is not active.
     *
     * @param eventName Name of the event to analyze
     * @param days Number of days to look back
     * @return Statistics about the event's occurrences
     */
    suspend fun getEventStats(
        eventName: String,
        days: Int,
    ): EventStats

    /**
     * Emits a metric with its parameters if the client is active.
     * Does nothing if the client is not active.
     *
     * @param metric The metric to emit
     */
    fun emitMetric(metric: AttributedMetric)
}

/**
 * Statistics about collected events over a time period.
 *
 * @property daysWithEvents Number of days that had at least one event
 * @property rollingAverage Average number of events per day over the period
 * @property totalEvents Total number of events in the period
 */
data class EventStats(
    val daysWithEvents: Int,
    val rollingAverage: Double,
    val totalEvents: Int,
)

/**
 * Interface for defining an attributed metric.
 * Each metric implementation should provide its name and parameters.
 */
interface AttributedMetric {
    /**
     * @return The name used to identify this metric
     */
    fun getPixelName(): String

    /**
     * @return Parameters to be included with this metric
     */
    suspend fun getMetricParameters(): Map<String, String>

    /**
     * @return Identifier used to deduplicate metric emissions. The same combination of metric
     *           and tag will only be emitted once.
     */
    suspend fun getTag(): String
}
