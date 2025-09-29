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

// owns storing events, providing stats and emitting metrics
// owns collection and monitoring windows (6mo monitoring)
// owns new user / returning user logic
// owns adding common params to all metrics (e.g. origin, removing default params)
interface AttributedMetricClient {
    // will store an event in the data base, keep the counter per day
    fun collectEvent(eventName: String)

    // return events stored in the last days, and precalculated stats
    suspend fun getEventStats(eventName: String, days: Int): EventStats

    // if in monitoring window will emit the metric
    // this part owns adding common params to all metrics (e.g. origin, removing default params)
    fun emitMetric(metric: AttributedMetric)
}

// stats about events collected
data class EventStats(
    // number of days with at least one event
    val daysWithEvents: Int,
    // rolling average of events based on days timeframe
    val rollingAverage: Double,
    // total number of events in the timeframe
    val totalEvents: Int
)

// interface for each metric
interface AttributedMetric {
    // Metric owns the pixel name value
    fun getPixelName(): String
    // Metric owns adding metric specific parameters
    suspend fun getMetricParameters(): Map<String, String>
}
