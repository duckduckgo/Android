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

package com.duckduckgo.app.startup_metrics.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

/**
 * Pixel names for app startup metrics.
 */
enum class StartupMetricsPixelName(override val pixelName: String) : Pixel.PixelName {
    /**
     * Pixel fired when app startup completes and reaches interactive state.
     */
    APP_STARTUP_TIME("m_app_startup_time"),
}

/**
 * Parameter keys for startup metrics pixels.
 */
object StartupMetricsPixelParameters {
    /**
     * Startup type classification (cold/warm/hot/unknown).
     * Type: String (enum)
     * Required: Yes
     */
    const val STARTUP_TYPE = "startup_type"

    /**
     * Time from process start to first frame in milliseconds (API 35+ only).
     * Type: Integer
     * Required: No (null for legacy measurement)
     * Valid range: 50-30000
     */
    const val TTID_DURATION_MS = "ttid_duration_ms"

    /**
     * Device memory capacity bucket for classification.
     * Type: String
     * Required: No (null if privacy review fails)
     * Valid values: <1GB, 1GB, 2GB, 4GB, 6GB, 8GB, 12GB, 16GB+
     */
    const val DEVICE_RAM_BUCKET = "device_ram_bucket"

    /**
     * Android API level of device.
     * Type: Integer
     * Required: Yes
     * Valid range: >= 26
     */
    const val API_LEVEL = "api_level"
}
