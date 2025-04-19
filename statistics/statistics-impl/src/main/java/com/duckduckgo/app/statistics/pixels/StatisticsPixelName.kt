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

package com.duckduckgo.app.statistics.pixels

import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PACKAGE_PRIVATE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName

@VisibleForTesting(otherwise = PACKAGE_PRIVATE)
enum class StatisticsPixelName(override val pixelName: String) : PixelName {
    BROWSER_DAILY_ACTIVE_FEATURE_STATE("m_browser_feature_daily_active_user_d"),
    RETENTION_SEGMENTS("m_retention_segments"),
}
