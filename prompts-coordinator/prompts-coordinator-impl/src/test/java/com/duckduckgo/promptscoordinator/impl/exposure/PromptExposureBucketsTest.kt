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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class PromptExposureBucketsTest {

    @Test
    fun whenDaysSinceInstallAtEachBoundaryThenBucketIsLowerInclusive() {
        mapOf(
            0L to "d0_6",
            6L to "d0_6",
            7L to "d7_13",
            13L to "d7_13",
            14L to "d14_20",
            20L to "d14_20",
            21L to "d21_27",
            27L to "d21_27",
            28L to "d28_plus",
            365L to "d28_plus",
        ).forEach { (days, bucket) -> assertEquals("days=$days", bucket, daysSinceInstallBucket(days)) }
    }

    @Test
    fun whenWeekIndexComputedThenItLinesUpWithTheDaysBuckets() {
        mapOf(0L to 0L, 6L to 0L, 7L to 1L, 13L to 1L, 14L to 2L, 27L to 3L, 28L to 4L).forEach { (days, week) ->
            assertEquals("days=$days", week, weekIndexOf(days))
        }
    }

    @Test
    fun whenNthInWeekAtEachBoundaryThenBucketIsExactUpToTen() {
        (1..10).forEach { assertEquals(it.toString(), nthInWeekBucket(it)) }
        mapOf(
            11 to "11_15",
            15 to "11_15",
            16 to "16_20",
            20 to "16_20",
            21 to "21_plus",
            500 to "21_plus",
        ).forEach { (nth, bucket) -> assertEquals("nth=$nth", bucket, nthInWeekBucket(nth)) }
    }

    @Test
    fun whenOpensPrevWeekAtEachBoundaryThenBucketMatches() {
        mapOf(
            0 to "0",
            1 to "1_2",
            2 to "1_2",
            3 to "3_5",
            5 to "3_5",
            6 to "6_10",
            10 to "6_10",
            11 to "11_20",
            20 to "11_20",
            21 to "21_50",
            50 to "21_50",
            51 to "51_plus",
            1_000 to "51_plus",
        ).forEach { (opens, bucket) -> assertEquals("opens=$opens", bucket, opensPrevWeekBucket(opens)) }
    }

    @Test
    fun whenNoPreviousPromptThenGapIsFirstForBothSentinels() {
        assertEquals("first", gapBucket(previousPromptAt = 0L, now = NOW))
        assertEquals("first", gapBucket(previousPromptAt = -1L, now = NOW))
    }

    @Test
    fun whenElapsedIsNegativeThenNoGapBucket() {
        assertNull(gapBucket(previousPromptAt = NOW + 1, now = NOW))
    }

    @Test
    fun whenElapsedAtEachBoundaryThenGapBucketIsLowerInclusive() {
        val minute = TimeUnit.MINUTES.toMillis(1)
        val hour = TimeUnit.HOURS.toMillis(1)
        mapOf(
            0L to "lt_1m",
            minute - 1 to "lt_1m",
            minute to "1_5m",
            5 * minute - 1 to "1_5m",
            5 * minute to "5_10m",
            10 * minute - 1 to "5_10m",
            10 * minute to "10_30m",
            30 * minute - 1 to "10_30m",
            30 * minute to "30_60m",
            hour - 1 to "30_60m",
            hour to "1_4h",
            4 * hour - 1 to "1_4h",
            4 * hour to "4_12h",
            12 * hour - 1 to "4_12h",
            12 * hour to "12_24h",
            24 * hour - 1 to "12_24h",
            24 * hour to "24_48h",
            48 * hour - 1 to "24_48h",
            48 * hour to "gt_48h",
        ).forEach { (elapsed, bucket) ->
            assertEquals("elapsed=$elapsed", bucket, gapBucket(previousPromptAt = NOW - elapsed, now = NOW))
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
