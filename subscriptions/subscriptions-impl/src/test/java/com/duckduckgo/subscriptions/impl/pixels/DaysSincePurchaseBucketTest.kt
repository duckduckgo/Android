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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DaysSincePurchaseBucketTest {

    @Test
    fun whenPurchasedUpToOneDayAgoThenD1() {
        assertEquals(DaysSincePurchaseBucket.D1, bucketFor(days = 0))
        assertEquals(DaysSincePurchaseBucket.D1, bucketFor(days = 1))
    }

    @Test
    fun whenPurchasedTwoToSevenDaysAgoThenD2To7() {
        assertEquals(DaysSincePurchaseBucket.D2_7, bucketFor(days = 2))
        assertEquals(DaysSincePurchaseBucket.D2_7, bucketFor(days = 7))
    }

    @Test
    fun whenPurchasedEightToThirtyDaysAgoThenD8To30() {
        assertEquals(DaysSincePurchaseBucket.D8_30, bucketFor(days = 8))
        assertEquals(DaysSincePurchaseBucket.D8_30, bucketFor(days = 30))
    }

    @Test
    fun whenPurchasedMoreThanThirtyDaysAgoThenD31Plus() {
        assertEquals(DaysSincePurchaseBucket.D31_PLUS, bucketFor(days = 31))
    }

    @Test
    fun whenStartDateIsInTheFutureThenD1() {
        assertEquals(DaysSincePurchaseBucket.D1, DaysSincePurchaseBucket.from(startedAtMillis = NOW + 1000, nowMillis = NOW))
    }

    private fun bucketFor(days: Long): DaysSincePurchaseBucket =
        DaysSincePurchaseBucket.from(startedAtMillis = NOW - TimeUnit.DAYS.toMillis(days), nowMillis = NOW)

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
