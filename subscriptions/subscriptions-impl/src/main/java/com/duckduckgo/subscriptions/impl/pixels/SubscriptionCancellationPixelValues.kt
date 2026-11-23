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

package com.duckduckgo.subscriptions.impl.pixels

import java.util.concurrent.TimeUnit

/** Which Subscription Settings row opened the Play Store subscription screen */
enum class CancellationEntry(val pixelValue: String) {
    CANCELLATION("cancellation"),
    MANAGE("manage"),
}

enum class CancellationFlowLoadFailureReason(val pixelValue: String) {
    HTTP("http"),
    NETWORK("network"),
}

enum class DaysSincePurchaseBucket(val pixelValue: String) {
    D1("d1"),
    D2_7("d2_7"),
    D8_30("d8_30"),
    D31_PLUS("d31_plus"),
    ;

    companion object {
        fun from(startedAtMillis: Long, nowMillis: Long): DaysSincePurchaseBucket {
            val days = TimeUnit.MILLISECONDS.toDays((nowMillis - startedAtMillis).coerceAtLeast(0))
            return when {
                days <= 1 -> D1
                days <= 7 -> D2_7
                days <= 30 -> D8_30
                else -> D31_PLUS
            }
        }
    }
}
