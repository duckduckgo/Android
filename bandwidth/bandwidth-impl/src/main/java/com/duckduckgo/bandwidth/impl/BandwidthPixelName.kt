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

package com.duckduckgo.bandwidth.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class BandwidthPixelName(override val pixelName: String) : Pixel.PixelName {
    BANDWIDTH("m_bandwidth")
}

object BandwidthPixelParameter {
    const val PERIOD = "period"
    const val APP_BYTES = "app_bytes"
    const val TOTAL_BYTES = "total_bytes"
}
