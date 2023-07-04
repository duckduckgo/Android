/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.statistics.api.featureusage.pixel

import com.duckduckgo.app.statistics.pixels.Pixel

enum class FeatureSegmentsPixelName(override val pixelName: String) : Pixel.PixelName {
    DAILY_USER_EVENT_SEGMENT("m_daily_user_event_segment"),
}

object FeatureSegmentsPixelParameters {
    const val DAYS_SINCE_APP_INSTALL = "days_since_app_install"
}
