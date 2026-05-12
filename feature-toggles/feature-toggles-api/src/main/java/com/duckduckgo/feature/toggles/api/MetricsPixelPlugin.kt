/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.feature.toggles.api

/**
 * Experiment pixels that want to be fired should implement this plugin. The associated plugin point
 * will call the plugins before the pixels that match the [MetricsPixel] spec are fired.
 */
interface MetricsPixelPlugin {
    /**
     * @return a list of [MetricsPixel] that will be intercepted and processed prior firing to adhere to our
     * experimental practises
     */
    suspend fun getMetrics(): List<MetricsPixel>
}

data class ConversionWindow(val lowerWindow: Int, val upperWindow: Int)

enum class MetricType {
    /** Fire the pixel once per conversion window. */
    NORMAL,

    /** Increment the count only while within the conversion window; fire when count reaches [MetricsPixel.value]. */
    COUNT_WHEN_IN_WINDOW,

    /** Increment the count unconditionally (regardless of conversion window); fire when count reaches [MetricsPixel.value]. */
    COUNT_ALWAYS,
}

data class MetricsPixel(
    val metric: String,
    val value: String,
    val conversionWindow: List<ConversionWindow>,
    val toggle: Toggle,
    val type: MetricType = MetricType.NORMAL,
)

/**
 * Internal extension interface for sending metric pixels. Should NEVER be used publicly.
 * Use [MetricsPixel.send] instead.
 */
interface MetricsPixelExtension {
    suspend fun send(metricsPixel: MetricsPixel)
}

/**
 * Provider for [MetricsPixelExtension]. Initialised by the impl module at process start.
 * Should NEVER be used publicly — call [MetricsPixel.send] instead.
 */
object MetricsPixelExtensionProvider {
    lateinit var instance: MetricsPixelExtension
}

/**
 * Sends this metric pixel, handling conversion window checks, deduplication and count thresholds.
 */
suspend fun MetricsPixel.send() = MetricsPixelExtensionProvider.instance.send(this)
