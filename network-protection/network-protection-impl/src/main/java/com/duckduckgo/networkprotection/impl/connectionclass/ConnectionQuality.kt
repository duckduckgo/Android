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

package com.duckduckgo.networkprotection.impl.connectionclass

/**
 * excellent -> [0, 20]
 * good -> (20, 50]
 * moderate -> (50, 200]
 * poor -> (200, 300]
 * terrible -> (300, any)
 */
enum class ConnectionQuality(val max: Double, val emoji: String) {
    TERRIBLE(Double.MAX_VALUE, "🥺"),
    POOR(300.0, "☹️"),
    MODERATE(200.0, "😐"),
    GOOD(50.0, "🙂"),
    EXCELLENT(20.0, "🤗"),
    UNKNOWN(Double.MAX_VALUE, "🤔"),
}

fun Double.asConnectionQuality(): ConnectionQuality {
    return if (this <= 0) {
        ConnectionQuality.UNKNOWN
    } else if (this <= ConnectionQuality.EXCELLENT.max) {
        ConnectionQuality.EXCELLENT
    } else if (this <= ConnectionQuality.GOOD.max) {
        ConnectionQuality.GOOD
    } else if (this <= ConnectionQuality.MODERATE.max) {
        ConnectionQuality.MODERATE
    } else if (this <= ConnectionQuality.POOR.max) {
        ConnectionQuality.POOR
    } else if (this <= ConnectionQuality.TERRIBLE.max) {
        ConnectionQuality.TERRIBLE
    } else {
        ConnectionQuality.UNKNOWN
    }
}

fun Int.asConnectionQuality(): ConnectionQuality {
    return this.toDouble().asConnectionQuality()
}
