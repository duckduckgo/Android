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

package com.duckduckgo.webdetection.api

/**
 * Interface for firing web detection telemetry pixels.
 */
interface WebDetectionPixels {
    /**
     * Fire the daily adwall detection count pixel.
     * @param count The number of adwall detections since the last daily pixel.
     */
    fun fireAdwallDailyPixel(count: Int)

    /**
     * Fire the weekly adwall detection count pixel.
     * @param count The number of adwall detections since the last weekly pixel.
     */
    fun fireAdwallWeeklyPixel(count: Int)
}
