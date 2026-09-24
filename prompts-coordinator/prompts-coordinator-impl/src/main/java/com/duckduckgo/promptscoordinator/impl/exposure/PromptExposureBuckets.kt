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

package com.duckduckgo.promptscoordinator.impl.exposure

import java.util.concurrent.TimeUnit

// Every bound is lower-inclusive, upper-exclusive.

/** Lines up with [daysSinceInstallBucket]: each week index maps to exactly one bucket until d28_plus. */
internal fun weekIndexOf(daysSinceInstall: Long): Long = daysSinceInstall / DAYS_PER_WEEK

internal fun daysSinceInstallBucket(daysSinceInstall: Long): String = when {
    daysSinceInstall < 7 -> "d0_6"
    daysSinceInstall < 14 -> "d7_13"
    daysSinceInstall < 21 -> "d14_20"
    daysSinceInstall < 28 -> "d21_27"
    else -> "d28_plus"
}

internal fun nthInWeekBucket(nth: Int): String = when {
    nth <= 10 -> nth.toString()
    nth <= 15 -> "11_15"
    nth <= 20 -> "16_20"
    else -> "21_plus"
}

internal fun opensPrevWeekBucket(opens: Int): String = when {
    opens <= 0 -> "0"
    opens <= 2 -> "1_2"
    opens <= 5 -> "3_5"
    opens <= 10 -> "6_10"
    opens <= 20 -> "11_20"
    opens <= 50 -> "21_50"
    else -> "51_plus"
}

/**
 * @param previousPromptAt the last stamped prompt, where any value `<= 0` means none was ever stamped.
 * @return the bucket, or null when the clock moved back and the gap is meaningless.
 */
internal fun gapBucket(previousPromptAt: Long, now: Long): String? {
    // Selected on the previous stamp, never on the elapsed value: both "unset" sentinels are <= 0.
    if (previousPromptAt <= 0L) return "first"

    val elapsed = now - previousPromptAt
    return when {
        elapsed < 0 -> null
        elapsed < TimeUnit.MINUTES.toMillis(1) -> "lt_1m"
        elapsed < TimeUnit.MINUTES.toMillis(5) -> "1_5m"
        elapsed < TimeUnit.MINUTES.toMillis(10) -> "5_10m"
        elapsed < TimeUnit.MINUTES.toMillis(30) -> "10_30m"
        elapsed < TimeUnit.MINUTES.toMillis(60) -> "30_60m"
        elapsed < TimeUnit.HOURS.toMillis(4) -> "1_4h"
        elapsed < TimeUnit.HOURS.toMillis(12) -> "4_12h"
        elapsed < TimeUnit.HOURS.toMillis(24) -> "12_24h"
        elapsed < TimeUnit.HOURS.toMillis(48) -> "24_48h"
        else -> "gt_48h"
    }
}

private const val DAYS_PER_WEEK = 7L
