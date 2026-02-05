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

package com.duckduckgo.webdetection.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.webdetection.api.WebDetectionPixels
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealWebDetectionPixels @Inject constructor(
    private val pixel: Pixel,
) : WebDetectionPixels {

    override fun fireAdwallDailyPixel(count: Int) {
        pixel.fire(
            WebDetectionPixelNames.ADWALL_DETECTION_DAILY,
            mapOf(PARAM_COUNT to bucketCount(count)),
        )
    }

    override fun fireAdwallWeeklyPixel(count: Int) {
        pixel.fire(
            WebDetectionPixelNames.ADWALL_DETECTION_WEEKLY,
            mapOf(PARAM_COUNT to bucketCount(count)),
        )
    }

    /**
     * Bucket the count for privacy. Buckets: 0, 1-5, 6-10, 11-20, 21-50, 51+
     */
    private fun bucketCount(count: Int): String {
        return when {
            count == 0 -> "0"
            count <= 5 -> "1-5"
            count <= 10 -> "6-10"
            count <= 20 -> "11-20"
            count <= 50 -> "21-50"
            else -> "51+"
        }
    }

    companion object {
        private const val PARAM_COUNT = "count"
    }
}
