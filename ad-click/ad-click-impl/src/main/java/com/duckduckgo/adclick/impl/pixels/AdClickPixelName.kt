/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class AdClickPixelName(override val pixelName: String) : Pixel.PixelName {
    AD_CLICK_DETECTED("m_ad_click_detected"),
    AD_CLICK_ACTIVE("m_ad_click_active"),
    AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION("m_pageloads_with_ad_attribution"),
}

object AdClickPixelValues {
    const val AD_CLICK_DETECTED_MATCHED = "matched"
    const val AD_CLICK_DETECTED_MISMATCH = "mismatch"
    const val AD_CLICK_DETECTED_SERP_ONLY = "serp_only"
    const val AD_CLICK_DETECTED_HEURISTIC_ONLY = "heuristic_only"
    const val AD_CLICK_DETECTED_NONE = "none"
}

object AdClickPixelParameters {
    const val AD_CLICK_DOMAIN_DETECTION = "domainDetection"
    const val AD_CLICK_HEURISTIC_DETECTION = "heuristicDetection"
    const val AD_CLICK_DOMAIN_DETECTION_ENABLED = "domainDetectionEnabled"
    const val AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION_COUNT = "count"
}
